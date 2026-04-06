package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.config.StorageBackendType
import studio.singlethread.lib.framework.bukkit.config.StorageProfileSelector
import studio.singlethread.lib.framework.bukkit.gui.STGuiService
import studio.singlethread.lib.framework.bukkit.storage.LazyStorage
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageApi
import studio.singlethread.lib.storage.api.StorageFactory
import studio.singlethread.lib.storage.jdbc.JdbcStorageFactory
import studio.singlethread.lib.storage.json.JsonStorageFactory

internal object StorageBootstrapStep {
    data class StorageBootstrapResult(
        val dependencyResults: List<DependencyLoadResult>,
    )

    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
        dependencyLoader: BukkitLibbyDependencyLoader,
    ): StorageBootstrapResult {
        val runtimeLoadResults = loadRuntimeDatabaseDependencies(plugin, pluginConfig, dependencyLoader)
        markDatabaseCapabilities(kernel, runtimeLoadResults)

        val storageApi = createStorageApi(capabilities, dependencyLoader)
        kernel.registerService(StorageApi::class, storageApi)

        val defaultStorage = createLazyDefaultStorage(plugin, capabilities, storageApi, pluginConfig)
        kernel.registerService(Storage::class, defaultStorage)
        return StorageBootstrapResult(dependencyResults = runtimeLoadResults)
    }

    private fun loadRuntimeDatabaseDependencies(
        plugin: JavaPlugin,
        pluginConfig: PluginFileConfig,
        dependencyLoader: BukkitLibbyDependencyLoader,
    ): List<DependencyLoadResult> {
        val runtimeLibraries =
            listOf(
                LibraryDescriptor(groupId = "org.xerial", artifactId = "sqlite-jdbc", version = "3.46.1.3"),
                LibraryDescriptor(groupId = "com.mysql", artifactId = "mysql-connector-j", version = "8.4.0"),
                LibraryDescriptor(groupId = "org.postgresql", artifactId = "postgresql", version = "42.7.3"),
            )
        val preflightClasses =
            mapOf(
                "sqlite-jdbc" to listOf("org.sqlite.JDBC"),
                "mysql-connector-j" to listOf("com.mysql.cj.jdbc.Driver"),
                "postgresql" to listOf("org.postgresql.Driver"),
            )

        if (pluginConfig.dependencies.runtime.loadDatabaseDrivers) {
            return runtimeLibraries.map { library ->
                RuntimeDependencyBootstrap.load(
                    plugin = plugin,
                    dependencyLoader = dependencyLoader,
                    library = library,
                    preflightClassNames = preflightClasses[library.artifactId].orEmpty(),
                )
            }
        }

        return runtimeLibraries.map {
            DependencyLoadResult(
                library = it,
                status = DependencyStatus.SKIPPED_DISABLED,
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

        val hasAnyJdbcDriver =
            listOf(
                CapabilityNames.STORAGE_SQLITE,
                CapabilityNames.STORAGE_MYSQL,
                CapabilityNames.STORAGE_POSTGRESQL,
            ).any { capabilities.isEnabled(it) }

        if (!hasAnyJdbcDriver) {
            capabilities.disable(CapabilityNames.STORAGE_JDBC, "No JDBC runtime driver could be loaded")
            return CompositeStorageApi(storageFactories)
        }

        storageFactories +=
            JdbcStorageFactory(
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

    private fun markCapability(
        kernel: STKernel,
        capability: String,
        result: DependencyLoadResult,
        failureReason: String,
    ) {
        val capabilities = kernel.capabilityRegistry
        if (DependencyCapabilityPolicy.isUsable(result)) {
            capabilities.enable(capability)
            return
        }

        val reason = DependencyCapabilityPolicy.disableReason(result, failureReason)
        capabilities.disable(capability, reason)
    }
}
