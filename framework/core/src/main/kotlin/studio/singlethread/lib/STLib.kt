package studio.singlethread.lib

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import studio.singlethread.lib.command.StlibCommandInstaller
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
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bridge.BridgeRuntimeInfo
import studio.singlethread.lib.framework.bukkit.gui.openInventory
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolver
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolver
import studio.singlethread.lib.health.StlibHealthSnapshotAssembler
import studio.singlethread.lib.ui.StlibDashboardMenuItemFactory
import studio.singlethread.lib.ui.StlibDashboardNavigator
import studio.singlethread.lib.lifecycle.StlibLifecycleLogger
import studio.singlethread.lib.lifecycle.StlibRuntimeConfigController
import studio.singlethread.lib.lifecycle.StlibRuntimeSummaryLogger
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

class STLib : STPlugin() {
    private val runtimeSummaryLogger =
        StlibRuntimeSummaryLogger(
            allPlugins = ::allPlugins,
            resolveVersion = { BukkitServerVersionResolver.resolve(server) },
            resolveRuntime = { BukkitRuntimeResolver.resolve(server) },
            logInfo = logger::info,
        )
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
            createGui = { rows, title, placeholders, definition ->
                gui(
                    rows = rows,
                    title = title,
                    placeholders = placeholders,
                    definition = definition,
                )
            },
            openGui = { player, gui -> player.openInventory(gui) },
            dashboardService = dashboardService,
            menuItemFactory = menuItemFactory,
            translate = ::translated,
            formatInstant = ::formatInstant,
            notifyPlayer = { player, message -> notifier.sendPrefixed(player, message) },
        )
    }
    private val healthSnapshotProvider by lazy {
        StlibHealthSnapshotAssembler(
            dashboardService = dashboardService,
            runtimeState = dashboardRuntimeController::state,
            dashboardProfile = { runtimeConfig.dashboard.profile },
            commandMetricsEnabled = ::isCommandMetricsEnabled,
            schedulerEnabled = { capabilityRegistry.isEnabled(CapabilityNames.RUNTIME_SCHEDULER) },
            diDiscovered = { diComponentSummary()?.discovered ?: 0 },
            diValidated = { diComponentSummary()?.validated ?: 0 },
            bridgeMode = { kernel.service(BridgeRuntimeInfo::class)?.mode?.name?.lowercase() ?: "unknown" },
            bridgeDistributed = {
                kernel.service(BridgeRuntimeInfo::class)?.distributedEnabled
                    ?: capabilityRegistry.isEnabled(CapabilityNames.BRIDGE_DISTRIBUTED)
            },
            bridgeRedisConnected = {
                kernel.service(BridgeRuntimeInfo::class)?.redisConnected
                    ?: capabilityRegistry.isEnabled(CapabilityNames.BRIDGE_REDIS)
            },
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
    private val commandInstaller by lazy {
        StlibCommandInstaller(
            register = ::command,
            translate = { key -> translation.translate(key) },
            statusCommand = statusCommand,
            reloadCommand = reloadCommand,
            openGuiCommand = openGuiCommand,
            runtimeSnapshot = runtimeSnapshotAssembler::snapshot,
        )
    }
    private val runtimeConfigController by lazy {
        StlibRuntimeConfigController(
            registerRuntimeConfig = {
                registerConfig(
                    fileName = "stlib",
                    migrationPlan = StlibRuntimeConfigMigration.plan,
                )
            },
            reloadRuntimeConfig = {
                reloadConfig(
                    fileName = "stlib",
                    migrationPlan = StlibRuntimeConfigMigration.plan,
                )
            },
            reloadAllConfigs = ::reloadAllConfigs,
            saveRuntimeConfig = { value -> saveConfig(fileName = "stlib", value = value) },
            reloadTranslations = ::reloadTranslations,
            configureCommandMetrics = ::configureCommandMetrics,
            configPath = ::configPath,
            logInfo = logger::info,
            logWarning = logger::warning,
        )
    }
    private val displayZone = ZoneId.systemDefault()
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val runtimeSummaryLogged = AtomicBoolean(false)
    private val runtimeConfig: StlibRuntimeConfig
        get() = runtimeConfigController.current()

    override fun initialize() {
        lifecycleLogger.initialize()
        runtimeConfigController.initialize()
        dashboardRuntimeController.initialize()

    }
    override fun load() {
        lifecycleLogger.loadComplete()
    }
    override fun enable() {
        commandInstaller.install()
        registerRuntimeSummaryHook()
        dashboardRuntimeController.start()
        lifecycleLogger.enabled()
    }
    override fun disable() {
        dashboardRuntimeController.stop()
        runtimeConfigController.shutdown()
        lifecycleLogger.disabled()
    }

    private fun translated(key: String, placeholders: Map<String, String> = emptyMap()): String {
        return translation.translate(key = key, placeholders = placeholders)
    }

    private fun dashboardUnavailableMessage(): String {
        if (!runtimeConfig.dashboard.enabled) {
            return translated("stlib.gui.feedback.dashboard_disabled_config", emptyMap())
        }
        return translated("stlib.gui.feedback.dashboard_unavailable", emptyMap())
    }

    private fun flushIntervalTicks(): Long {
        return runtimeConfigController.flushIntervalTicks(ticksPerSecond = TICKS_PER_SECOND)
    }

    private fun reloadRuntime(): StlibReloadSnapshot {
        val runtimeReload = runtimeConfigController.reload()

        dashboardRuntimeController.stop()
        dashboardRuntimeController.initialize()
        dashboardRuntimeController.start()
        val healthSnapshot = healthSnapshotProvider.snapshot()
        val bridgeInfo = kernel.service(BridgeRuntimeInfo::class)
        val diSummary = diComponentSummary()

        return StlibReloadSnapshot(
            reloadedConfigCount = runtimeReload.reloadedConfigCount,
            dashboardAvailable = healthSnapshot.dashboardAvailable,
            dashboardProfile = healthSnapshot.dashboardProfile,
            persistenceEnabled = healthSnapshot.persistenceEnabled,
            persistenceActive = healthSnapshot.persistenceActive,
            commandMetricsEnabled = healthSnapshot.commandMetricsEnabled,
            schedulerEnabled = capabilityRegistry.isEnabled(CapabilityNames.RUNTIME_SCHEDULER),
            diDiscovered = diSummary?.discovered ?: 0,
            diValidated = diSummary?.validated ?: 0,
            bridgeMode = bridgeInfo?.mode?.name?.lowercase() ?: "unknown",
            bridgeDistributed =
                bridgeInfo?.distributedEnabled ?: capabilityRegistry.isEnabled(CapabilityNames.BRIDGE_DISTRIBUTED),
            bridgeNodeId = bridgeInfo?.nodeId?.value ?: bridgeNodeId().value,
        )
    }

    private fun registerRuntimeSummaryHook() {
        val listener =
            object : Listener {
                @EventHandler
                fun onServerLoad(event: ServerLoadEvent) {
                    if (event.type != ServerLoadEvent.LoadType.STARTUP) {
                        return
                    }
                    logRuntimeSummary()
                    unlisten(this)
                }
            }
        listen(listener)
        // Fallback for late-enable cases where STARTUP event already passed.
        later(1L, Runnable {
            logRuntimeSummary()
            unlisten(listener)
        })
    }

    private fun logRuntimeSummary() {
        if (!runtimeSummaryLogged.compareAndSet(false, true)) {
            return
        }
        runtimeSummaryLogger.print()
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
    }
}
