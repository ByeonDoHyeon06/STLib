package studio.singlethread.lib.registry.oraxen

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

class OraxenResourceProvider(
    private val pluginEnabledChecker: (String) -> Boolean = { pluginName ->
        Bukkit.getPluginManager().isPluginEnabled(pluginName)
    },
) : ResourceProvider, ExternalResourceProvider, ResourceItemProvider, ResourceBlockProvider, ResourceFurnitureProvider {
    override val providerId: String = "oraxen"
    override val upstreamPluginName: String = "Oraxen"

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

        val names =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_ITEMS_CLASS,
                methodNames = listOf("getItemNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(names)
    }

    override fun resolveItemId(itemStack: ItemStack): String? {
        if (!isAvailable() || itemStack.type == Material.AIR) {
            return null
        }

        val resolved =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_ITEMS_CLASS,
                methodNames = listOf("getIdByItem", "getIdByItemStack", "getItemIdByItemStack"),
                itemStack,
            )
        return ReflectiveResourceAccess.asNonBlankString(resolved)
    }

    override fun displayName(id: String): String? {
        if (!isAvailable()) {
            return null
        }

        val itemBuilder = itemBuilder(id) ?: return null
        return ReflectiveResourceAccess.asNonBlankString(
            ReflectiveResourceAccess.invoke(itemBuilder, listOf("getDisplayName")),
        )
    }

    override fun icon(id: String): ItemStack? {
        return createItem(id)
    }

    override fun createItem(id: String): ItemStack? {
        if (!isAvailable()) {
            return null
        }

        val itemBuilder = itemBuilder(id) ?: return null
        return (ReflectiveResourceAccess.invoke(itemBuilder, listOf("build")) as? ItemStack)?.clone()
    }

    override fun blockIds(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("getBlockIDs", "getBlockIds", "getBlockNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveBlockId(block: Block): String? {
        if (!isAvailable() || block.type == Material.AIR) {
            return null
        }

        val id =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("getIdByBlock", "getBlockID", "getBlockId"),
                block,
            )
        return ReflectiveResourceAccess.asNonBlankString(id)
    }

    override fun placeBlock(id: String, block: Block): Boolean {
        if (!isAvailable()) {
            return false
        }

        if (callBlockPlacement(id, block)) {
            return true
        }

        return resolveBlockId(block)?.equals(id, ignoreCase = true) == true
    }

    override fun removeBlock(block: Block): Boolean {
        if (!isAvailable()) {
            return false
        }

        val result =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("remove", "breakBlock"),
                block,
            )
        if (asSuccess(result)) {
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
                className = ORAXEN_FURNITURE_CLASS,
                methodNames = listOf("getFurnitureIDs", "getFurnitureIds", "getFurnitureNames"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveFurnitureId(entity: Entity): String? {
        if (!isAvailable()) {
            return null
        }

        val id =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_FURNITURE_CLASS,
                methodNames = listOf("getIdByEntity", "getFurnitureID", "getFurnitureId"),
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
                className = ORAXEN_FURNITURE_CLASS,
                methodNames = listOf("place", "spawn"),
                id,
                location,
            )
        if (asSuccess(direct)) {
            return true
        }

        val swapped =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_FURNITURE_CLASS,
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
                className = ORAXEN_FURNITURE_CLASS,
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
            return "Oraxen plugin not installed or disabled"
        }
        if (!dataLoaded.get()) {
            return "Oraxen item registry not ready yet"
        }
        return null
    }

    private fun itemBuilder(id: String): Any? {
        return ReflectiveResourceAccess.invokeStatic(
            className = ORAXEN_ITEMS_CLASS,
            methodNames = listOf("getItemById"),
            id,
        )
    }

    private fun callBlockPlacement(
        id: String,
        block: Block,
    ): Boolean {
        val directLocation =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                id,
                block.location,
            )
        if (asSuccess(directLocation)) {
            return true
        }

        val swappedLocation =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                block.location,
                id,
            )
        if (asSuccess(swappedLocation)) {
            return true
        }

        val directBlock =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                id,
                block,
            )
        if (asSuccess(directBlock)) {
            return true
        }

        val swappedBlock =
            ReflectiveResourceAccess.invokeStatic(
                className = ORAXEN_BLOCKS_CLASS,
                methodNames = listOf("place", "setBlock"),
                block,
                id,
            )
        return asSuccess(swappedBlock)
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
                className = ORAXEN_ITEMS_CLASS,
                methodNames = listOf("getItemNames"),
            )
        return itemNames != null
    }

    private companion object {
        private const val ORAXEN_ITEMS_CLASS = "io.th0rgal.oraxen.api.OraxenItems"
        private const val ORAXEN_BLOCKS_CLASS = "io.th0rgal.oraxen.api.OraxenBlocks"
        private const val ORAXEN_FURNITURE_CLASS = "io.th0rgal.oraxen.api.OraxenFurniture"
    }
}
