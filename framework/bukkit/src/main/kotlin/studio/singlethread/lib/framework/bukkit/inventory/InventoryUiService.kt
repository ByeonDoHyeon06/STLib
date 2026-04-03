package studio.singlethread.lib.framework.bukkit.inventory

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player

interface InventoryUiService : AutoCloseable {
    fun menu(rows: Int, title: Component, builder: StMenuBuilder.() -> Unit): StMenu

    fun open(player: Player, menu: StMenu)

    override fun close()
}
