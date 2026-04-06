package studio.singlethread.lib.platform.bukkit.bootstrap

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.platform.common.bootstrap.PlatformBootstrap
import studio.singlethread.lib.registry.common.service.DefaultResourceService
import studio.singlethread.lib.registry.common.service.ResourceService
import studio.singlethread.lib.registry.itemsadder.ItemsAdderResourceProvider
import studio.singlethread.lib.registry.nexo.NexoResourceProvider
import studio.singlethread.lib.registry.oraxen.OraxenResourceProvider
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.StorageApi
import studio.singlethread.lib.storage.api.StorageFactory
import studio.singlethread.lib.storage.json.JsonStorageFactory
import studio.singlethread.lib.storage.jdbc.JdbcStorageFactory

class BukkitPlatformBootstrap : PlatformBootstrap<JavaPlugin> {
    override fun bootstrap(platform: JavaPlugin, kernel: STKernel) {
        val capabilities = kernel.capabilityRegistry

        capabilities.enable(CapabilityNames.PLATFORM_BUKKIT)
        if (isFoliaServer()) {
            capabilities.enable(CapabilityNames.PLATFORM_FOLIA)
        } else {
            capabilities.disable(CapabilityNames.PLATFORM_FOLIA, "Folia runtime not detected")
        }

        val configService: ConfigService = ConfigurateConfigService()
        kernel.registerService(ConfigService::class, configService)
        capabilities.enable(CapabilityNames.CONFIG_CONFIGURATE)

        val dependencyLoader = BukkitLibbyDependencyLoader(platform)
        val runtimeLibraries =
            listOf(
                LibraryDescriptor(
                    groupId = "org.xerial",
                    artifactId = "sqlite-jdbc",
                    version = "3.46.1.3",
                ),
                LibraryDescriptor(
                    groupId = "com.mysql",
                    artifactId = "mysql-connector-j",
                    version = "8.4.0",
                ),
                LibraryDescriptor(
                    groupId = "org.postgresql",
                    artifactId = "postgresql",
                    version = "42.7.3",
                ),
            )

        val loadResults = dependencyLoader.loadAll(runtimeLibraries)

        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_SQLITE,
            result = loadResults.first { it.library.artifactId == "sqlite-jdbc" }.status,
            reason = "sqlite-jdbc runtime dependency failed to load",
        )

        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_MYSQL,
            result = loadResults.first { it.library.artifactId == "mysql-connector-j" }.status,
            reason = "mysql-connector-j runtime dependency failed to load",
        )

        markCapability(
            kernel = kernel,
            capability = CapabilityNames.STORAGE_POSTGRESQL,
            result = loadResults.first { it.library.artifactId == "postgresql" }.status,
            reason = "postgresql runtime dependency failed to load",
        )

        val storageFactories = mutableListOf<StorageFactory>()
        storageFactories += JsonStorageFactory(primaryThreadChecker = { Bukkit.isPrimaryThread() })
        capabilities.enable(CapabilityNames.STORAGE_JSON)

        val hasAnyJdbcDriver = listOf(
            CapabilityNames.STORAGE_SQLITE,
            CapabilityNames.STORAGE_MYSQL,
            CapabilityNames.STORAGE_POSTGRESQL,
        ).any { capabilities.isEnabled(it) }

        if (hasAnyJdbcDriver) {
            storageFactories += JdbcStorageFactory(
                primaryThreadChecker = { Bukkit.isPrimaryThread() },
                dependencyLoader = dependencyLoader,
            )
            capabilities.enable(CapabilityNames.STORAGE_JDBC)
        } else {
            capabilities.disable(CapabilityNames.STORAGE_JDBC, "No JDBC runtime driver could be loaded")
        }

        val storageApi: StorageApi = CompositeStorageApi(storageFactories)
        kernel.registerService(StorageApi::class, storageApi)

        val itemsAdder = ItemsAdderResourceProvider()
        val oraxen = OraxenResourceProvider()
        val nexo = NexoResourceProvider()

        val resourceService = DefaultResourceService(listOf(itemsAdder, oraxen, nexo))
        kernel.registerService(ResourceService::class, resourceService)

        if (itemsAdder.isAvailable()) {
            capabilities.enable(CapabilityNames.RESOURCE_ITEMSADDER)
        } else {
            capabilities.disable(CapabilityNames.RESOURCE_ITEMSADDER, "ItemsAdder plugin not installed")
        }

        if (oraxen.isAvailable()) {
            capabilities.enable(CapabilityNames.RESOURCE_ORAXEN)
        } else {
            capabilities.disable(CapabilityNames.RESOURCE_ORAXEN, "Oraxen plugin not installed")
        }

        if (nexo.isAvailable()) {
            capabilities.enable(CapabilityNames.RESOURCE_NEXO)
        } else {
            capabilities.disable(CapabilityNames.RESOURCE_NEXO, "Nexo plugin not installed")
        }

        platform.logger.info("STLib platform bootstrap completed")
    }

    private fun isFoliaServer(): Boolean {
        return runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }

    private fun markCapability(
        kernel: STKernel,
        capability: String,
        result: DependencyStatus,
        reason: String,
    ) {
        val capabilities = kernel.capabilityRegistry
        if (result == DependencyStatus.LOADED || result == DependencyStatus.PRESENT) {
            capabilities.enable(capability)
            return
        }
        capabilities.disable(capability, reason)
    }
}
