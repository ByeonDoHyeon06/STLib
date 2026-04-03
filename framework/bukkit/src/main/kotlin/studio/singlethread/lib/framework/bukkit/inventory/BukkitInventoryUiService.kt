package studio.singlethread.lib.framework.bukkit.inventory

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

class BukkitInventoryUiService(
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
) : InventoryUiService, Listener {
    private val menus = ConcurrentHashMap<Inventory, StMenu>()
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

    override fun menu(
        rows: Int,
        title: Component,
        builder: StMenuBuilder.() -> Unit,
    ): StMenu {
        ensureActive()
        require(rows in 1..6) { "rows must be between 1 and 6" }

        val inventory = inventoryFactory(rows * 9, title)
        val menuBuilder = StMenuBuilder(inventory).apply(builder)
        val menu = menuBuilder.build()
        menus[menu.inventory] = menu
        return menu
    }

    override fun open(
        player: Player,
        menu: StMenu,
    ) {
        ensureActive()
        // Rebind on open so menu instances survive close/reopen cycles.
        menus[menu.inventory] = menu
        player.openInventory(menu.inventory)
    }

    @EventHandler
    fun onMenuClick(event: InventoryClickEvent) {
        val topInventory = event.view.topInventory
        val menu = menus[topInventory] ?: return
        if (event.clickedInventory != topInventory) {
            return
        }

        event.isCancelled = true
        menu.handleClick(event.slot, event)
    }

    @EventHandler
    fun onMenuClose(event: InventoryCloseEvent) {
        if (event.inventory.viewers.isNotEmpty()) {
            return
        }
        menus.remove(event.inventory)
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

        val snapshot = menus.values.toList()
        snapshot.forEach { menu ->
            menu.inventory.viewers.toList().forEach { viewer ->
                if (viewer is Player) {
                    viewer.closeInventory()
                }
            }
        }
        menus.clear()
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
