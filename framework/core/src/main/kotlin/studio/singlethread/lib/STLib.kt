package studio.singlethread.lib

import org.bukkit.entity.Player
import studio.singlethread.lib.command.StlibOpenGuiCommand
import studio.singlethread.lib.command.StlibReloadCommand
import studio.singlethread.lib.command.StlibReloadSnapshot
import studio.singlethread.lib.command.StlibRuntimeSnapshotAssembler
import studio.singlethread.lib.command.StlibStatusCommand
import studio.singlethread.lib.config.StlibRuntimeConfig
import studio.singlethread.lib.config.StlibRuntimeConfigMigration
import studio.singlethread.lib.dashboard.StlibDashboardRuntimeAdapter
import studio.singlethread.lib.dashboard.StlibDashboardRuntimeController
import studio.singlethread.lib.dashboard.StlibDashboardService
import studio.singlethread.lib.dashboard.StlibStorageCapabilityPolicy
import studio.singlethread.lib.dashboard.StlibStatsStore
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin
import studio.singlethread.lib.health.StlibHealthSnapshotAssembler
import studio.singlethread.lib.ui.StlibDashboardMenuItemFactory
import studio.singlethread.lib.ui.StlibDashboardNavigator
import studio.singlethread.lib.lifecycle.StlibLifecycleLogger
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class STLib : STPlugin() {
    private val lifecycleLogger = StlibLifecycleLogger(::translated, logger::info)
    private val statusCommand = StlibStatusCommand(translate = ::translated, logInfo = logger::info)
    private val reloadCommand =
        StlibReloadCommand(
            translate = ::translated,
            logInfo = logger::info,
            logWarning = logger::warning,
            reload = ::reloadRuntime,
        )
    private val openGuiCommand =
        StlibOpenGuiCommand(
            translate = ::translated,
            logInfo = logger::info,
            logWarning = logger::warning,
            isDashboardAvailable = { dashboardRuntimeController.isAvailable() },
            dashboardUnavailableMessage = ::dashboardUnavailableMessage,
            openGui = { player -> dashboardNavigator.openList(player) },
        )
    private val statsStore by lazy {
        StlibStatsStore(
            collection = storage.collection(DASHBOARD_STATS_COLLECTION),
            logWarning = logger::warning,
        )
    }
    private val dashboardService by lazy {
        StlibDashboardService(
            plugins = ::allPlugins,
            statsStore = statsStore,
            now = { Instant.now() },
        )
    }
    private val menuItemFactory by lazy {
        StlibDashboardMenuItemFactory(
            parse = ::mini,
            translate = ::translated,
        )
    }
    private val runtimeSnapshotAssembler by lazy {
        StlibRuntimeSnapshotAssembler(
            storageBackend = { pluginConfig.storage.backend.name.lowercase() },
            plugins = ::allPlugins,
        )
    }
    private val dashboardNavigator by lazy {
        StlibDashboardNavigator(
            ui = ui,
            dashboardService = dashboardService,
            menuItemFactory = menuItemFactory,
            translate = ::translated,
            formatInstant = ::formatInstant,
            notifyPlayer = { player, message -> announce(player, message) },
        )
    }
    private val healthSnapshotProvider by lazy {
        StlibHealthSnapshotAssembler(
            dashboardService = dashboardService,
            runtimeState = dashboardRuntimeController::state,
            dashboardProfile = { runtimeConfig.dashboard.profile },
            commandMetricsEnabled = ::isCommandMetricsEnabled,
        )
    }
    private val dashboardRuntimeController by lazy {
        StlibDashboardRuntimeController(
            dashboardService = StlibDashboardRuntimeAdapter(dashboardService),
            capabilityPolicy = StlibStorageCapabilityPolicy(capabilityRegistry::isEnabled),
            schedulePeriodicFlush = { delayTicks, periodTicks, task -> timer(delayTicks, periodTicks, task) },
            async = { task -> async(task) },
            logWarning = logger::warning,
            isDashboardEnabled = { runtimeConfig.dashboard.enabled },
            isPersistenceEnabled = { runtimeConfig.dashboard.persistStats },
            flushPeriodTicks = ::flushIntervalTicks,
        )
    }
    private val displayZone = ZoneId.systemDefault()
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private lateinit var runtimeConfig: StlibRuntimeConfig

    override fun initialize() {
        lifecycleLogger.initialize()
        runtimeConfig =
            registerConfig(
                fileName = "stlib",
                migrationPlan = StlibRuntimeConfigMigration.plan,
            )
        normalizeRuntimeConfig()
        applyRuntimeSwitches()
        cleanupLegacyStatsFile()
        dashboardRuntimeController.initialize()

    }

    override fun load() {
        lifecycleLogger.loadComplete()
    }

    override fun enable() {
        command("stlib") {
            permission = "stlib.command"
            description = translation.translate("command.stlib.description")
            literal("reload") {
                permission = "stlib.command.reload"
                executes { context ->
                    reloadCommand.execute(context)
                }
            }
            executes { context ->
                statusCommand.execute(context, runtimeSnapshotAssembler.snapshot())
            }
        }
        command("stlibgui") {
            permission = "stlib.command.gui"
            description = translation.translate("command.stlibgui.description")
            executes { context ->
                openGuiCommand.execute(
                    context = context,
                    player = context.audience as? Player,
                )
            }
        }

        dashboardRuntimeController.start()
        lifecycleLogger.enabled()
    }

    override fun disable() {
        dashboardRuntimeController.stop()
        configureCommandMetrics(false)
        lifecycleLogger.disabled()
    }

    private fun translated(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): String {
        return translation.translate(key = key, placeholders = placeholders)
    }

    private fun dashboardUnavailableMessage(): String {
        if (!runtimeConfig.dashboard.enabled) {
            return translated("stlib.gui.feedback.dashboard_disabled_config", emptyMap())
        }
        return translated("stlib.gui.feedback.dashboard_unavailable", emptyMap())
    }

    private fun normalizeRuntimeConfig() {
        val dashboard = runtimeConfig.dashboard
        var changed = false

        val normalizedProfile = normalizeDashboardProfile(dashboard.profile)
        if (dashboard.profile != normalizedProfile) {
            dashboard.profile = normalizedProfile
            changed = true
        }

        val normalizedInterval = dashboard.flushIntervalSeconds.coerceIn(minimumValue = 5, maximumValue = 3_600)
        if (dashboard.flushIntervalSeconds != normalizedInterval) {
            dashboard.flushIntervalSeconds = normalizedInterval
            changed = true
        }

        if (!changed) {
            return
        }
        saveConfig(fileName = "stlib", value = runtimeConfig)
    }

    private fun normalizeDashboardProfile(rawProfile: String): String {
        return when (rawProfile.trim().lowercase()) {
            DASHBOARD_PROFILE_CORE_OPS -> DASHBOARD_PROFILE_CORE_OPS
            else -> DASHBOARD_PROFILE_CORE_OPS
        }
    }

    private fun applyRuntimeSwitches() {
        configureCommandMetrics(runtimeConfig.metrics.command.enabled)
    }

    private fun flushIntervalTicks(): Long {
        return runtimeConfig.dashboard.flushIntervalSeconds.toLong() * TICKS_PER_SECOND
    }

    private fun reloadRuntime(): StlibReloadSnapshot {
        val reloaded = reloadAllConfigs()
        runtimeConfig =
            reloadConfig(
                fileName = "stlib",
                migrationPlan = StlibRuntimeConfigMigration.plan,
            )
        normalizeRuntimeConfig()
        applyRuntimeSwitches()
        reloadTranslations()

        dashboardRuntimeController.stop()
        cleanupLegacyStatsFile()
        dashboardRuntimeController.initialize()
        dashboardRuntimeController.start()
        val healthSnapshot = healthSnapshotProvider.snapshot()

        return StlibReloadSnapshot(
            reloadedConfigCount = reloaded.size,
            dashboardAvailable = healthSnapshot.dashboardAvailable,
            dashboardProfile = healthSnapshot.dashboardProfile,
            persistenceEnabled = healthSnapshot.persistenceEnabled,
            persistenceActive = healthSnapshot.persistenceActive,
            commandMetricsEnabled = healthSnapshot.commandMetricsEnabled,
        )
    }

    private fun cleanupLegacyStatsFile() {
        val legacyPath = configPath("stplugin-stats")
        if (!Files.exists(legacyPath)) {
            return
        }

        runCatching {
            Files.deleteIfExists(legacyPath)
        }.onSuccess {
            logger.info("Removed legacy dashboard stats file ${legacyPath.fileName}; stats are now stored in Storage backend")
        }.onFailure { error ->
            logger.warning("Legacy stats file exists but could not be removed (${legacyPath.fileName}): ${error.message}")
        }
    }

    private fun formatInstant(value: Instant?): String {
        if (value == null) {
            return translated("stlib.gui.value.none")
        }
        return displayTimeFormatter.format(value.atZone(displayZone))
    }

    private companion object {
        private const val TICKS_PER_SECOND = 20L
        private const val DASHBOARD_STATS_COLLECTION = "stlib_dashboard_stats"
        private const val DASHBOARD_PROFILE_CORE_OPS = "core_ops"
    }
}
