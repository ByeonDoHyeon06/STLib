package studio.singlethread.lib.registry.ecoitems

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.capability.ResourceCapability
import studio.singlethread.lib.registry.common.provider.ExternalResourceProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import java.util.concurrent.atomic.AtomicBoolean

class EcoItemsResourceProvider(
    private val pluginEnabledChecker: (String) -> Boolean = { pluginName ->
        Bukkit.getPluginManager().isPluginEnabled(pluginName)
    },
) : ResourceProvider, ExternalResourceProvider, ResourceItemProvider {
    override val providerId: String = "ecoitems"
    override val upstreamPluginName: String = "EcoItems"

    private val available = AtomicBoolean(false)

    override fun isAvailable(): Boolean {
        refreshState()
        return available.get()
    }

    override fun capabilities(): Set<ResourceCapability> {
        return setOf(
            ResourceCapability.ID_LOOKUP,
            ResourceCapability.ITEM_LOOKUP,
            ResourceCapability.DISPLAY_NAME,
            ResourceCapability.ICON,
            ResourceCapability.CREATE_ITEM,
        )
    }

    override fun resolveItemId(itemStack: ItemStack): String? {
        if (!isAvailable() || itemStack.type == Material.AIR) {
            return null
        }

        return invokeStatic("com.willfp.ecoitems.items.EcoItems", "getByItem", itemStack)
            ?.let { invokeFirstString(it, "getId", "getID", "id", "name") }
            ?: invokeStatic("com.willfp.ecoitems.api.EcoItemsAPI", "getByItem", itemStack)
                ?.let { invokeFirstString(it, "getId", "getID", "id", "name") }
    }

    override fun refreshState() {
        val pluginEnabled = pluginEnabledChecker(upstreamPluginName)
        available.set(pluginEnabled && (classExists("com.willfp.ecoitems.items.EcoItems") || classExists("com.willfp.ecoitems.api.EcoItemsAPI")))
    }

    override fun onUpstreamPluginDisabled() {
        available.set(false)
    }

    override fun unavailableReason(): String? {
        refreshState()
        if (available.get()) {
            return null
        }
        if (!pluginEnabledChecker(upstreamPluginName)) {
            return "EcoItems plugin not installed or disabled"
        }
        return "EcoItems API classes were not found"
    }

    override fun ids(): Collection<String> {
        val values =
            invokeStatic("com.willfp.ecoitems.items.EcoItems", "values")
                ?: invokeStatic("com.willfp.ecoitems.api.EcoItemsAPI", "values")
                ?: invokeStatic("com.willfp.ecoitems.items.EcoItems", "getItems")
                ?: invokeStatic("com.willfp.ecoitems.api.EcoItemsAPI", "getItems")
                ?: return emptyList()

        return when (values) {
            is Map<*, *> -> values.keys.mapNotNull { it?.toString() }.filter { it.isNotBlank() }
            is Collection<*> -> values.mapNotNull { item ->
                item?.let { invokeFirstString(it, "getId", "getID", "id", "name") }
            }.filter { it.isNotBlank() }

            else -> emptyList()
        }
    }

    override fun displayName(id: String): String? {
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
        val ecoItem = ecoItem(id) ?: return null
        if (ecoItem is ItemStack) {
            return ecoItem.clone()
        }

        val directItem = invokeFirst(ecoItem, "getItemStack", "toItemStack", "build", "getItem")
        if (directItem is ItemStack) {
            return directItem.clone()
        }

        return null
    }

    private fun ecoItem(id: String): Any? {
        val normalized = id.trim()
        if (normalized.isBlank()) {
            return null
        }

        return invokeStatic("com.willfp.ecoitems.items.EcoItems", "getByID", normalized)
            ?: invokeStatic("com.willfp.ecoitems.api.EcoItemsAPI", "getByID", normalized)
            ?: invokeStatic("com.willfp.ecoitems.items.EcoItems", "get", normalized)
            ?: invokeStatic("com.willfp.ecoitems.api.EcoItemsAPI", "get", normalized)
    }

    private fun classExists(className: String): Boolean {
        return runCatching { Class.forName(className) }.isSuccess
    }

    private fun invokeStatic(
        className: String,
        methodName: String,
        vararg args: Any,
    ): Any? {
        return runCatching {
            val type = Class.forName(className)
            val method = type.methods.firstOrNull {
                it.name == methodName && it.parameterCount == args.size
            } ?: return null
            method.invoke(null, *args)
        }.getOrNull()
    }

    private fun invokeFirst(
        target: Any,
        vararg methodNames: String,
    ): Any? {
        return methodNames.asSequence()
            .mapNotNull { methodName ->
                runCatching {
                    target.javaClass.methods
                        .firstOrNull { it.name == methodName && it.parameterCount == 0 }
                        ?.invoke(target)
                }.getOrNull()
            }
            .firstOrNull()
    }

    private fun invokeFirstString(
        target: Any,
        vararg methodNames: String,
    ): String? {
        return invokeFirst(target, *methodNames)?.toString()
    }
}
