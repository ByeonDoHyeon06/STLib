package studio.singlethread.lib.framework.bukkit.inventory

import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory

class StMenu internal constructor(
    val inventory: Inventory,
    private val handlers: Map<Int, (InventoryClickEvent) -> Unit>,
) {
    internal fun handleClick(
        slot: Int,
        event: InventoryClickEvent,
    ) {
        handlers[slot]?.invoke(event)
    }
}
