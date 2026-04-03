package studio.singlethread.lib.registry.common.provider

import org.bukkit.inventory.ItemStack

interface ResourceItemProvider : ResourceProvider {
    fun resolveItemId(itemStack: ItemStack): String?
}
