package studio.singlethread.lib.registry.mmoitems

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.registry.common.capability.ResourceCapability
import studio.singlethread.lib.registry.common.provider.ExternalResourceProvider
import studio.singlethread.lib.registry.common.provider.ResourceItemProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import java.util.concurrent.atomic.AtomicBoolean

class MMOItemsResourceProvider(
    private val pluginEnabledChecker: (String) -> Boolean = { pluginName ->
        Bukkit.getPluginManager().isPluginEnabled(pluginName)
    },
) : ResourceProvider, ExternalResourceProvider, ResourceItemProvider {
    override val providerId: String = "mmoitems"
    override val upstreamPluginName: String = "MMOItems"

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

        val resolved =
            invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getID", itemStack)
                ?: invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getId", itemStack)
        return resolved?.toString()?.takeIf { it.isNotBlank() }
    }

    override fun refreshState() {
        val pluginEnabled = pluginEnabledChecker(upstreamPluginName)
        available.set(pluginEnabled && (classExists("net.Indyuce.mmoitems.api.Type") || classExists("net.Indyuce.mmoitems.api.MMOItems")))
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
            return "MMOItems plugin not installed or disabled"
        }
        return "MMOItems API classes were not found"
    }

    override fun ids(): Collection<String> {
        val typeClass = runCatching { Class.forName("net.Indyuce.mmoitems.api.Type") }.getOrNull() ?: return emptyList()
        val values = runCatching { typeClass.getMethod("values").invoke(null) as? Array<*> }.getOrNull().orEmpty()

        val ids = linkedSetOf<String>()
        values.forEach { type ->
            if (type == null) {
                return@forEach
            }
            val typeId = invokeFirstString(type, "getId", "name").orEmpty().lowercase()
            if (typeId.isBlank()) {
                return@forEach
            }

            val templates = invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getItems", type)
                ?: invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getTemplates", type)
                ?: return@forEach

            when (templates) {
                is Map<*, *> -> templates.keys.forEach { key ->
                    val itemId = key?.toString().orEmpty()
                    if (itemId.isNotBlank()) {
                        ids += "$typeId:$itemId"
                    }
                }

                is Collection<*> -> templates.forEach { template ->
                    val itemId = template?.let { invokeFirstString(it, "getId", "id", "name") }.orEmpty()
                    if (itemId.isNotBlank()) {
                        ids += "$typeId:$itemId"
                    }
                }
            }
        }

        return ids.toList()
    }

    override fun displayName(id: String): String? {
        val stack = createItem(id) ?: return null
        val meta = stack.itemMeta ?: return null
        if (meta.hasDisplayName()) {
            return meta.displayName
        }
        return id
    }

    override fun icon(id: String): ItemStack? {
        return createItem(id)
    }

    override fun createItem(id: String): ItemStack? {
        val (typeId, itemId) = parseQualifiedId(id) ?: return null
        val mmoItem = resolveMmoItem(typeId, itemId) ?: return null
        if (mmoItem is ItemStack) {
            return mmoItem.clone()
        }

        val builtViaNoArgBuilder = runCatching {
            val builder = mmoItem.javaClass.methods
                .firstOrNull { it.name == "newBuilder" && it.parameterCount == 0 }
                ?.invoke(mmoItem)
            builder?.let { buildItemStack(it) }
        }.getOrNull()
        if (builtViaNoArgBuilder != null) {
            return builtViaNoArgBuilder
        }

        val builtViaLevelBuilder = runCatching {
            val builder = mmoItem.javaClass.methods
                .firstOrNull { it.name == "newBuilder" && it.parameterCount == 1 && it.parameterTypes[0] == Int::class.javaPrimitiveType }
                ?.invoke(mmoItem, 1)
            builder?.let { buildItemStack(it) }
        }.getOrNull()
        if (builtViaLevelBuilder != null) {
            return builtViaLevelBuilder
        }

        return buildItemStack(mmoItem)
    }

    private fun resolveMmoItem(
        typeId: String,
        itemId: String,
    ): Any? {
        return invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getItem", typeId, itemId)
            ?: invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getMMOItem", typeId, itemId)
            ?: invokeStatic("net.Indyuce.mmoitems.api.MMOItems", "getTemplate", typeId, itemId)
    }

    private fun buildItemStack(source: Any): ItemStack? {
        if (source is ItemStack) {
            return source.clone()
        }

        val direct = invokeFirst(source, "newItemStack", "toItemStack", "build", "asItemStack")
        if (direct is ItemStack) {
            return direct.clone()
        }

        return null
    }

    private fun parseQualifiedId(value: String): Pair<String, String>? {
        val normalized = value.trim()
        val split = normalized.split(':', limit = 2)
        if (split.size != 2) {
            return null
        }

        val typeId = split[0].trim().lowercase()
        val itemId = split[1].trim()
        if (typeId.isBlank() || itemId.isBlank()) {
            return null
        }
        return typeId to itemId
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
