package studio.singlethread.lib.registry.vanilla

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.capability.ResourceCapability
import studio.singlethread.lib.registry.common.provider.ResourceBlockProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import java.util.Locale

class VanillaResourceProvider : ResourceProvider, ResourceItemProvider, ResourceBlockProvider {
    override val providerId: String = "minecraft"

    private val materialsById: Map<String, Material> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Material.entries
            .asSequence()
            .filter { material -> material.isItem && material != Material.AIR }
            .associateBy { material -> material.name.lowercase(Locale.ROOT) }
    }

    private val blockMaterialsById: Map<String, Material> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        Material.entries
            .asSequence()
            .filter { material -> material.isBlock && material != Material.AIR }
            .associateBy { material -> material.name.lowercase(Locale.ROOT) }
    }

    private val allIds: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        materialsById.keys.sorted()
    }

    private val allBlockIds: List<String> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        blockMaterialsById.keys.sorted()
    }

    override fun isAvailable(): Boolean = true

    override fun capabilities(): Set<ResourceCapability> {
        return setOf(
            ResourceCapability.ID_LOOKUP,
            ResourceCapability.ITEM_LOOKUP,
            ResourceCapability.DISPLAY_NAME,
            ResourceCapability.ICON,
            ResourceCapability.CREATE_ITEM,
            ResourceCapability.BLOCK_LOOKUP,
            ResourceCapability.CREATE_BLOCK,
            ResourceCapability.REMOVE_BLOCK,
        )
    }

    override fun ids(): Collection<String> = allIds

    override fun resolveItemId(itemStack: ItemStack): String? {
        val material = itemStack.type
        if (material == Material.AIR || !material.isItem) {
            return null
        }
        return material.name.lowercase(Locale.ROOT)
    }

    override fun displayName(id: String): String? {
        val material = material(id) ?: return null
        return prettify(material)
    }

    override fun icon(id: String): ItemStack? {
        return createItem(id)
    }

    override fun createItem(id: String): ItemStack? {
        val material = material(id) ?: return null
        return ItemStack(material)
    }

    override fun blockIds(): Collection<String> = allBlockIds

    override fun resolveBlockId(block: Block): String? {
        val material = block.type
        if (material == Material.AIR || !material.isBlock) {
            return null
        }
        return material.name.lowercase(Locale.ROOT)
    }

    override fun placeBlock(id: String, block: Block): Boolean {
        val material = blockMaterial(id) ?: return false
        block.type = material
        return true
    }

    override fun removeBlock(block: Block): Boolean {
        if (block.type == Material.AIR) {
            return false
        }
        block.type = Material.AIR
        return true
    }

    override fun blockDisplayName(id: String): String? {
        val material = blockMaterial(id) ?: return null
        return prettify(material)
    }

    override fun blockIcon(id: String): ItemStack? {
        val material = blockMaterial(id) ?: return null
        if (!material.isItem || material == Material.AIR) {
            return null
        }
        return ItemStack(material)
    }

    private fun material(id: String): Material? {
        val normalized =
            id.trim()
                .lowercase(Locale.ROOT)
                .removePrefix("minecraft:")
        if (normalized.isBlank()) {
            return null
        }
        return materialsById[normalized]
    }

    private fun blockMaterial(id: String): Material? {
        val normalized =
            id.trim()
                .lowercase(Locale.ROOT)
                .removePrefix("minecraft:")
        if (normalized.isBlank()) {
            return null
        }
        return blockMaterialsById[normalized]
    }

    private fun prettify(material: Material): String {
        return material.name
            .lowercase(Locale.ROOT)
            .split('_')
            .joinToString(" ") { token -> token.replaceFirstChar { char -> char.uppercase(Locale.ROOT) } }
    }
}
