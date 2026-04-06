package studio.singlethread.lib.framework.bukkit.bootstrap

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bootstrap.step.BridgeBootstrapStep
import studio.singlethread.lib.framework.bukkit.bootstrap.step.ConfigBootstrapStep
import studio.singlethread.lib.framework.bukkit.bootstrap.step.PlatformCapabilityStep
import studio.singlethread.lib.framework.bukkit.bootstrap.step.ResourceBootstrapStep
import studio.singlethread.lib.framework.bukkit.bootstrap.step.StorageBootstrapStep
import studio.singlethread.lib.framework.bukkit.bootstrap.step.TextBootstrapStep
import studio.singlethread.lib.framework.bukkit.bridge.BridgeRuntimeInfo
import studio.singlethread.lib.framework.bukkit.gui.STGuiService
import studio.singlethread.lib.storage.api.StorageApi
import kotlin.system.measureTimeMillis

object BukkitKernelBootstrapper {
    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        pluginVersion: String,
    ): BootstrapDiagnostics {
        val capabilities = kernel.capabilityRegistry
        val stepDurations = linkedMapOf<String, Long>()
        val dependencyResults = mutableListOf<studio.singlethread.lib.dependency.common.model.DependencyLoadResult>()

        runBootstrapStep(stepDurations, "platform-capabilities") {
            PlatformCapabilityStep.bootstrap(capabilities)
        }

        val configBootstrap =
            runBootstrapStep(stepDurations, "config") {
                ConfigBootstrapStep.bootstrap(
                    plugin = plugin,
                    kernel = kernel,
                    capabilities = capabilities,
                    pluginVersion = pluginVersion,
                )
            }

        runBootstrapStep(stepDurations, "text") {
            TextBootstrapStep.bootstrap(
                plugin = plugin,
                kernel = kernel,
                configService = configBootstrap.configService,
                pluginConfig = configBootstrap.pluginConfig,
                capabilities = capabilities,
            )
        }

        val dependencyLoader = BukkitLibbyDependencyLoader(plugin)

        val bridgeBootstrap =
            runBootstrapStep(stepDurations, "bridge") {
                BridgeBootstrapStep.bootstrap(
                    plugin = plugin,
                    pluginConfig = configBootstrap.pluginConfig,
                    capabilities = capabilities,
                    dependencyLoader = dependencyLoader,
                )
            }
        dependencyResults += bridgeBootstrap.dependencyResults
        kernel.registerService(BridgeService::class, bridgeBootstrap.service)
        kernel.registerService(BridgeRuntimeInfo::class, bridgeBootstrap.runtimeInfo)

        val storageBootstrap =
            runBootstrapStep(stepDurations, "storage") {
            StorageBootstrapStep.bootstrap(
                plugin = plugin,
                kernel = kernel,
                pluginConfig = configBootstrap.pluginConfig,
                capabilities = capabilities,
                dependencyLoader = dependencyLoader,
            )
        }
        dependencyResults += storageBootstrap.dependencyResults

        runBootstrapStep(stepDurations, "resource") {
            ResourceBootstrapStep.bootstrap(
                plugin = plugin,
                kernel = kernel,
                pluginConfig = configBootstrap.pluginConfig,
                capabilities = capabilities,
            )
        }

        return BootstrapDiagnostics(
            stepDurationsMillis = stepDurations.toMap(),
            dependencyResults = dependencyResults.toList(),
            bridgeMode = bridgeBootstrap.runtimeInfo.mode.name.lowercase(),
            bridgeNodeId = bridgeBootstrap.runtimeInfo.nodeId.value,
            bridgeNamespace = bridgeBootstrap.runtimeInfo.namespace,
            components = listOf("config", "text", "bridge", "storage", "resource"),
        )
    }

    fun shutdown(kernel: STKernel) {
        kernel.service(StorageApi::class)?.close()
        kernel.service(BridgeService::class)?.close()
        kernel.service(STGuiService::class)?.close()
    }

    private inline fun <T> runBootstrapStep(
        stepDurations: MutableMap<String, Long>,
        stepName: String,
        block: () -> T,
    ): T {
        var result: T? = null
        val elapsedMillis = measureTimeMillis { result = block() }
        stepDurations[stepName] = elapsedMillis
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}
