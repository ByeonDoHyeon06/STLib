package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.bukkit.support.STCallbackFailureLogger
import java.util.concurrent.ConcurrentHashMap

class BukkitGuiService(
    private val plugin: JavaPlugin,
    private val debugLoggingEnabled: () -> Boolean = { false },
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
) : STGuiService, Listener {
    private val sessions = ConcurrentHashMap<Inventory, STGuiSession>()
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
        title: Component,
        size: Int,
        type: InventoryType,
        definition: STGuiDefinition,
    ): STGui {
        ensureActive()
        val resolvedSize = resolveTemplateSize(type = type, requestedSize = size, title = title)
        val builder = STGuiBuilder(resolvedSize)
        with(definition) { builder.define() }
        val blueprint = builder.build()

        return STGui(
            title = title,
            size = resolvedSize,
            type = type,
            blueprint = blueprint,
            openRequest = this::openTracked,
            reportError = { phase, error ->
                if (error == null) {
                    plugin.logger.warning("GUI runtime warning: $phase")
                } else {
                    STCallbackFailureLogger.log(
                        logger = plugin.logger,
                        subsystem = "GUI",
                        phase = phase,
                        error = error,
                        debugEnabled = debugLoggingEnabled,
                    )
                }
            },
        )
    }

    override fun open(
        player: Player,
        gui: STGui,
    ) {
        openTracked(player, gui)
    }

    private fun openTracked(
        player: Player,
        gui: STGui,
    ): InventoryView? {
        ensureActive()

        val inventory = createInventory(gui)
        val session = gui.createSession(viewer = player, inventory = inventory)
        session.render()

        sessions[inventory] = session

        val openedView = player.openInventory(inventory) ?: run {
            sessions.remove(inventory)
            return null
        }
        sessions[openedView.topInventory] = session
        return openedView
    }

    @EventHandler
    fun onGuiOpen(event: InventoryOpenEvent) {
        val topInventory = event.view.topInventory
        val session = sessions[topInventory] ?: return
        val player = event.player as? Player ?: return
        if (player != session.viewer) {
            return
        }
        session.onOpen()
    }

    @EventHandler
    fun onGuiClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        val session = sessions[topInventory] ?: return
        if (session.cancelAllClicks()) {
            event.isCancelled = true
        }
        if (event.clickedInventory != topInventory) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        if (player != session.viewer) {
            return
        }

        val context = STGuiClickContext(player = player, event = event, session = session)
        session.handleGlobalClick(context)
        session.handleSlotClick(event.slot, context)
    }

    @EventHandler
    fun onGuiDrag(event: InventoryDragEvent) {
        val topInventory = event.view.topInventory
        val session = sessions[topInventory] ?: return
        if (!session.cancelAllClicks()) {
            return
        }
        if (event.rawSlots.any { slot -> slot < topInventory.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onGuiClose(event: InventoryCloseEvent) {
        val topInventory = event.view.topInventory
        val session = sessions[topInventory] ?: return
        val player = event.player as? Player
        if (player != null && player == session.viewer) {
            session.onClose()
        }

        if (topInventory.viewers.isNotEmpty()) {
            return
        }
        sessions.entries.removeIf { (_, trackedSession) -> trackedSession === session }
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

        val snapshot = sessions.values.toSet()
        snapshot.forEach { session ->
            session.inventory.viewers.toList().forEach { viewer ->
                if (viewer is Player) {
                    viewer.closeInventory()
                }
            }
        }
        sessions.clear()

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

    private fun resolveTemplateSize(
        type: InventoryType,
        requestedSize: Int,
        title: Component,
    ): Int {
        require(requestedSize > 0) { "size must be > 0" }

        return when (typeProfile(type)) {
            InventoryTypeProfile.CHEST_ROWS -> {
                require(requestedSize in 9..54 && requestedSize % 9 == 0) {
                    "size for inventory type $type must be a multiple of 9 between 9 and 54"
                }
                requestedSize
            }

            InventoryTypeProfile.FIXED -> {
                val probe = typedInventoryFactory(type, title)
                val resolvedSize = probe.size
                require(requestedSize == resolvedSize) {
                    "inventory type $type requires fixed size $resolvedSize (requested=$requestedSize)"
                }
                resolvedSize
            }
        }
    }

    private fun createInventory(gui: STGui): Inventory {
        return when (typeProfile(gui.type)) {
            InventoryTypeProfile.CHEST_ROWS -> {
                val inventory = inventoryFactory(gui.size, gui.title)
                require(inventory.size == gui.size) {
                    "inventory factory resolved ${inventory.size} but expected ${gui.size}"
                }
                inventory
            }

            InventoryTypeProfile.FIXED -> {
                val inventory = typedInventoryFactory(gui.type, gui.title)
                require(inventory.size == gui.size) {
                    "typed inventory $gui.type resolved ${inventory.size} but expected ${gui.size}"
                }
                inventory
            }
        }
    }

    private fun typeProfile(type: InventoryType): InventoryTypeProfile {
        return if (type == InventoryType.CHEST) {
            InventoryTypeProfile.CHEST_ROWS
        } else {
            InventoryTypeProfile.FIXED
        }
    }

    private enum class InventoryTypeProfile {
        CHEST_ROWS,
        FIXED,
    }
}
