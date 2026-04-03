package studio.singlethread.lib.registry.common.service

import org.bukkit.block.Block
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.model.ResourceBlockRef

interface ResourceBlocks {
    fun from(refOrId: String): ResourceBlockRef?

    fun from(block: Block): ResourceBlockRef?

    fun ids(): Collection<String>

    fun displayName(refOrId: String): String?

    fun icon(refOrId: String): ItemStack?

    fun place(refOrId: String, block: Block): Boolean

    fun remove(block: Block): Boolean

    fun exists(refOrId: String): Boolean {
        return from(refOrId) != null
    }
}
