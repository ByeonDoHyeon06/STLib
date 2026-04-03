package studio.singlethread.lib.registry.common.provider

import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack

interface ResourceBlockProvider : ResourceProvider {
    fun blockIds(): Collection<String>

    fun resolveBlockId(block: Block): String?

    fun placeBlock(id: String, block: Block): Boolean

    fun removeBlock(block: Block): Boolean

    fun blockDisplayName(id: String): String? {
        return displayName(id)
    }

    fun blockIcon(id: String): ItemStack? {
        return icon(id)
    }
}
