package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.bukkit.bridge.BridgeRuntimeInfo
import studio.singlethread.lib.framework.bukkit.bridge.CompositeBridgeService
import studio.singlethread.lib.framework.bukkit.bridge.InMemoryBridgeService
import studio.singlethread.lib.framework.bukkit.bridge.NamespacedBridgeService
import studio.singlethread.lib.framework.bukkit.bridge.RedissonBridgeService
import studio.singlethread.lib.framework.bukkit.config.BridgeMode
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.support.STCallbackFailureLogger
import studio.singlethread.lib.platform.common.capability.CapabilityNames

internal data class BridgeBootstrapResult(
    val service: BridgeService,
    val runtimeInfo: BridgeRuntimeInfo,
    val dependencyResults: List<DependencyLoadResult>,
)

internal object BridgeBootstrapStep {
    fun bootstrap(
        plugin: JavaPlugin,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
        dependencyLoader: BukkitLibbyDependencyLoader,
    ): BridgeBootstrapResult {
        val bridgeConfig = pluginConfig.bridge
        val nodeId = resolveNodeId(plugin, bridgeConfig.nodeId)
        val maxPendingRequests = bridgeConfig.maxPendingRequests.coerceAtLeast(1)
        val dependencyResults = mutableListOf<DependencyLoadResult>()
        val localBridge =
            InMemoryBridgeService(
                localNodeId = nodeId,
                maxPendingRequests = maxPendingRequests,
                callbackFailureReporter = { phase, error ->
                    STCallbackFailureLogger.log(
                        logger = plugin.logger,
                        subsystem = "Bridge",
                        phase = phase,
                        error = error,
                        debugEnabled = { pluginConfig.plugin.debug },
                    )
                },
            )

        capabilities.enable(CapabilityNames.BRIDGE_CODEC)
        capabilities.enable(CapabilityNames.BRIDGE_RPC)

        val distributed =
            createDistributedBridge(
                plugin = plugin,
                pluginConfig = pluginConfig,
                dependencyLoader = dependencyLoader,
                nodeId = nodeId,
                capabilities = capabilities,
                maxPendingRequests = maxPendingRequests,
                dependencyResults = dependencyResults,
            )
        val rootBridge =
            when (bridgeConfig.mode) {
                BridgeMode.LOCAL -> localBridge
                BridgeMode.REDIS -> distributed ?: localBridge
                BridgeMode.COMPOSITE -> CompositeBridgeService(local = localBridge, distributed = distributed)
            }

        val localEnabled = bridgeConfig.mode != BridgeMode.REDIS || distributed == null
        if (localEnabled) {
            capabilities.enable(CapabilityNames.BRIDGE_LOCAL)
        } else {
            capabilities.disable(CapabilityNames.BRIDGE_LOCAL, "Bridge mode is redis-only")
        }

        val distributedEnabled = distributed != null
        if (distributedEnabled) {
            capabilities.enable(CapabilityNames.BRIDGE_DISTRIBUTED)
            capabilities.enable(CapabilityNames.BRIDGE_REDIS)
        } else {
            capabilities.disable(CapabilityNames.BRIDGE_DISTRIBUTED, "Distributed bridge backend unavailable")
            capabilities.disable(CapabilityNames.BRIDGE_REDIS, "Redisson backend unavailable")
        }

        val message =
            when {
                bridgeConfig.mode == BridgeMode.LOCAL ->
                    "mode=local, nodeId=${nodeId.value}, namespace=${bridgeConfig.namespace}"

                distributedEnabled ->
                    "mode=${bridgeConfig.mode.name.lowercase()}, nodeId=${nodeId.value}, namespace=${bridgeConfig.namespace}, distributed=redis"

                else ->
                    "mode=${bridgeConfig.mode.name.lowercase()}, nodeId=${nodeId.value}, namespace=${bridgeConfig.namespace}, fallback=local"
            }

        val runtimeInfo =
            BridgeRuntimeInfo(
                mode = bridgeConfig.mode,
                nodeId = nodeId,
                namespace = bridgeConfig.namespace,
                requestTimeoutMillis = bridgeConfig.requestTimeoutMillis,
                maxPendingRequests = maxPendingRequests,
                distributedEnabled = distributedEnabled,
                redisConnected = distributedEnabled,
                message = message,
                metricsProvider = { rootBridge.metrics() },
            )

        return BridgeBootstrapResult(
            service = NamespacedBridgeService(rootBridge, bridgeConfig.namespace),
            runtimeInfo = runtimeInfo,
            dependencyResults = dependencyResults.toList(),
        )
    }

    private fun createDistributedBridge(
        plugin: JavaPlugin,
        pluginConfig: PluginFileConfig,
        dependencyLoader: BukkitLibbyDependencyLoader,
        nodeId: BridgeNodeId,
        capabilities: CapabilityRegistry,
        maxPendingRequests: Int,
        dependencyResults: MutableList<DependencyLoadResult>,
    ): BridgeService? {
        val mode = pluginConfig.bridge.mode
        if (mode == BridgeMode.LOCAL) {
            return null
        }

        val redissonLibrary =
            LibraryDescriptor(
                groupId = "org.redisson",
                artifactId = "redisson-all",
                version = "3.35.0",
            )

        val loadResult =
            if (pluginConfig.dependencies.runtime.loadRedisBridge) {
                RuntimeDependencyBootstrap.load(
                    plugin = plugin,
                    dependencyLoader = dependencyLoader,
                    library = redissonLibrary,
                    preflightClassNames = listOf("org.redisson.Redisson"),
                )
            } else {
                DependencyLoadResult(
                    library = redissonLibrary,
                    status = DependencyStatus.SKIPPED_DISABLED,
                    message = "Redis bridge dependency loading disabled by config/depend.yml",
                )
            }
        dependencyResults += loadResult

        if (!DependencyCapabilityPolicy.isUsable(loadResult)) {
            plugin.logger.warning(
                "Distributed bridge dependency unavailable: ${loadResult.message ?: loadResult.status.name.lowercase()}",
            )
            capabilities.disable(
                CapabilityNames.BRIDGE_REDIS,
                DependencyCapabilityPolicy.disableReason(loadResult, "Redisson dependency is unavailable"),
            )
            return null
        }

        return RedissonBridgeService.createOrNull(
            nodeId = nodeId,
            namespace = pluginConfig.bridge.namespace,
            redis = pluginConfig.bridge.redis,
            maxPendingRequests = maxPendingRequests,
            logWarning = plugin.logger::warning,
            logger = plugin.logger,
            debugLoggingEnabled = { pluginConfig.plugin.debug },
        )?.also {
            capabilities.enable(CapabilityNames.BRIDGE_REDIS)
        } ?: run {
            capabilities.disable(CapabilityNames.BRIDGE_REDIS, "Redisson backend initialization failed")
            null
        }
    }

    private fun resolveNodeId(
        plugin: JavaPlugin,
        configured: String,
    ): BridgeNodeId {
        val trimmed = configured.trim()
        if (trimmed.isNotBlank() && !trimmed.equals("auto", ignoreCase = true)) {
            return BridgeNodeId(trimmed)
        }
        val fallback = "${plugin.name.lowercase()}-${Bukkit.getPort()}"
        return BridgeNodeId(fallback)
    }
}
