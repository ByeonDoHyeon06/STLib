package studio.singlethread.lib.registry.common.service

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.model.ResourceFurnitureRef

interface ResourceFurnitures {
    fun from(refOrId: String): ResourceFurnitureRef?

    fun from(entity: Entity): ResourceFurnitureRef?

    fun ids(): Collection<String>

    fun displayName(refOrId: String): String?

    fun icon(refOrId: String): ItemStack?

    fun place(refOrId: String, location: Location): Boolean

    fun remove(entity: Entity): Boolean

    fun exists(refOrId: String): Boolean {
        return from(refOrId) != null
    }
}
