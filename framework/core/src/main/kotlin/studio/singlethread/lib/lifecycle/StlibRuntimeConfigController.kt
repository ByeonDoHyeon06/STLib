package studio.singlethread.lib.lifecycle

import studio.singlethread.lib.config.StlibRuntimeConfig
import java.nio.file.Files
import java.nio.file.Path

data class StlibRuntimeReloadResult(
    val reloadedConfigCount: Int,
)

class StlibRuntimeConfigController(
    private val registerRuntimeConfig: () -> StlibRuntimeConfig,
    private val reloadRuntimeConfig: () -> StlibRuntimeConfig,
    private val reloadAllConfigs: () -> Map<String, Any>,
    private val saveRuntimeConfig: (StlibRuntimeConfig) -> Unit,
    private val reloadTranslations: () -> Unit,
    private val configureCommandMetrics: (Boolean) -> Unit,
    private val configPath: (String) -> Path,
    private val logInfo: (String) -> Unit,
    private val logWarning: (String) -> Unit,
) {
    private var runtimeConfig: StlibRuntimeConfig = StlibRuntimeConfig()

    fun initialize(): StlibRuntimeConfig {
        runtimeConfig = registerRuntimeConfig()
        runtimeConfig = normalize(runtimeConfig)
        applyRuntimeSwitches(runtimeConfig)
        cleanupLegacyStatsFile()
        return runtimeConfig
    }

    fun reload(): StlibRuntimeReloadResult {
        val reloadedConfigCount = reloadAllConfigs().size
        runtimeConfig = reloadRuntimeConfig()
        runtimeConfig = normalize(runtimeConfig)
        applyRuntimeSwitches(runtimeConfig)
        reloadTranslations()
        cleanupLegacyStatsFile()
        return StlibRuntimeReloadResult(
            reloadedConfigCount = reloadedConfigCount,
        )
    }

    fun shutdown() {
        configureCommandMetrics(false)
    }

    fun current(): StlibRuntimeConfig {
        return runtimeConfig
    }

    fun flushIntervalTicks(ticksPerSecond: Long): Long {
        return runtimeConfig.dashboard.flushIntervalSeconds.toLong() * ticksPerSecond
    }

    private fun applyRuntimeSwitches(config: StlibRuntimeConfig) {
        configureCommandMetrics(config.metrics.command.enabled)
    }

    private fun normalize(config: StlibRuntimeConfig): StlibRuntimeConfig {
        val dashboard = config.dashboard
        var changed = false

        val normalizedProfile =
            when (dashboard.profile.trim().lowercase()) {
                DASHBOARD_PROFILE_CORE_OPS -> DASHBOARD_PROFILE_CORE_OPS
                else -> DASHBOARD_PROFILE_CORE_OPS
            }
        if (dashboard.profile != normalizedProfile) {
            dashboard.profile = normalizedProfile
            changed = true
        }

        val normalizedInterval =
            dashboard.flushIntervalSeconds.coerceIn(
                minimumValue = DASHBOARD_FLUSH_INTERVAL_MIN,
                maximumValue = DASHBOARD_FLUSH_INTERVAL_MAX,
            )
        if (dashboard.flushIntervalSeconds != normalizedInterval) {
            dashboard.flushIntervalSeconds = normalizedInterval
            changed = true
        }

        if (changed) {
            saveRuntimeConfig(config)
        }
        return config
    }

    private fun cleanupLegacyStatsFile() {
        val legacyPath = configPath(LEGACY_DASHBOARD_STATS_FILE)
        if (!Files.exists(legacyPath)) {
            return
        }

        runCatching {
            Files.deleteIfExists(legacyPath)
        }.onSuccess {
            logInfo("Removed legacy dashboard stats file ${legacyPath.fileName}; stats are now stored in Storage backend")
        }.onFailure { error ->
            logWarning("Legacy stats file exists but could not be removed (${legacyPath.fileName}): ${error.message}")
        }
    }

    private companion object {
        private const val LEGACY_DASHBOARD_STATS_FILE = "stplugin-stats"
        private const val DASHBOARD_PROFILE_CORE_OPS = "core_ops"
        private const val DASHBOARD_FLUSH_INTERVAL_MIN = 5
        private const val DASHBOARD_FLUSH_INTERVAL_MAX = 3_600
    }
}
