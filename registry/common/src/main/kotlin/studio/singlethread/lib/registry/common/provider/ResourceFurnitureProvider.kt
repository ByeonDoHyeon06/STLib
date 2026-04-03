package studio.singlethread.lib.registry.common.provider

import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack

interface ResourceFurnitureProvider : ResourceProvider {
    fun furnitureIds(): Collection<String>

    fun resolveFurnitureId(entity: Entity): String?

    fun placeFurniture(id: String, location: Location): Boolean

    fun removeFurniture(entity: Entity): Boolean

    fun furnitureDisplayName(id: String): String? {
        return displayName(id)
    }

    fun furnitureIcon(id: String): ItemStack? {
        return icon(id)
    }
}
