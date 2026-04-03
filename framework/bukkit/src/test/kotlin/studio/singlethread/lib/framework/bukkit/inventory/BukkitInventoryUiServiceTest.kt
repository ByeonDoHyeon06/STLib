package studio.singlethread.lib.framework.bukkit.inventory

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.util.concurrent.ConcurrentHashMap

class BukkitInventoryUiServiceTest {
    @Test
    fun `constructor should not register listener before activation`() {
        var registerCount = 0

        BukkitInventoryUiService(
            plugin = mock(JavaPlugin::class.java),
            registerListener = { registerCount++ },
            unregisterListener = {},
            inventoryFactory = { _, _ -> error("inventory factory should not be used in this test") },
        )

        assertEquals(0, registerCount)
    }

    @Test
    fun `activate should register once and close should unregister once`() {
        var registerCount = 0
        var unregisterCount = 0

        val service =
            BukkitInventoryUiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = { registerCount++ },
                unregisterListener = { unregisterCount++ },
                inventoryFactory = { _, _ -> error("inventory factory should not be used in this test") },
            )

        service.activate()
        service.activate()
        service.close()
        service.close()

        assertEquals(1, registerCount)
        assertEquals(1, unregisterCount)
    }

    @Test
    fun `open should rebind menu after close removal`() {
        val inventory = mock(Inventory::class.java)
        Mockito.`when`(inventory.size).thenReturn(9)

        val service =
            BukkitInventoryUiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val menu =
            service.menu(rows = 1, title = Component.text("test")) {
                slot(0, null)
            }

        val closeEvent = mock(InventoryCloseEvent::class.java)
        Mockito.`when`(closeEvent.inventory).thenReturn(inventory)
        Mockito.`when`(inventory.viewers).thenReturn(emptyList())
        service.onMenuClose(closeEvent)

        assertEquals(0, trackedMenuCount(service))

        val player = mock(Player::class.java)
        Mockito.doReturn(mock(InventoryView::class.java)).`when`(player).openInventory(any(Inventory::class.java))

        service.open(player, menu)

        assertEquals(1, trackedMenuCount(service))
    }

    @Test
    fun `menu mapping should remain while other viewers are still present`() {
        val inventory = mock(Inventory::class.java)
        Mockito.`when`(inventory.size).thenReturn(9)

        val service =
            BukkitInventoryUiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        service.menu(rows = 1, title = Component.text("test")) {
            slot(0, null)
        }

        val closeEvent = mock(InventoryCloseEvent::class.java)
        Mockito.`when`(closeEvent.inventory).thenReturn(inventory)
        Mockito.`when`(inventory.viewers).thenReturn(listOf(mock(Player::class.java)))

        service.onMenuClose(closeEvent)

        assertEquals(1, trackedMenuCount(service))
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackedMenuCount(service: BukkitInventoryUiService): Int {
        val field = BukkitInventoryUiService::class.java.getDeclaredField("menus")
        field.isAccessible = true
        return (field.get(service) as ConcurrentHashMap<Inventory, StMenu>).size
    }
}
