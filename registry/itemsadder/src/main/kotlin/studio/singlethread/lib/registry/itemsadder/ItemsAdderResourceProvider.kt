package studio.singlethread.lib.registry.itemsadder

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

class ItemsAdderResourceProvider(
    private val pluginEnabledChecker: (String) -> Boolean = { pluginName ->
        Bukkit.getPluginManager().isPluginEnabled(pluginName)
    },
) : ResourceProvider, ExternalResourceProvider, ResourceItemProvider, ResourceBlockProvider, ResourceFurnitureProvider {
    override val providerId: String = "itemsadder"
    override val upstreamPluginName: String = "ItemsAdder"

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

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_STACK_CLASS,
                methodNames = listOf("getNamespacedIdsInRegistry"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveItemId(itemStack: ItemStack): String? {
        if (!isAvailable() || itemStack.type == Material.AIR) {
            return null
        }

        val customStack =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_STACK_CLASS,
                methodNames = listOf("byItemStack", "byItemStackNoCopy"),
                itemStack,
            ) ?: return null

        return namespacedId(customStack)
    }

    override fun displayName(id: String): String? {
        if (!isAvailable()) {
            return null
        }

        val stack = customStack(id) ?: return null
        return ReflectiveResourceAccess.invoke(
            target = stack,
            methodNames = listOf("getDisplayName"),
        ) as? String
    }

    override fun icon(id: String): ItemStack? {
        return createItem(id)
    }

    override fun createItem(id: String): ItemStack? {
        if (!isAvailable()) {
            return null
        }

        val stack = customStack(id) ?: return null
        return (ReflectiveResourceAccess.invoke(stack, listOf("getItemStack")) as? ItemStack)?.clone()
    }

    override fun blockIds(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_BLOCK_CLASS,
                methodNames = listOf("getNamespacedIdsInRegistry"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveBlockId(block: Block): String? {
        if (!isAvailable() || block.type == Material.AIR) {
            return null
        }

        val customBlock =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_BLOCK_CLASS,
                methodNames = listOf("byAlreadyPlaced", "byPlaced", "byAlreadyPlacedBlock"),
                block,
            ) ?: return null

        return namespacedId(customBlock)
    }

    override fun placeBlock(id: String, block: Block): Boolean {
        if (!isAvailable()) {
            return false
        }

        if (callPlace(CUSTOM_BLOCK_CLASS, id, block.location, block)) {
            return true
        }

        val customBlock = customBlock(id) ?: return false
        if (callPlace(customBlock, block.location, block)) {
            return true
        }

        return resolveBlockId(block)?.equals(id, ignoreCase = true) == true
    }

    override fun removeBlock(block: Block): Boolean {
        if (!isAvailable() || block.type == Material.AIR) {
            return false
        }

        val removedStatically =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_BLOCK_CLASS,
                methodNames = listOf("remove", "breakBlock"),
                block,
            )
        if (asSuccess(removedStatically)) {
            return true
        }

        val customBlock =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_BLOCK_CLASS,
                methodNames = listOf("byAlreadyPlaced", "byPlaced", "byAlreadyPlacedBlock"),
                block,
            )
        if (customBlock != null) {
            val removed = ReflectiveResourceAccess.invoke(customBlock, listOf("remove", "breakBlock"), block)
            if (asSuccess(removed)) {
                return true
            }
        }

        block.type = Material.AIR
        return true
    }

    override fun furnitureIds(): Collection<String> {
        if (!isAvailable()) {
            return emptyList()
        }

        val ids =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_FURNITURE_CLASS,
                methodNames = listOf("getNamespacedIdsInRegistry"),
            )
        return ReflectiveResourceAccess.asStringCollection(ids)
    }

    override fun resolveFurnitureId(entity: Entity): String? {
        if (!isAvailable()) {
            return null
        }

        val furniture =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_FURNITURE_CLASS,
                methodNames = listOf("byAlreadySpawned", "byEntity", "byAlreadySpawnedEntity"),
                entity,
            ) ?: return null

        return namespacedId(furniture)
    }

    override fun placeFurniture(id: String, location: Location): Boolean {
        if (!isAvailable()) {
            return false
        }

        if (callPlace(CUSTOM_FURNITURE_CLASS, id, location)) {
            return true
        }

        val furniture = customFurniture(id) ?: return false
        return callPlace(furniture, location)
    }

    override fun removeFurniture(entity: Entity): Boolean {
        if (!isAvailable()) {
            return false
        }

        val removedStatically =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_FURNITURE_CLASS,
                methodNames = listOf("remove", "despawn"),
                entity,
            )
        if (asSuccess(removedStatically)) {
            return true
        }

        val furniture =
            ReflectiveResourceAccess.invokeStatic(
                className = CUSTOM_FURNITURE_CLASS,
                methodNames = listOf("byAlreadySpawned", "byEntity", "byAlreadySpawnedEntity"),
                entity,
            )
        if (furniture != null) {
            val removed = ReflectiveResourceAccess.invoke(furniture, listOf("remove", "despawn"), entity)
            if (asSuccess(removed)) {
                return true
            }
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
            return "ItemsAdder plugin not installed or disabled"
        }
        if (!dataLoaded.get()) {
            return "Waiting for ItemsAdderLoadDataEvent"
        }
        return null
    }

    private fun customStack(id: String): Any? {
        return ReflectiveResourceAccess.invokeStatic(
            className = CUSTOM_STACK_CLASS,
            methodNames = listOf("getInstance"),
            id,
        )
    }

    private fun customBlock(id: String): Any? {
        return ReflectiveResourceAccess.invokeStatic(
            className = CUSTOM_BLOCK_CLASS,
            methodNames = listOf("getInstance"),
            id,
        )
    }

    private fun customFurniture(id: String): Any? {
        return ReflectiveResourceAccess.invokeStatic(
            className = CUSTOM_FURNITURE_CLASS,
            methodNames = listOf("getInstance"),
            id,
        )
    }

    private fun namespacedId(instance: Any): String? {
        return ReflectiveResourceAccess.asNonBlankString(
            ReflectiveResourceAccess.invoke(
                target = instance,
                methodNames = listOf("getNamespacedID", "getNamespacedId", "getId", "getID"),
            ),
        )
    }

    private fun callPlace(
        className: String,
        id: String,
        vararg targets: Any,
    ): Boolean {
        val args = arrayOf<Any>(id, *targets)
        val result =
            ReflectiveResourceAccess.invokeStatic(
                className = className,
                methodNames = listOf("place", "spawn"),
                *args,
            )
        return asSuccess(result)
    }

    private fun callPlace(
        target: Any,
        vararg placementTargets: Any,
    ): Boolean {
        val result =
            ReflectiveResourceAccess.invoke(
                target = target,
                methodNames = listOf("place", "spawn"),
                *placementTargets,
            )
        return asSuccess(result)
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
        val loadedFlag =
            ReflectiveResourceAccess.invokeStatic(
                className = "dev.lone.itemsadder.api.ItemsAdder",
                methodNames = listOf("areItemsLoaded"),
            ) as? Boolean
        if (loadedFlag != null) {
            return loadedFlag
        }

        val allItems =
            ReflectiveResourceAccess.invokeStatic(
                className = "dev.lone.itemsadder.api.ItemsAdder",
                methodNames = listOf("getAllItems"),
            )
        return allItems != null
    }

    private companion object {
        private const val CUSTOM_STACK_CLASS = "dev.lone.itemsadder.api.CustomStack"
        private const val CUSTOM_BLOCK_CLASS = "dev.lone.itemsadder.api.CustomBlock"
        private const val CUSTOM_FURNITURE_CLASS = "dev.lone.itemsadder.api.CustomFurniture"
    }
}
