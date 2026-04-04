package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.inventory.Inventory
import org.bukkit.event.inventory.InventoryType
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

class BukkitGuiService(
    private val plugin: JavaPlugin,
    private val registerListener: (Listener) -> Unit = { listener ->
        Bukkit.getPluginManager().registerEvents(listener, plugin)
    },
    private val unregisterListener: (Listener) -> Unit = { listener ->
        HandlerList.unregisterAll(listener)
    },
    private val inventoryFactory: (size: Int, title: Component) -> Inventory = { size, title ->
        Bukkit.createInventory(null, size, title)
    },
    private val typedInventoryFactory: (type: InventoryType, title: Component) -> Inventory = { type, title ->
        Bukkit.createInventory(null, type, title)
    },
) : StGuiService, Listener {
    private val guis = ConcurrentHashMap<Inventory, StGui>()
    private val activationLock = Any()
    @Volatile
    private var active = false

    fun activate() {
        synchronized(activationLock) {
            if (active) {
                return
            }
            registerListener(this)
            active = true
        }
    }

    override fun create(
        rows: Int,
        title: Component,
        definition: StGuiDefinition,
    ): StGui {
        ensureActive()
        require(rows in 1..6) { "rows must be between 1 and 6" }

        val inventory = inventoryFactory(rows * 9, title)
        return createWithInventory(
            inventory = inventory,
            rows = rows,
            definition = definition,
        )
    }

    override fun create(
        size: Int,
        title: Component,
        type: InventoryType,
        definition: StGuiDefinition,
    ): StGui {
        ensureActive()
        require(size in 9..54 && size % 9 == 0) { "size must be a multiple of 9 between 9 and 54" }

        val inventory = typedInventoryFactory(type, title)
        require(inventory.size == size) {
            "inventory type $type resolved size ${inventory.size}, expected $size"
        }
        return createWithInventory(
            inventory = inventory,
            rows = size / 9,
            definition = definition,
        )
    }

    private fun createWithInventory(
        inventory: Inventory,
        rows: Int,
        definition: StGuiDefinition,
    ): StGui {
        val builder = StGuiBuilder(rows)
        with(definition) { builder.define() }
        val gui =
            StGui(
                inventory = inventory,
                blueprint = builder.build(),
                renderRequest = { targetGui, viewer -> targetGui.render(viewer) },
                reportError = { phase, error ->
                    if (error == null) {
                        plugin.logger.warning("GUI runtime warning: $phase")
                    } else {
                        plugin.logger.log(Level.WARNING, "GUI runtime failure: $phase", error)
                    }
                },
            )
        gui.render(viewer = null)
        guis[inventory] = gui
        return gui
    }

    override fun open(
        player: Player,
        gui: StGui,
    ) {
        ensureActive()
        guis[gui.inventory] = gui
        player.openInventory(gui.inventory)
    }

    @EventHandler
    fun onGuiOpen(event: InventoryOpenEvent) {
        val gui = guis[event.inventory] ?: return
        val player = event.player as? Player ?: return
        gui.onOpen(player)
    }

    @EventHandler
    fun onGuiClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        val gui = guis[topInventory] ?: return
        if (event.clickedInventory != topInventory) {
            return
        }

        if (gui.cancelAllClicks()) {
            event.isCancelled = true
        }

        val player = event.whoClicked as? Player ?: return
        val context =
            StGuiClickContext(
                player = player,
                event = event,
                gui = gui,
            )
        gui.handleGlobalClick(context)
        gui.handleSlotClick(event.slot, context)
    }

    @EventHandler
    fun onGuiClose(event: InventoryCloseEvent) {
        val gui = guis[event.inventory] ?: return
        val player = event.player as? Player
        if (player != null) {
            gui.onClose(player)
        }

        if (event.inventory.viewers.isNotEmpty()) {
            return
        }
        guis.remove(event.inventory)
    }

    override fun close() {
        val shouldUnregister = synchronized(activationLock) {
            if (!active) {
                false
            } else {
                active = false
                true
            }
        }

        val snapshot = guis.values.toList()
        snapshot.forEach { gui ->
            gui.inventory.viewers.toList().forEach { viewer ->
                if (viewer is Player) {
                    viewer.closeInventory()
                }
            }
        }
        guis.clear()

        if (shouldUnregister) {
            unregisterListener(this)
        }
    }

    private fun ensureActive() {
        if (active) {
            return
        }
        activate()
    }
}
