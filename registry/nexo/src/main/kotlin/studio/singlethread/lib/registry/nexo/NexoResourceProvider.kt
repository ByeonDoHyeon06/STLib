package studio.singlethread.lib.registry.nexo

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.capability.ResourceCapability
import studio.singlethread.lib.registry.common.provider.ExternalResourceProvider
import studio.singlethread.lib.registry.common.provider.ReflectiveResourceAccess
import studio.singlethread.lib.registry.common.provider.ResourceBlockProvider
import studio.singlethread.lib.registry.common.provider.ResourceFurnitureProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import java.util.concurrent.atomic.AtomicBoolean

class NexoResourceProvider(
    private val pluginEnabledChecker: (String) -> Boolean = { pluginName ->
        Bukkit.getPluginManager().isPluginEnabled(pluginName)
    },
) : ResourceProvider, ExternalResourceProvider, ResourceItemProvider, ResourceBlockProvider, ResourceFurnitureProvider {
    override val providerId: String = "nexo"
    override val upstreamPluginName: String = "Nexo"

    private val pluginEnabled = AtomicBoolean(false)
    private val dataLoaded = AtomicBoolean(false)

    override fun isAvailable(): Boolean {
        refreshState()
        return pluginEnabled.get() && dataLoaded.get()
    }

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
            ResourceCapability.FURNITURE_LOOKUP,
            ResourceCapability.CREATE_FURNITURE,
            ResourceCapability.REMOVE_FURNITURE,
        )
    }

    override fun ids(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val itemNames =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_ITEMS_CLASS,
                methodNames = listOf("itemNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(itemNames)
    }

    override fun resolveItemId(itemStack: ItemStack): String? {
        if (!isAvailable() || itemStack.type == Material.AIR) {
            return null
        }

        val id =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_ITEMS_CLASS,
                methodNames = listOf("idFromItem", "idFromItemStack", "itemIdFromItemStack"),
                itemStack,
            )
        return ReflectiveResourceAccess.asNonBlankString(id)
    }

    override fun displayName(id: String): String? {
        if (!isAvailable()) {
            return null
        }

        val item = createItem(id) ?: return null
        val meta = item.itemMeta ?: return null
        if (meta.hasDisplayName()) {
            return meta.displayName
        }
        return id
    }

    override fun icon(id: String): ItemStack? {
        return createItem(id)
    }

    override fun createItem(id: String): ItemStack? {
        if (!isAvailable()) {
            return null
        }

        val builder =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_ITEMS_CLASS,
                methodNames = listOf("itemFromId"),
                id,
            ) ?: return null

        return (ReflectiveResourceAccess.invoke(builder, listOf("build")) as? ItemStack)?.clone()
    }

    override fun blockIds(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_BLOCKS_CLASS,
                methodNames = listOf("blockIDs", "blockIds", "blockNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveBlockId(block: Block): String? {
        if (!isAvailable() || block.type == Material.AIR) {
            return null
        }

        val id =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_BLOCKS_CLASS,
                methodNames = listOf("idFromBlock", "blockIdFromBlock", "getBlockID"),
                block,
            )
        return ReflectiveResourceAccess.asNonBlankString(id)
    }

    override fun placeBlock(id: String, block: Block): Boolean {
        if (!isAvailable()) {
            return false
        }

        val direct =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                id,
                block.location,
            )
        if (asSuccess(direct)) {
            return true
        }

        val swapped =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                block.location,
                id,
            )
        if (asSuccess(swapped)) {
            return true
        }

        return resolveBlockId(block)?.equals(id, ignoreCase = true) == true
    }

    override fun removeBlock(block: Block): Boolean {
        if (!isAvailable()) {
            return false
        }

        val removed =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_BLOCKS_CLASS,
                methodNames = listOf("remove", "breakBlock"),
                block,
            )
        if (asSuccess(removed)) {
            return true
        }

        val previous = block.type
        block.type = Material.AIR
        return previous != Material.AIR
    }

    override fun furnitureIds(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_FURNITURE_CLASS,
                methodNames = listOf("furnitureIDs", "furnitureIds", "furnitureNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveFurnitureId(entity: Entity): String? {
        if (!isAvailable()) {
            return null
        }

        val id =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_FURNITURE_CLASS,
                methodNames = listOf("idFromEntity", "furnitureIdFromEntity", "getFurnitureID"),
                entity,
            )
        return ReflectiveResourceAccess.asNonBlankString(id)
    }

    override fun placeFurniture(id: String, location: Location): Boolean {
        if (!isAvailable()) {
            return false
        }

        val direct =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_FURNITURE_CLASS,
                methodNames = listOf("place", "spawn"),
                id,
                location,
            )
        if (asSuccess(direct)) {
            return true
        }

        val swapped =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_FURNITURE_CLASS,
                methodNames = listOf("place", "spawn"),
                location,
                id,
            )
        return asSuccess(swapped)
    }

    override fun removeFurniture(entity: Entity): Boolean {
        if (!isAvailable()) {
            return false
        }

        val removed =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_FURNITURE_CLASS,
                methodNames = listOf("remove", "despawn"),
                entity,
            )
        if (asSuccess(removed)) {
            return true
        }

        entity.remove()
        return true
    }

    override fun refreshState() {
        val enabled = pluginEnabledChecker(upstreamPluginName)
        pluginEnabled.set(enabled)
        if (!enabled) {
            dataLoaded.set(false)
            return
        }

        if (dataLoaded.get()) {
            return
        }

        if (probeLoadedByApi()) {
            dataLoaded.set(true)
        }
    }

    override fun onUpstreamPluginEnabled() {
        pluginEnabled.set(true)
        refreshState()
    }

    override fun onUpstreamPluginDisabled() {
        pluginEnabled.set(false)
        dataLoaded.set(false)
    }

    override fun onUpstreamDataLoaded() {
        dataLoaded.set(true)
    }

    override fun unavailableReason(): String? {
        refreshState()
        if (!pluginEnabled.get()) {
            return "Nexo plugin not installed or disabled"
        }
        if (!dataLoaded.get()) {
            return "Waiting for NexoItemsLoadedEvent"
        }
        return null
    }

    private fun asSuccess(result: Any?): Boolean {
        return when (result) {
            is Boolean -> result
            is Number -> result.toInt() != 0
            null -> false
            else -> true
        }
    }

    private fun probeLoadedByApi(): Boolean {
        val itemNames =
            ReflectiveResourceAccess.invokeStatic(
                className = NEXO_ITEMS_CLASS,
                methodNames = listOf("itemNames"),
            )
        return itemNames != null
    }

    private companion object {
        private const val NEXO_ITEMS_CLASS = "com.nexomc.nexo.api.NexoItems"
        private const val NEXO_BLOCKS_CLASS = "com.nexomc.nexo.api.NexoBlocks"
        private const val NEXO_FURNITURE_CLASS = "com.nexomc.nexo.api.NexoFurniture"
    }
}
