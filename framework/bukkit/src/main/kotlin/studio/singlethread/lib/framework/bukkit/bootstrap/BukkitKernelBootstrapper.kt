package studio.singlethread.lib.framework.bukkit.bootstrap

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.kernel.requireService
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.bukkit.bridge.InMemoryBridgeService
import studio.singlethread.lib.framework.bukkit.config.BukkitConfigRegistry
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfigLoader
import studio.singlethread.lib.framework.bukkit.config.StorageBackendType
import studio.singlethread.lib.framework.bukkit.config.StorageProfileSelector
import studio.singlethread.lib.framework.bukkit.inventory.InventoryUiService
import studio.singlethread.lib.framework.bukkit.notifier.BukkitNotifierService
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.bukkit.resource.ResourceCapabilityBinding
import studio.singlethread.lib.framework.bukkit.scheduler.BukkitSchedulerService
import studio.singlethread.lib.framework.bukkit.storage.LazyStorage
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.text.NoopPlaceholderResolver
import studio.singlethread.lib.framework.bukkit.text.PlaceholderApiResolver
import studio.singlethread.lib.framework.bukkit.text.PlaceholderResolver
import studio.singlethread.lib.framework.bukkit.translation.BukkitTranslationService
import studio.singlethread.lib.framework.bukkit.translation.BundledTranslationInstaller
import studio.singlethread.lib.framework.bukkit.translation.NoopTranslationService
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
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageApi
import studio.singlethread.lib.storage.api.StorageFactory
import studio.singlethread.lib.storage.jdbc.JdbcStorageFactory
import studio.singlethread.lib.storage.json.JsonStorageFactory

