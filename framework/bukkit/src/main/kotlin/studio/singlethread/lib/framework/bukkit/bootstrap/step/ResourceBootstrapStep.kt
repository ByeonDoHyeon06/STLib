package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.bukkit.resource.ResourceCapabilityBinding
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.registry.common.provider.ExternalResourceProvider
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import studio.singlethread.lib.registry.common.service.DefaultResourceService
import studio.singlethread.lib.registry.common.service.ResourceService
import studio.singlethread.lib.registry.ecoitems.EcoItemsResourceProvider
import studio.singlethread.lib.registry.itemsadder.ItemsAdderResourceProvider
import studio.singlethread.lib.registry.mmoitems.MMOItemsResourceProvider
import studio.singlethread.lib.registry.nexo.NexoResourceProvider
import studio.singlethread.lib.registry.oraxen.OraxenResourceProvider
import studio.singlethread.lib.registry.vanilla.VanillaResourceProvider

internal object ResourceBootstrapStep {
    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
    ) {
        val providers = mutableListOf<ResourceProvider>()
        val bindings = mutableListOf<ResourceCapabilityBinding>()

        registerResourceProvider(
            enabledByConfig = pluginConfig.dependencies.integrations.itemsAdder,
            capability = CapabilityNames.RESOURCE_ITEMSADDER,
            provider = ItemsAdderResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )
        registerResourceProvider(
            enabledByConfig = pluginConfig.dependencies.integrations.oraxen,
            capability = CapabilityNames.RESOURCE_ORAXEN,
            provider = OraxenResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )
        registerResourceProvider(
            enabledByConfig = pluginConfig.dependencies.integrations.nexo,
            capability = CapabilityNames.RESOURCE_NEXO,
            provider = NexoResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )
        registerResourceProvider(
            enabledByConfig = pluginConfig.dependencies.integrations.mmoItems,
            capability = CapabilityNames.RESOURCE_MMOITEMS,
            provider = MMOItemsResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )
        registerResourceProvider(
            enabledByConfig = pluginConfig.dependencies.integrations.ecoItems,
            capability = CapabilityNames.RESOURCE_ECOITEMS,
            provider = EcoItemsResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )
        registerResourceProvider(
            enabledByConfig = true,
            capability = CapabilityNames.RESOURCE_MINECRAFT,
            provider = VanillaResourceProvider(),
            providers = providers,
            bindings = bindings,
            capabilities = capabilities,
        )

        kernel.registerService(ResourceService::class, DefaultResourceService(providers))
        kernel.registerService(
            BukkitResourceIntegrationRuntime::class,
            BukkitResourceIntegrationRuntime(
                plugin = plugin,
                capabilityRegistry = capabilities,
                bindings = bindings,
            ),
        )
    }

    private fun registerResourceProvider(
        enabledByConfig: Boolean,
        capability: String,
        provider: ResourceProvider,
        providers: MutableList<ResourceProvider>,
        bindings: MutableList<ResourceCapabilityBinding>,
        capabilities: CapabilityRegistry,
    ) {
        if (!enabledByConfig) {
            capabilities.disable(capability, "Disabled by config/depend.yml")
            return
        }

        providers += provider
        val binding = ResourceCapabilityBinding(capability = capability, provider = provider)
        bindings += binding
        syncResourceCapability(binding, capabilities)
    }

    private fun syncResourceCapability(
        binding: ResourceCapabilityBinding,
        capabilities: CapabilityRegistry,
    ) {
        val provider = binding.provider
        if (provider is ExternalResourceProvider) {
            provider.refreshState()
        }

        if (provider.isAvailable()) {
            capabilities.enable(binding.capability)
            return
        }

        val reason =
            (provider as? ExternalResourceProvider)
                ?.unavailableReason()
                ?.takeIf { it.isNotBlank() }
                ?: "${provider.providerId} provider unavailable"
        capabilities.disable(binding.capability, reason)
    }
}
