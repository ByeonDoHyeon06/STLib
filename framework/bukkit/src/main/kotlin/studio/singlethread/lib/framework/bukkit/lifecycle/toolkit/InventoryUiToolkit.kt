package studio.singlethread.lib.framework.bukkit.lifecycle.toolkit

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import studio.singlethread.lib.framework.bukkit.inventory.InventoryUiService
import studio.singlethread.lib.framework.bukkit.inventory.StMenu
import studio.singlethread.lib.framework.bukkit.inventory.StMenuBuilder

class InventoryUiToolkit(
    private val inventoryUiService: InventoryUiService,
    private val parseTitle: (String, Map<String, String>) -> Component,
) {
    fun menu(
        rows: Int,
        title: String,
        placeholders: Map<String, String> = emptyMap(),
        builder: StMenuBuilder.() -> Unit,
    ): StMenu {
        return inventoryUiService.menu(
            rows = rows,
            title = parseTitle(title, placeholders),
            builder = builder,
        )
    }

    fun open(
        player: Player,
        menu: StMenu,
    ) {
        inventoryUiService.open(player, menu)
    }
}

