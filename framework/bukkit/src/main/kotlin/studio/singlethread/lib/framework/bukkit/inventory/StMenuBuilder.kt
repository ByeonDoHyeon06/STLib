package studio.singlethread.lib.framework.bukkit.inventory

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class StMenuBuilder internal constructor(
    private val inventory: Inventory,
) {
    private val handlers = linkedMapOf<Int, (InventoryClickEvent) -> Unit>()

    fun slot(
        slot: Int,
        item: ItemStack?,
        onClick: (InventoryClickEvent) -> Unit = {},
    ) {
        require(slot in 0 until inventory.size) { "slot index out of bounds: $slot" }
        inventory.setItem(slot, item)
        handlers[slot] = onClick
    }

    fun fill(
        item: ItemStack?,
        onClick: (InventoryClickEvent) -> Unit = {},
    ) {
        for (slot in 0 until inventory.size) {
            slot(slot, item?.clone(), onClick)
        }
    }

    internal fun build(): StMenu {
        return StMenu(inventory = inventory, handlers = handlers.toMap())
    }
}
