package studio.singlethread.lib.framework.bukkit.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.InventoryView

fun Player.openInventory(gui: StGui): InventoryView? {
    return openInventory(gui.inventory)
}
