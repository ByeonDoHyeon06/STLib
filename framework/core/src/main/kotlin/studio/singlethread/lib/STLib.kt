package studio.singlethread.lib

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerLoadEvent
import org.bukkit.entity.Player
import studio.singlethread.lib.command.STLibRootCommand
import studio.singlethread.lib.config.STLibRuntimeConfig
import studio.singlethread.lib.config.STLibRuntimeConfigMigration
import studio.singlethread.lib.dashboard.STLibDashboardRuntimeAdapter
import studio.singlethread.lib.dashboard.STLibDashboardRuntimeController
import studio.singlethread.lib.dashboard.STLibDashboardService
import studio.singlethread.lib.dashboard.STLibStorageCapabilityPolicy
import studio.singlethread.lib.dashboard.STLibStatsStore
import studio.singlethread.lib.operations.STLibCommandRuntime
import studio.singlethread.lib.operations.STLibDoctorExecutor
import studio.singlethread.lib.operations.STLibOpenGuiExecutor
import studio.singlethread.lib.operations.STLibReloadExecutor
import studio.singlethread.lib.operations.STLibReloadSnapshot
import studio.singlethread.lib.operations.STLibStatusExecutor
import studio.singlethread.lib.operations.STLibStatusSnapshotAssembler
import studio.singlethread.lib.framework.api.config.register
import studio.singlethread.lib.framework.api.config.reload
import studio.singlethread.lib.framework.api.config.save
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bridge.BridgeRuntimeInfo
import studio.singlethread.lib.framework.bukkit.bridge.metricsSnapshot
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin
import studio.singlethread.lib.framework.bukkit.management.STPlugins
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolver
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolver
import studio.singlethread.lib.health.STLibHealthSnapshotAssembler
import studio.singlethread.lib.ui.STLibDashboardMenuItemFactory
import studio.singlethread.lib.ui.STLibDashboardNavigator
import studio.singlethread.lib.lifecycle.STLibRuntimeConfigController
import studio.singlethread.lib.lifecycle.STLibRuntimeSummaryLogger
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.nio.file.Path
import java.nio.file.Paths

class STLib : STPlugin(), STLibCommandRuntime {
    private val runtimeSummaryLogger =
        STLibRuntimeSummaryLogger(
            allPlugins = STPlugins::all,
            resolveVersion = { BukkitServerVersionResolver.resolve(server) },
            resolveRuntime = { BukkitRuntimeResolver.resolve(server) },
            logInfo = logger::info,
        )
    private val statusExecutor = STLibStatusExecutor(translate = ::translated, logInfo = logger::info)
    private val reloadExecutor =
        STLibReloadExecutor(
            translate = ::translated,
            logInfo = logger::info,
            logWarning = logger::warning,
            reload = ::reloadRuntime,
        )
    private val openGuiExecutor =
        STLibOpenGuiExecutor(
            translate = ::translated,
            logInfo = logger::info,
            logWarning = logger::warning,
            isDashboardAvailable = { dashboardRuntimeController.isAvailable() },
            dashboardUnavailableMessage = ::dashboardUnavailableMessage,
            openGui = { player -> dashboardNavigator.openList(player) },
        )
    private val doctorExecutor =
        STLibDoctorExecutor(
            translate = ::translated,
            logInfo = logger::info,
        )
    private val statsStore by lazy {
        STLibStatsStore(
            collectionProvider = { storage.collection(DASHBOARD_STATS_COLLECTION) },
            logWarning = logger::warning,
        )
    }
    private val dashboardService by lazy {
        STLibDashboardService(
            plugins = STPlugins::all,
            statsStore = statsStore,
            now = { Instant.now() },
        )
    }
    private val menuItemFactory by lazy {
        STLibDashboardMenuItemFactory(
            parse = { message, placeholders -> text.parse(message, placeholders) },
            translate = ::translated,
        )
    }
    private val statusSnapshotAssembler by lazy {
        STLibStatusSnapshotAssembler(
            storageBackend = { pluginConfig.storage.backend.name.lowercase() },
            plugins = STPlugins::all,
        )
    }
    private val dashboardNavigator by lazy {
        STLibDashboardNavigator(
            createGui = { size, title, placeholders, definition ->
                gui.create(
                    title = text.parse(title, placeholders),
                    size = size,
                    definition = definition,
                )
            },
            openGui = { player, gui -> this.gui.open(player, gui) },
            dashboardService = dashboardService,
            menuItemFactory = menuItemFactory,
            translate = ::translated,
            formatInstant = ::formatInstant,
            notifyPlayer = { player, message -> notifier.sendPrefixed(player, message) },
        )
    }
    private val healthSnapshotProvider by lazy {
        STLibHealthSnapshotAssembler(
            dashboardService = dashboardService,
            runtimeState = dashboardRuntimeController::state,
            dashboardProfile = { runtimeConfig.dashboard.profile },
            commandMetricsEnabled = STPlugins::isCommandMetricsEnabled,
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
            bridgeMetrics = {
                kernel.service(BridgeRuntimeInfo::class)?.metricsSnapshot()
                    ?: studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot.EMPTY
            },
        )
    }
    private val dashboardRuntimeController by lazy {
        STLibDashboardRuntimeController(
            dashboardService = STLibDashboardRuntimeAdapter(dashboardService),
            capabilityPolicy = STLibStorageCapabilityPolicy(capabilityRegistry::isEnabled),
            schedulePeriodicFlush = { delayTicks, periodTicks, task -> scheduler.runTimer(delayTicks, periodTicks, task) },
            async = { task -> scheduler.runAsync(task) },
            logWarning = logger::warning,
            isDashboardEnabled = { runtimeConfig.dashboard.enabled },
            isPersistenceEnabled = { runtimeConfig.dashboard.persistStats },
            flushPeriodTicks = ::flushIntervalTicks,
        )
    }
    private val runtimeConfigController by lazy {
        STLibRuntimeConfigController(
            registerRuntimeConfig = {
                configRegistry.register("stlib", STLibRuntimeConfigMigration.plan)
            },
            reloadRuntimeConfig = {
                configRegistry.reload("stlib", STLibRuntimeConfigMigration.plan)
            },
            reloadAllConfigs = configRegistry::reloadAll,
            saveRuntimeConfig = { value -> configRegistry.save("stlib", value) },
            reloadTranslations = translation::reload,
            configureCommandMetrics = STPlugins::configureCommandMetrics,
            configPath = ::runtimeConfigPath,
            logInfo = logger::info,
            logWarning = logger::warning,
        )
    }
    private val displayZone = ZoneId.systemDefault()
    private val displayTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val runtimeSummaryLogged = AtomicBoolean(false)
    private val runtimeConfig: STLibRuntimeConfig
        get() = runtimeConfigController.current()

