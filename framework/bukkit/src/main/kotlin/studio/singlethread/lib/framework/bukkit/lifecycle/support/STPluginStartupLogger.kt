package studio.singlethread.lib.framework.bukkit.lifecycle.support

import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.framework.bukkit.bootstrap.BootstrapDiagnostics
import java.util.logging.Logger

internal class STPluginStartupLogger(
    private val logger: Logger,
    private val pluginName: String,
    private val debugEnabled: () -> Boolean,
) {
    fun logBootstrapReady(diagnostics: BootstrapDiagnostics) {
        logger.info(
            "Environment ready: bridge=${diagnostics.bridgeMode}, node=${diagnostics.bridgeNodeId}, " +
                "namespace=${diagnostics.bridgeNamespace}",
        )

        val dependencySummary = DependencySummary.from(diagnostics.dependencyResults)
        val dependencyLine =
            buildString {
                append("Dependencies ready: loaded=${dependencySummary.loadedCount}, reused=${dependencySummary.reusedCount}")
                if (dependencySummary.warningCount > 0) {
                    append(", warnings=${dependencySummary.warningCount}")
                }
            }
        logger.info(dependencyLine)

        if (dependencySummary.versionMismatches.isNotEmpty()) {
            logger.info("Version mismatch detected:")
            dependencySummary.versionMismatches.forEach { mismatch ->
                logger.info(
                    "- ${mismatch.artifact} requested=${mismatch.requestedVersion}, using=${mismatch.actualVersion}",
                )
            }
        }

        logger.info("Components ready: ${diagnostics.components.joinToString(", ")}")

        if (debugEnabled()) {
            logDebug(diagnostics)
        }
    }

    fun logEnabledSuccessfully() {
        logger.info("$pluginName enabled successfully")
    }

    private fun logDebug(diagnostics: BootstrapDiagnostics) {
        diagnostics.stepDurationsMillis.forEach { (stepName, durationMillis) ->
            logger.info("[debug] bootstrap.$stepName=${durationMillis}ms")
        }

        diagnostics.dependencyResults.forEach { result ->
            when (result.status) {
                DependencyStatus.PRESENT -> {
                    val actualVersion = result.detectedVersion?.takeIf { it.isNotBlank() } ?: "unknown"
                    logger.info(
                        "[debug] reused dependency: ${result.library.artifactId} $actualVersion from classpath",
                    )
                }

                DependencyStatus.LOADED -> {
                    val actualVersion =
                        result.detectedVersion?.takeIf { it.isNotBlank() }
                            ?: result.library.version
                    val elapsedMillis = result.elapsedMillis ?: 0L
                    logger.info(
                        "[debug] loaded dependency: ${result.library.artifactId} $actualVersion in ${elapsedMillis}ms",
                    )
                }

                else -> Unit
            }
        }
    }

    private data class VersionMismatch(
        val artifact: String,
        val requestedVersion: String,
        val actualVersion: String,
    )

    private data class DependencySummary(
        val loadedCount: Int,
        val reusedCount: Int,
        val warningCount: Int,
        val versionMismatches: List<VersionMismatch>,
    ) {
        companion object {
            fun from(results: List<DependencyLoadResult>): DependencySummary {
                val loadedCount = results.count { it.status == DependencyStatus.LOADED }
                val reusedCount = results.count { it.status == DependencyStatus.PRESENT }
                val failedCount = results.count { it.status == DependencyStatus.FAILED }
                val mismatches = collectVersionMismatches(results)
                return DependencySummary(
                    loadedCount = loadedCount,
                    reusedCount = reusedCount,
                    warningCount = failedCount + mismatches.size,
                    versionMismatches = mismatches,
                )
            }

            private fun collectVersionMismatches(results: List<DependencyLoadResult>): List<VersionMismatch> {
                return results.asSequence()
                    .filter { it.status == DependencyStatus.PRESENT }
                    .mapNotNull { result ->
                        val requestedVersion = result.library.version.trim()
                        val actualVersion = result.detectedVersion?.trim().orEmpty()
                        if (actualVersion.isBlank()) {
                            return@mapNotNull null
                        }
                        if (requestedVersion.equals(actualVersion, ignoreCase = true)) {
                            return@mapNotNull null
                        }
                        VersionMismatch(
                            artifact = result.library.artifactId,
                            requestedVersion = requestedVersion,
                            actualVersion = actualVersion,
                        )
                    }.toList()
            }
        }
    }
}
