package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.ConcurrentHashMap

class BukkitGuiServiceTest {
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
    fun `open should bind session and close should unbind when no viewers remain`() {
        val inventory = mock(Inventory::class.java)
        val openedTopInventory = mock(Inventory::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(inventory.size).thenReturn(9)
        `when`(openedTopInventory.size).thenReturn(9)
        `when`(openedView.topInventory).thenReturn(openedTopInventory)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        assertEquals(0, trackedSessionCount(service))

        val player = mock(Player::class.java)
        doReturn(openedView).`when`(player).openInventory(any(Inventory::class.java))
        service.open(player, gui)
        assertEquals(2, trackedSessionCount(service))

        val closeEvent = mock(InventoryCloseEvent::class.java)
        val closeView = mock(InventoryView::class.java)
        `when`(closeView.topInventory).thenReturn(openedTopInventory)
        `when`(closeEvent.view).thenReturn(closeView)
        `when`(closeEvent.player).thenReturn(player)
        `when`(openedTopInventory.viewers).thenReturn(emptyList())
        service.onGuiClose(closeEvent)

        assertEquals(0, trackedSessionCount(service))
    }

    @Test
    fun `gui mapping should remain while other viewers are still present`() {
        val inventory = mock(Inventory::class.java)
        `when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        val player = mock(Player::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(inventory)
        doReturn(openedView).`when`(player).openInventory(any(Inventory::class.java))
        service.open(player, gui)

        val closeEvent = mock(InventoryCloseEvent::class.java)
        val closeView = mock(InventoryView::class.java)
        `when`(closeView.topInventory).thenReturn(inventory)
        `when`(closeEvent.view).thenReturn(closeView)
        `when`(closeEvent.player).thenReturn(player)
        `when`(inventory.viewers).thenReturn(listOf(mock(Player::class.java)))

        service.onGuiClose(closeEvent)

        assertEquals(1, trackedSessionCount(service))
    }

    @Test
    fun `onGuiOpen should invoke open handlers for tracked session`() {
        val inventory = mock(Inventory::class.java)
        `when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        var openCount = 0
        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition =
                    STGuiDefinition {
                        onOpen(STGuiOpenHandler { openCount++ })
                    },
            )

        val opener = mock(Player::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(inventory)
        doReturn(openedView).`when`(opener).openInventory(any(Inventory::class.java))
        service.open(opener, gui)

        val openEvent = mock(InventoryOpenEvent::class.java)
        `when`(openEvent.view).thenReturn(openedView)
        `when`(openEvent.player).thenReturn(opener)

        service.onGuiOpen(openEvent)

        assertEquals(1, openCount)
    }

    @Test
    fun `create with inventory type should validate fixed size`() {
        val wrongSizeInventory = mock(Inventory::class.java)
        `when`(wrongSizeInventory.size).thenReturn(27)

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
                title = Component.text("typed"),
                size = 9,
                type = InventoryType.DROPPER,
                definition = STGuiDefinition { set(0, null) },
            )
        }
    }

    @Test
    fun `create should allow fixed type when requested size matches`() {
        val dropperInventory = mock(Inventory::class.java)
        `when`(dropperInventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                typedInventoryFactory = { _, _ -> dropperInventory },
            )

        service.create(
            title = Component.text("dropper"),
            size = 9,
            type = InventoryType.DROPPER,
            definition = STGuiDefinition { set(0, null) },
        )
    }

    @Test
    fun `onGuiClick should cancel when tracked gui is open even if click is outside top inventory`() {
        val inventory = mock(Inventory::class.java)
        val openedTopInventory = mock(Inventory::class.java)
        val bottomInventory = mock(Inventory::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(openedTopInventory)
        `when`(inventory.size).thenReturn(9)
        `when`(openedTopInventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        val player = mock(Player::class.java)
        doReturn(openedView).`when`(player).openInventory(any(Inventory::class.java))
        service.open(player, gui)

        val clickEvent = mock(InventoryClickEvent::class.java)
        var cancelled = false
        `when`(clickEvent.view).thenReturn(openedView)
        `when`(clickEvent.clickedInventory).thenReturn(bottomInventory)
        `when`(clickEvent.whoClicked).thenReturn(player)
        doAnswer {
            cancelled = it.arguments[0] as Boolean
            null
        }.`when`(clickEvent).setCancelled(anyBoolean())

        service.onGuiClick(clickEvent)

        assertTrue(cancelled)
    }

    @Test
    fun `onGuiDrag should cancel when drag touches top inventory`() {
        val inventory = mock(Inventory::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(inventory)
        `when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        val player = mock(Player::class.java)
        doReturn(openedView).`when`(player).openInventory(any(Inventory::class.java))
        service.open(player, gui)

        val dragEvent = mock(InventoryDragEvent::class.java)
        var cancelled = false
        `when`(dragEvent.view).thenReturn(openedView)
        `when`(dragEvent.rawSlots).thenReturn(setOf(0, 10))
        doAnswer {
            cancelled = it.arguments[0] as Boolean
            null
        }.`when`(dragEvent).setCancelled(anyBoolean())

        service.onGuiDrag(dragEvent)

        assertTrue(cancelled)
    }

    @Test
    fun `player openInventory stgui extension should route through tracked open path`() {
        val inventory = mock(Inventory::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(inventory)
        `when`(inventory.size).thenReturn(9)

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventory },
            )

        val gui =
            service.create(
                title = Component.text("test"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        val player = mock(Player::class.java)
        doReturn(openedView).`when`(player).openInventory(any(Inventory::class.java))

        player.openInventory(gui)

        assertEquals(1, trackedSessionCount(service))
    }

    @Test
    fun `open should create per viewer isolated sessions`() {
        val firstInventory = mock(Inventory::class.java)
        val secondInventory = mock(Inventory::class.java)
        `when`(firstInventory.size).thenReturn(9)
        `when`(secondInventory.size).thenReturn(9)
        val inventories = ArrayDeque(listOf(firstInventory, secondInventory))

        val service =
            BukkitGuiService(
                plugin = mock(JavaPlugin::class.java),
                registerListener = {},
                unregisterListener = {},
                inventoryFactory = { _, _ -> inventories.removeFirst() },
            )

        val gui =
            service.create(
                title = Component.text("session"),
                size = 9,
                definition = STGuiDefinition { set(0, null) },
            )

        val firstPlayer = mock(Player::class.java)
        val secondPlayer = mock(Player::class.java)
        val firstView = mock(InventoryView::class.java)
        val secondView = mock(InventoryView::class.java)
        `when`(firstView.topInventory).thenReturn(firstInventory)
        `when`(secondView.topInventory).thenReturn(secondInventory)
        doReturn(firstView).`when`(firstPlayer).openInventory(firstInventory)
        doReturn(secondView).`when`(secondPlayer).openInventory(secondInventory)

        service.open(firstPlayer, gui)
        service.open(secondPlayer, gui)

        val sessions = trackedSessions(service).values.toSet().toList()
        assertEquals(2, sessions.size)
        assertNotEquals(sessions[0], sessions[1])
    }

    @Suppress("UNCHECKED_CAST")
    private fun trackedSessions(service: BukkitGuiService): ConcurrentHashMap<Inventory, STGuiSession> {
        val field = BukkitGuiService::class.java.getDeclaredField("sessions")
        field.isAccessible = true
        return field.get(service) as ConcurrentHashMap<Inventory, STGuiSession>
    }

    private fun trackedSessionCount(service: BukkitGuiService): Int {
        return trackedSessions(service).size
    }
}
