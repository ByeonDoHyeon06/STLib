package studio.singlethread.lib.framework.bukkit.bootstrap

import studio.singlethread.lib.dependency.common.model.DependencyLoadResult

data class BootstrapDiagnostics(
    val stepDurationsMillis: Map<String, Long>,
    val dependencyResults: List<DependencyLoadResult>,
    val bridgeMode: String,
    val bridgeNodeId: String,
    val bridgeNamespace: String,
    val components: List<String>,
)
