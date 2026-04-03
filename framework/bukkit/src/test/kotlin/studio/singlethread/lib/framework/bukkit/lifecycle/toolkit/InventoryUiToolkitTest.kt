package studio.singlethread.lib.framework.bukkit.lifecycle.toolkit

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import studio.singlethread.lib.framework.bukkit.inventory.InventoryUiService
import studio.singlethread.lib.framework.bukkit.inventory.StMenu

class InventoryUiToolkitTest {
    @Test
    fun `menu should parse title and delegate to inventory ui service`() {
        val expectedMenu = StMenu(mock(Inventory::class.java), emptyMap())
        var capturedRows: Int? = null
        var capturedTitle: Component? = null
        var parsedInput: String? = null
        var parsedPlaceholders: Map<String, String>? = null

        val service =
            object : InventoryUiService {
                override fun menu(
                    rows: Int,
                    title: Component,
                    builder: studio.singlethread.lib.framework.bukkit.inventory.StMenuBuilder.() -> Unit,
                ): StMenu {
                    capturedRows = rows
                    capturedTitle = title
                    return expectedMenu
                }

                override fun open(
                    player: Player,
                    menu: StMenu,
                ) = Unit

                override fun close() = Unit
            }

        val toolkit =
            InventoryUiToolkit(
                inventoryUiService = service,
                parseTitle = { raw, placeholders ->
                    parsedInput = raw
                    parsedPlaceholders = placeholders
                    Component.text("parsed-title")
                },
            )

        val result =
            toolkit.menu(
                rows = 6,
                title = "<gold>{title}</gold>",
                placeholders = mapOf("title" to "Dashboard"),
            ) { }

        assertSame(expectedMenu, result)
        assertEquals(6, capturedRows)
        assertEquals(Component.text("parsed-title"), capturedTitle)
        assertEquals("<gold>{title}</gold>", parsedInput)
        assertEquals(mapOf("title" to "Dashboard"), parsedPlaceholders)
    }

    @Test
    fun `open should delegate to inventory ui service`() {
        val expectedMenu = StMenu(mock(Inventory::class.java), emptyMap())
        val player = mock(Player::class.java)
        var openedPlayer: Player? = null
        var openedMenu: StMenu? = null

        val service =
            object : InventoryUiService {
                override fun menu(
                    rows: Int,
                    title: Component,
                    builder: studio.singlethread.lib.framework.bukkit.inventory.StMenuBuilder.() -> Unit,
                ): StMenu {
                    return expectedMenu
                }

                override fun open(
                    player: Player,
                    menu: StMenu,
                ) {
                    openedPlayer = player
                    openedMenu = menu
                }

                override fun close() = Unit
            }

        val toolkit =
            InventoryUiToolkit(
                inventoryUiService = service,
                parseTitle = { _, _ -> Component.text("unused") },
            )

        toolkit.open(player, expectedMenu)

        assertSame(player, openedPlayer)
        assertSame(expectedMenu, openedMenu)
    }
}

