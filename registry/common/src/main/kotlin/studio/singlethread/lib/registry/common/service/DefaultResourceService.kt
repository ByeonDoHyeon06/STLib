package studio.singlethread.lib.registry.common.service

import org.bukkit.Location
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.model.ResourceBlockRef
import studio.singlethread.lib.registry.common.model.ResourceFurnitureRef
import studio.singlethread.lib.registry.common.model.ResourceItemRef
import studio.singlethread.lib.registry.common.provider.ResourceBlockProvider
import studio.singlethread.lib.registry.common.provider.ResourceFurnitureProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import java.util.concurrent.CopyOnWriteArrayList

class DefaultResourceService(
    providers: Collection<ResourceProvider> = emptyList(),
) : ResourceService {
    private val providers = CopyOnWriteArrayList(providers)
    private val itemView = ItemView(::availableProviders)
    private val blockView = BlockView(::availableProviders)
    private val furnitureView = FurnitureView(::availableProviders)

    override fun registerProvider(provider: ResourceProvider) {
        providers.add(provider)
    }

    override fun unregisterProvider(providerId: String): Boolean {
        return providers.removeIf { provider ->
            provider.providerId.equals(providerId.trim(), ignoreCase = true)
        }
    }

    override fun providers(): Collection<ResourceProvider> = providers.toList()

    override fun availableProviders(): Collection<ResourceProvider> {
        return providers.filter { it.isAvailable() }
    }

    override fun items(): ResourceItems = itemView

    override fun blocks(): ResourceBlocks = blockView

    override fun furnitures(): ResourceFurnitures = furnitureView

    override fun from(refOrId: String): ResourceItemRef? {
        return itemView.from(refOrId)
    }

    override fun from(itemStack: ItemStack): ResourceItemRef? {
        return itemView.from(itemStack)
    }

    override fun ids(): Collection<String> {
        return itemView.ids()
    }

    override fun displayName(refOrId: String): String? {
        return itemView.displayName(refOrId)
    }

    override fun icon(refOrId: String): ItemStack? {
        return itemView.icon(refOrId)
    }

    override fun create(refOrId: String): ItemStack? {
        return itemView.create(refOrId)
    }

    private class ItemView(
        private val providers: () -> Collection<ResourceProvider>,
    ) : ResourceItems {
        override fun from(refOrId: String): ResourceItemRef? {
            return resolveReference(
                refOrId = refOrId,
                providers = providers(),
                ids = { provider -> provider.ids() },
            ) { provider, id -> ResourceItemRef(provider.providerId, id) }
        }

        override fun from(itemStack: ItemStack): ResourceItemRef? {
            if (itemStack.type.isAir) {
                return null
            }

            val available = providers()
            return available.asSequence()
                .mapNotNull { provider ->
                    val itemProvider = provider as? ResourceItemProvider ?: return@mapNotNull null
                    val resolvedId = itemProvider.resolveItemId(itemStack)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
                    ResourceItemRef(provider.providerId, resolvedId)
                }
                .firstOrNull()
        }

        override fun ids(): Collection<String> {
            return providers().flatMap { provider ->
                provider.ids().map { id -> "${provider.providerId}:$id" }
            }
        }

        override fun displayName(refOrId: String): String? {
            val available = providers()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.displayName(ref.id)
        }

        override fun icon(refOrId: String): ItemStack? {
            val available = providers()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.icon(ref.id)
        }

        override fun create(refOrId: String): ItemStack? {
            val available = providers()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.createItem(ref.id)
        }
    }

    private class BlockView(
        private val providers: () -> Collection<ResourceProvider>,
    ) : ResourceBlocks {
        override fun from(refOrId: String): ResourceBlockRef? {
            val available = providers().filterIsInstance<ResourceBlockProvider>()
            return resolveReference(
                refOrId = refOrId,
                providers = available,
                ids = { provider -> provider.blockIds() },
            ) { provider, id -> ResourceBlockRef(provider.providerId, id) }
        }

        override fun from(block: Block): ResourceBlockRef? {
            if (block.type.isAir) {
                return null
            }

            val available = providers().filterIsInstance<ResourceBlockProvider>()
            return available.asSequence()
                .mapNotNull { provider ->
                    val resolvedId = provider.resolveBlockId(block)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
                    ResourceBlockRef(provider.providerId, resolvedId)
                }
                .firstOrNull()
        }

        override fun ids(): Collection<String> {
            return providers().asSequence()
                .filterIsInstance<ResourceBlockProvider>()
                .flatMap { provider ->
                    provider.blockIds().asSequence().map { id -> "${provider.providerId}:$id" }
                }
                .toList()
        }

        override fun displayName(refOrId: String): String? {
            val available = providers().filterIsInstance<ResourceBlockProvider>()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.blockDisplayName(ref.id)
        }

        override fun icon(refOrId: String): ItemStack? {
            val available = providers().filterIsInstance<ResourceBlockProvider>()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.blockIcon(ref.id)
        }

        override fun place(refOrId: String, block: Block): Boolean {
            val available = providers().filterIsInstance<ResourceBlockProvider>()
            val ref = from(refOrId) ?: return false
            val provider = provider(ref.provider, available) ?: return false
            return provider.placeBlock(ref.id, block)
        }

        override fun remove(block: Block): Boolean {
            val available = providers().filterIsInstance<ResourceBlockProvider>()
            val ref = from(block) ?: return false
            val provider = provider(ref.provider, available) ?: return false
            return provider.removeBlock(block)
        }
    }

    private class FurnitureView(
        private val providers: () -> Collection<ResourceProvider>,
    ) : ResourceFurnitures {
        override fun from(refOrId: String): ResourceFurnitureRef? {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            return resolveReference(
                refOrId = refOrId,
                providers = available,
                ids = { provider -> provider.furnitureIds() },
            ) { provider, id -> ResourceFurnitureRef(provider.providerId, id) }
        }

        override fun from(entity: Entity): ResourceFurnitureRef? {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            return available.asSequence()
                .mapNotNull { provider ->
                    val resolvedId = provider.resolveFurnitureId(entity)?.trim().takeIf { !it.isNullOrBlank() } ?: return@mapNotNull null
                    ResourceFurnitureRef(provider.providerId, resolvedId)
                }
                .firstOrNull()
        }

        override fun ids(): Collection<String> {
            return providers().asSequence()
                .filterIsInstance<ResourceFurnitureProvider>()
                .flatMap { provider ->
                    provider.furnitureIds().asSequence().map { id -> "${provider.providerId}:$id" }
                }
                .toList()
        }

        override fun displayName(refOrId: String): String? {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.furnitureDisplayName(ref.id)
        }

        override fun icon(refOrId: String): ItemStack? {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            val ref = from(refOrId) ?: return null
            val provider = provider(ref.provider, available) ?: return null
            return provider.furnitureIcon(ref.id)
        }

        override fun place(refOrId: String, location: Location): Boolean {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            val ref = from(refOrId) ?: return false
            val provider = provider(ref.provider, available) ?: return false
            return provider.placeFurniture(ref.id, location)
        }

        override fun remove(entity: Entity): Boolean {
            val available = providers().filterIsInstance<ResourceFurnitureProvider>()
            val ref = from(entity) ?: return false
            val provider = provider(ref.provider, available) ?: return false
            return provider.removeFurniture(entity)
        }
    }

    private companion object {
        private fun <T : ResourceProvider, R> resolveReference(
            refOrId: String,
            providers: Collection<T>,
            ids: (T) -> Collection<String>,
            toRef: (provider: T, id: String) -> R,
        ): R? {
            val value = refOrId.trim()
            if (value.isBlank()) {
                return null
            }

            if (':' in value) {
                val split = value.split(':', limit = 2)
                if (split.size != 2 || split[0].isBlank() || split[1].isBlank()) {
                    return null
                }

                val provider = provider(split[0], providers) ?: return null
                val matchedId = matchId(split[1], ids(provider)) ?: return null
                return toRef(provider, matchedId)
            }

            val matched = providers.asSequence()
                .mapNotNull { provider ->
                    val matchedId = matchId(value, ids(provider)) ?: return@mapNotNull null
                    toRef(provider, matchedId)
                }
                .firstOrNull()
            return matched
        }

        private fun matchId(
            requested: String,
            ids: Collection<String>,
        ): String? {
            return ids.firstOrNull { id ->
                id.equals(requested, ignoreCase = true)
            }
        }

        private fun <T : ResourceProvider> provider(
            providerId: String,
            availableProviders: Collection<T>,
        ): T? {
            return availableProviders.firstOrNull { provider ->
                provider.providerId.equals(providerId, ignoreCase = true)
            }
        }
    }
}