    override fun initialize() {
        runtimeConfigController.initialize()
        dashboardRuntimeController.initialize()

    }
    override fun load() {
    }
    override fun enable() {
        command<STLibRootCommand>()
        registerRuntimeSummaryHook()
        dashboardRuntimeController.start()
    }
    override fun disable() {
        dashboardRuntimeController.stop()
        runtimeConfigController.shutdown()
    }

    private fun translated(key: String, placeholders: Map<String, String> = emptyMap()): String {
        return translation.translate(key = key, placeholders = placeholders)
    }

    override fun commandDescription(key: String): String {
        return translation.translate(key)
    }

    override fun executeStatus(context: CommandContext) {
        statusExecutor.execute(context, statusSnapshotAssembler.snapshot())
    }

    override fun executeReload(context: CommandContext) {
        reloadExecutor.execute(context)
    }

    override fun executeDoctor(context: CommandContext) {
        doctorExecutor.execute(context, healthSnapshotProvider.snapshot())
    }

    override fun executeOpenGui(
        context: CommandContext,
        player: Player?,
    ) {
        openGuiExecutor.execute(context, player)
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

    private fun runtimeConfigPath(fileName: String): Path {
        val normalizedName = fileName.trim()
        require(normalizedName.isNotBlank()) { "fileName must not be blank" }
        val withExtension =
            if (normalizedName.endsWith(".yml", ignoreCase = true)) {
                normalizedName
            } else {
                "$normalizedName.yml"
            }
        val relative = Paths.get(withExtension).normalize()
        require(!relative.isAbsolute) { "fileName must be relative path" }
        require(!relative.startsWith("..")) { "fileName must not escape config directory" }
        return dataFolder.toPath().resolve("config").resolve(relative).normalize()
    }

    private fun reloadRuntime(): STLibReloadSnapshot {
        val runtimeReload = runtimeConfigController.reload()

        dashboardRuntimeController.stop()
        dashboardRuntimeController.initialize()
        dashboardRuntimeController.start()
        val healthSnapshot = healthSnapshotProvider.snapshot()
        val bridgeInfo = kernel.service(BridgeRuntimeInfo::class)
        val diSummary = diComponentSummary()

        return STLibReloadSnapshot(
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
            bridgeNodeId = bridgeInfo?.nodeId?.value ?: bridge.nodeId().value,
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
