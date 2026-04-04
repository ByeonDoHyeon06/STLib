package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.util.concurrent.ConcurrentHashMap

class BukkitGuiServiceTest {
    @Test
    fun `constructor should not register listener before activation`() {
        var registerCount = 0

        BukkitGuiService(
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
            BukkitGuiService(
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
    fun `open should rebind gui after close removal`() {
        val inventory = mock(Inventory::class.java)
        Mockito.`when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                rows = 1,
                title = Component.text("test"),
                definition = StGuiDefinition { slot(0, null) },
            )

        val closeEvent = mock(InventoryCloseEvent::class.java)
        Mockito.`when`(closeEvent.inventory).thenReturn(inventory)
        Mockito.`when`(inventory.viewers).thenReturn(emptyList())
        service.onGuiClose(closeEvent)

        assertEquals(0, trackedGuiCount(service))

        val player = mock(Player::class.java)
        Mockito.doReturn(mock(InventoryView::class.java)).`when`(player).openInventory(any(Inventory::class.java))

        service.open(player, gui)

        assertEquals(1, trackedGuiCount(service))
    }

    @Test
    fun `gui mapping should remain while other viewers are still present`() {
        val inventory = mock(Inventory::class.java)
        Mockito.`when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        service.create(
            rows = 1,
            title = Component.text("test"),
            definition = StGuiDefinition { slot(0, null) },
        )

        val closeEvent = mock(InventoryCloseEvent::class.java)
        Mockito.`when`(closeEvent.inventory).thenReturn(inventory)
        Mockito.`when`(inventory.viewers).thenReturn(listOf(mock(Player::class.java)))

        service.onGuiClose(closeEvent)

        assertEquals(1, trackedGuiCount(service))
    }

    @Test
    fun `onGuiOpen should invoke open handlers for tracked gui`() {
        val inventory = mock(Inventory::class.java)
        Mockito.`when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        var openCount = 0
        service.create(
                rows = 1,
                title = Component.text("test"),
                definition =
                    StGuiDefinition {
                        onOpen(StGuiOpenHandler { openCount++ })
                    },
            )

        val openEvent = mock(InventoryOpenEvent::class.java)
        val player = mock(Player::class.java)
        Mockito.`when`(openEvent.inventory).thenReturn(inventory)
        Mockito.`when`(openEvent.player).thenReturn(player)

        service.onGuiOpen(openEvent)

        assertEquals(1, openCount)
    }

    @Test
    fun `create with inventory type should validate resolved size`() {
        val wrongSizeInventory = mock(Inventory::class.java)
        Mockito.`when`(wrongSizeInventory.size).thenReturn(27)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> error("inventoryFactory should not be used") },
                typedInventoryFactory = { _, _ -> wrongSizeInventory },
            )

        assertThrows(IllegalArgumentException::class.java) {
            service.create(
                size = 9,
                title = Component.text("typed"),
                type = InventoryType.DROPPER,
                definition = StGuiDefinition { slot(0, null) },
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackedGuiCount(service: BukkitGuiService): Int {
        val field = BukkitGuiService::class.java.getDeclaredField("guis")
        field.isAccessible = true
        return (field.get(service) as ConcurrentHashMap<Inventory, StGui>).size
    }
}
