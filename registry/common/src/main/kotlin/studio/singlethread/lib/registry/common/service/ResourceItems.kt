package studio.singlethread.lib.registry.common.service

import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.model.ResourceItemRef

interface ResourceItems {
    fun from(refOrId: String): ResourceItemRef?

    fun from(itemStack: ItemStack): ResourceItemRef?

    fun ids(): Collection<String>

    fun displayName(refOrId: String): String?

    fun icon(refOrId: String): ItemStack?

    fun create(refOrId: String): ItemStack?

    fun exists(refOrId: String): Boolean {
        return from(refOrId) != null
    }
}
