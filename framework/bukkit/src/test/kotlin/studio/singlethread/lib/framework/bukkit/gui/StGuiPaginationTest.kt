package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.ConcurrentHashMap

class StGuiPaginationTest {
    @Test
    fun `state page should fallback to base layout when no page matches and no default exists`() {
        val inventory = mock(Inventory::class.java)

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
                title = Component.text("state-page-base"),
                definition =
                    StGuiDefinition {
                        state("view", "unknown")
                        set(0, null) // base layout
                        page(stateKey = "view", stateValue = "list") {
                            set(1, null)
                        }
                        page(stateKey = "view", stateValue = "detail") {
                            set(2, null)
                        }
                    },
            )

        assertEquals(setOf(0), slotHandlerSlots(gui))
    }

    @Test
    fun `state page should render matching page and fallback when state changes`() {
        val inventory = mock(Inventory::class.java)

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
                title = Component.text("state-page"),
                definition =
                    StGuiDefinition {
                        state("view", "list")
                        page(stateKey = "view", stateValue = "list") {
                            set(0, null)
                        }
                        page(stateKey = "view", stateValue = "detail") {
                            set(1, null)
                        }
                        pageDefault(stateKey = "view") {
                            set(2, null)
                        }
                    },
            )

        assertEquals(setOf(0), slotHandlerSlots(gui))

        val context =
            StGuiClickContext(
                player = mock(Player::class.java),
                event = mock(InventoryClickEvent::class.java),
                gui = gui,
            )
        context.state("view", "detail")
        context.refresh()

        assertEquals(setOf(1), slotHandlerSlots(gui))

        context.state("view", "unknown")
        context.refresh()

        assertEquals(setOf(2), slotHandlerSlots(gui))
    }

    @Suppress("UNCHECKED_CAST")
    private fun slotHandlerSlots(gui: StGui): Set<Int> {
        val field = StGui::class.java.getDeclaredField("slotHandlers")
        field.isAccessible = true
        val handlers = field.get(gui) as ConcurrentHashMap<Int, StGuiClickHandler>
        return handlers.keys
    }
}
