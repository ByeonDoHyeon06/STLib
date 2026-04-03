package studio.singlethread.lib.registry.common.service

import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.model.ResourceItemRef
import studio.singlethread.lib.registry.common.provider.ResourceProvider

interface ResourceService : ResourceItems {
    fun registerProvider(provider: ResourceProvider)

    fun unregisterProvider(providerId: String): Boolean

    fun providers(): Collection<ResourceProvider>

    fun availableProviders(): Collection<ResourceProvider>

    fun items(): ResourceItems

    fun blocks(): ResourceBlocks

    fun furnitures(): ResourceFurnitures

    fun resolve(refOrId: String): ResourceItemRef? {
        return from(refOrId)
    }

    fun createItem(refOrId: String): ItemStack? {
        return create(refOrId)
    }
}
