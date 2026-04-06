package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.ConcurrentHashMap

class STGuiPaginationTest {
    @Test
    fun `view should fallback to base layout when no state match exists`() {
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
                title = Component.text("view-base"),
                size = 9,
                definition =
                    STGuiDefinition {
                        state("view", "unknown")
                        set(0, null) // base layout
                        view(stateKey = "view", stateValue = "list") {
                            set(1, null)
                        }
                        view(stateKey = "view", stateValue = "detail") {
                            set(2, null)
                        }
                    },
            )

        openGui(service, gui, inventory)
        val session = firstSession(service)
        assertEquals(setOf(0), slotHandlerSlots(session))
    }

    @Test
    fun `view should render matching state and refresh when state changes`() {
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
                title = Component.text("view-match"),
                size = 9,
                definition =
                    STGuiDefinition {
                        state("view", "list")
                        view(stateKey = "view", stateValue = "list") {
                            set(0, null)
                        }
                        view(stateKey = "view", stateValue = "detail") {
                            set(1, null)
                        }
                        set(2, null) // base layout always rendered
                    },
            )

        val player = openGui(service, gui, inventory)
        val session = firstSession(service)
        assertEquals(setOf(0, 2), slotHandlerSlots(session))

        val context =
            STGuiClickContext(
                player = player,
                event = mock(InventoryClickEvent::class.java),
                session = session,
            )
        context.state("view", "detail")
        context.refresh()

        assertEquals(setOf(1, 2), slotHandlerSlots(session))

        context.state("view", "unknown")
        context.refresh()

        assertEquals(setOf(2), slotHandlerSlots(session))
    }

    @Test
    fun `show and toggle helpers should update state and refresh in one step`() {
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
                title = Component.text("view-helpers"),
                size = 9,
                definition =
                    STGuiDefinition {
                        state("view", "list")
                        view(stateKey = "view", stateValue = "list") {
                            set(0, null)
                        }
                        view(stateKey = "view", stateValue = "detail") {
                            set(1, null)
                        }
                    },
            )

        val player = openGui(service, gui, inventory)
        val session = firstSession(service)
        val event = mock(InventoryClickEvent::class.java)
        val context = STGuiClickContext(player = player, event = event, session = session)

        context.show("view", "detail")
        assertEquals(setOf(1), slotHandlerSlots(session))

        val toggled = context.toggle("view", first = "list", second = "detail")
        assertEquals("list", toggled)
        assertEquals(setOf(0), slotHandlerSlots(session))
    }

    private fun openGui(
        service: BukkitGuiService,
        gui: STGui,
        inventory: Inventory,
    ): Player {
        val player = mock(Player::class.java)
        val openedView = mock(InventoryView::class.java)
        `when`(openedView.topInventory).thenReturn(inventory)
        `when`(player.openInventory(inventory)).thenReturn(openedView)
        service.open(player, gui)
        return player
    }

    @Suppress("UNCHECKED_CAST")
    private fun firstSession(service: BukkitGuiService): STGuiSession {
        val field = BukkitGuiService::class.java.getDeclaredField("sessions")
        field.isAccessible = true
        val sessions = field.get(service) as ConcurrentHashMap<Inventory, STGuiSession>
        return sessions.values.first()
    }

    @Suppress("UNCHECKED_CAST")
    private fun slotHandlerSlots(session: STGuiSession): Set<Int> {
        val field = STGuiSession::class.java.getDeclaredField("slotHandlers")
        field.isAccessible = true
        val handlers = field.get(session) as ConcurrentHashMap<Int, STGuiClickHandler>
        return handlers.keys
    }
}
