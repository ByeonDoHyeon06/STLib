package studio.singlethread.lib.registry.common.provider

import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.capability.ResourceCapability

interface ResourceProvider {
    val providerId: String

    fun isAvailable(): Boolean

    fun capabilities(): Set<ResourceCapability>

    fun ids(): Collection<String>

    fun displayName(id: String): String?

    fun icon(id: String): ItemStack?

    fun createItem(id: String): ItemStack?
}