object BukkitKernelBootstrapper {
    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        pluginVersion: String,
    ) {
        val capabilities = kernel.capabilityRegistry
        markPlatformCapabilities(capabilities)

        val configService = ConfigurateConfigService()
        kernel.registerService(ConfigService::class, configService)
        capabilities.enable(CapabilityNames.CONFIG_CONFIGURATE)

        kernel.registerService(
            ConfigRegistry::class,
            BukkitConfigRegistry(
                configService = configService,
                dataDirectory = plugin.dataFolder.toPath(),
                logWarning = plugin.logger::warning,
            ),
        )
        capabilities.enable(CapabilityNames.CONFIG_REGISTRY)

        val pluginConfig = PluginFileConfigLoader(
            configService = configService,
            dataDirectory = plugin.dataFolder.toPath(),
            pluginName = plugin.name,
            pluginVersion = pluginVersion,
        ).load()
        kernel.registerService(PluginFileConfig::class, pluginConfig)

        val textService = kernel.requireService<TextService>()
        val placeholderResolver = createPlaceholderResolver(plugin, pluginConfig, capabilities)
        kernel.registerService(PlaceholderResolver::class, placeholderResolver)
        kernel.registerService(
            BukkitTextParser::class,
            BukkitTextParser(
                textService = textService,
                placeholderResolver = placeholderResolver,
            ),
        )

        val translationService = createTranslationService(plugin, configService, capabilities)
        kernel.registerService(TranslationService::class, translationService)

        kernel.registerService(
            NotifierService::class,
            BukkitNotifierService(
                textService = textService,
                pluginName = plugin.name,
            ),
        )
        capabilities.enable(CapabilityNames.TEXT_NOTIFIER)

        kernel.registerService(SchedulerService::class, BukkitSchedulerService(plugin))
        capabilities.enable(CapabilityNames.RUNTIME_SCHEDULER)

        kernel.registerService(BridgeService::class, InMemoryBridgeService())
        capabilities.enable(CapabilityNames.BRIDGE_LOCAL)
        capabilities.disable(CapabilityNames.BRIDGE_DISTRIBUTED, "No distributed bridge backend configured")

        val dependencyLoader = BukkitLibbyDependencyLoader(plugin)
        val runtimeLoadResults = loadRuntimeDatabaseDependencies(pluginConfig, dependencyLoader)
        markDatabaseCapabilities(kernel, runtimeLoadResults)

        val storageApi = createStorageApi(capabilities, dependencyLoader)
        kernel.registerService(StorageApi::class, storageApi)

        val defaultStorage = createLazyDefaultStorage(plugin, capabilities, storageApi, pluginConfig)
        kernel.registerService(Storage::class, defaultStorage)

        configureResourceService(plugin, kernel, capabilities, pluginConfig)
    }

    fun shutdown(kernel: STKernel) {
        kernel.service(StorageApi::class)?.close()
        kernel.service(BridgeService::class)?.close()
        kernel.service(InventoryUiService::class)?.close()
    }

    private fun markPlatformCapabilities(capabilities: CapabilityRegistry) {
        capabilities.enable(CapabilityNames.PLATFORM_BUKKIT)
        if (isFoliaServer()) {
            capabilities.enable(CapabilityNames.PLATFORM_FOLIA)
            return
        }
        capabilities.disable(CapabilityNames.PLATFORM_FOLIA, "Folia runtime not detected")
    }

    private fun createPlaceholderResolver(
        plugin: JavaPlugin,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
    ): PlaceholderResolver {
        if (!pluginConfig.dependencies.integrations.placeholderApi) {
            capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "Disabled by config/depend.yml")
            return NoopPlaceholderResolver
        }

        val placeholderPluginInstalled = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        if (!placeholderPluginInstalled) {
            capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "PlaceholderAPI plugin not installed")
            return NoopPlaceholderResolver
        }

        val resolver = PlaceholderApiResolver.createOrNull(plugin.logger)
        if (resolver != null) {
            capabilities.enable(CapabilityNames.TEXT_PLACEHOLDERAPI)
            return resolver
        }

        capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "PlaceholderAPI bridge initialization failed")
        return NoopPlaceholderResolver
    }

    private fun loadRuntimeDatabaseDependencies(
        pluginConfig: PluginFileConfig,
        dependencyLoader: BukkitLibbyDependencyLoader,
    ): List<DependencyLoadResult> {
        val runtimeLibraries =
            listOf(
                LibraryDescriptor(groupId = "org.xerial", artifactId = "sqlite-jdbc", version = "3.46.1.3"),
                LibraryDescriptor(groupId = "com.mysql", artifactId = "mysql-connector-j", version = "8.4.0"),
                LibraryDescriptor(groupId = "org.postgresql", artifactId = "postgresql", version = "42.7.3"),
            )

        if (pluginConfig.dependencies.runtime.loadDatabaseDrivers) {
            return dependencyLoader.loadAll(runtimeLibraries)
        }

        return runtimeLibraries.map {
            DependencyLoadResult(
                library = it,
                status = DependencyStatus.SKIPPED,
                message = "Runtime dependency loading disabled by config/depend.yml",
            )
        }
    }

    private fun markDatabaseCapabilities(kernel: STKernel, results: List<DependencyLoadResult>) {
        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_SQLITE,
            result = results.first { it.library.artifactId == "sqlite-jdbc" },
            failureReason = "sqlite-jdbc runtime dependency failed to load",
        )

        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_MYSQL,
            result = results.first { it.library.artifactId == "mysql-connector-j" },
            failureReason = "mysql-connector-j runtime dependency failed to load",
        )

        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_POSTGRESQL,
            result = results.first { it.library.artifactId == "postgresql" },
            failureReason = "postgresql runtime dependency failed to load",
        )
    }

    private fun createStorageApi(
        capabilities: CapabilityRegistry,
        dependencyLoader: BukkitLibbyDependencyLoader,
    ): StorageApi {
        val storageFactories = mutableListOf<StorageFactory>()
        storageFactories += JsonStorageFactory(primaryThreadChecker = { Bukkit.isPrimaryThread() })
        capabilities.enable(CapabilityNames.STORAGE_JSON)

        val hasAnyJdbcDriver = listOf(
            CapabilityNames.STORAGE_SQLITE,
            CapabilityNames.STORAGE_MYSQL,
            CapabilityNames.STORAGE_POSTGRESQL,
        ).any { capabilities.isEnabled(it) }

        if (!hasAnyJdbcDriver) {
            capabilities.disable(CapabilityNames.STORAGE_JDBC, "No JDBC runtime driver could be loaded")
            return CompositeStorageApi(storageFactories)
        }

        storageFactories += JdbcStorageFactory(
            primaryThreadChecker = { Bukkit.isPrimaryThread() },
            dependencyLoader = dependencyLoader,
        )
        capabilities.enable(CapabilityNames.STORAGE_JDBC)

        return CompositeStorageApi(storageFactories)
    }

    private fun createLazyDefaultStorage(
        plugin: JavaPlugin,
        capabilities: CapabilityRegistry,
        storageApi: StorageApi,
        pluginConfig: PluginFileConfig,
    ): Storage {
        return LazyStorage {
            createDefaultStorage(plugin, capabilities, storageApi, pluginConfig)
        }
    }

    private fun createDefaultStorage(
        plugin: JavaPlugin,
        capabilities: CapabilityRegistry,
        storageApi: StorageApi,
        pluginConfig: PluginFileConfig,
    ): Storage {
        val selector = StorageProfileSelector(plugin.dataFolder.toPath())
        val requestedBackend = pluginConfig.storage.backend
        var selectedBackend = selector.resolveBackend(requestedBackend, capabilities)

        if (selectedBackend != requestedBackend) {
            plugin.logger.warning(
                "Configured storage backend ${requestedBackend.name.lowercase()} is unavailable; " +
                    "falling back to ${selectedBackend.name.lowercase()}",
            )
        }

        var resolvedStorageConfig = selector.resolveForBackend(pluginConfig.storage, selectedBackend, plugin.name)
        val created = runCatching { storageApi.create(resolvedStorageConfig) }

        if (created.isFailure) {
            plugin.logger.warning(
                "Failed to initialize ${selectedBackend.name.lowercase()} storage: ${created.exceptionOrNull()?.message}; " +
                    "falling back to json",
            )
            selectedBackend = StorageBackendType.JSON
            resolvedStorageConfig = selector.resolveForBackend(pluginConfig.storage, selectedBackend, plugin.name)
            return storageApi.create(resolvedStorageConfig)
        }

        plugin.logger.info(
            "Default storage backend: ${selectedBackend.name.lowercase()} (namespace=${resolvedStorageConfig.namespace})",
        )
        return created.getOrThrow()
    }

    private fun configureResourceService(
        plugin: JavaPlugin,
        kernel: STKernel,
        capabilities: CapabilityRegistry,
        pluginConfig: PluginFileConfig,
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

    private fun isFoliaServer(): Boolean {
        return runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }

    private fun markCapability(
        kernel: STKernel,
        capability: String,
        result: DependencyLoadResult,
        failureReason: String,
    ) {
        val capabilities = kernel.capabilityRegistry
        if (result.status == DependencyStatus.LOADED) {
            capabilities.enable(capability)
            return
        }

        val reason = result.message ?: failureReason
        capabilities.disable(capability, reason)
    }

    private fun createTranslationService(
        plugin: JavaPlugin,
        configService: ConfigService,
        capabilities: CapabilityRegistry,
    ): TranslationService {
        val translationDirectory = plugin.dataFolder.toPath().resolve("translation")
        BundledTranslationInstaller.installMissing(
            plugin = plugin,
            translationDirectory = translationDirectory,
            logger = plugin.logger,
        )

        val service = runCatching {
            BukkitTranslationService(
                configService = configService,
                configPath = plugin.dataFolder.toPath().resolve("config/translation.yml"),
                translationDirectory = translationDirectory,
                logger = plugin.logger,
            )
        }.onFailure { error ->
            plugin.logger.warning("Translation service initialization failed: ${error.message}")
        }.getOrNull()

        if (service != null) {
            capabilities.enable(CapabilityNames.TEXT_TRANSLATION)
            return service
        }

        capabilities.disable(CapabilityNames.TEXT_TRANSLATION, "Translation service bootstrap failed")
        return NoopTranslationService
    }
}
