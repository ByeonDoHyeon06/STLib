package studio.singlethread.lib.framework.bukkit.lifecycle

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryType
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgePayloadListener
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandRegistrar
import studio.singlethread.lib.framework.api.command.STCommand
import studio.singlethread.lib.framework.api.command.CommandTree
import studio.singlethread.lib.framework.api.command.commandDsl
import studio.singlethread.lib.framework.api.config.ConfigMigrationPlan
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.config.VersionedConfig
import studio.singlethread.lib.framework.api.di.ComponentScanSummary
import studio.singlethread.lib.framework.api.event.EventRegistrar
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.lifecycle.STLifecycle
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.api.scheduler.ChainedScheduledTask
import studio.singlethread.lib.framework.api.scheduler.DelaySchedule
import studio.singlethread.lib.framework.api.scheduler.RepeatSchedule
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.api.kernel.requireService
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bootstrap.BukkitKernelBootstrapper
import studio.singlethread.lib.framework.bukkit.bridge.BridgeRuntimeInfo
import studio.singlethread.lib.framework.bukkit.command.BukkitCommandRegistrar
import studio.singlethread.lib.framework.bukkit.command.CommandApiLifecycle
import studio.singlethread.lib.framework.bukkit.config.BukkitConfigRegistry
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.di.ReflectiveComponentResolver
import studio.singlethread.lib.framework.bukkit.event.BukkitEventCaller
import studio.singlethread.lib.framework.bukkit.event.BukkitEventRegistrar
import studio.singlethread.lib.framework.bukkit.event.STEvent
import studio.singlethread.lib.framework.bukkit.event.STListener
import studio.singlethread.lib.framework.bukkit.gui.BukkitGuiService
import studio.singlethread.lib.framework.bukkit.gui.StGui
import studio.singlethread.lib.framework.bukkit.gui.StGuiDefinition
import studio.singlethread.lib.framework.bukkit.gui.StGuiService
import studio.singlethread.lib.framework.bukkit.management.STPluginDescriptor
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPlugins
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.bukkit.support.PluginConventions
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.translation.BukkitTranslationFacade
import studio.singlethread.lib.framework.bukkit.lifecycle.support.CachedServicePropertyDelegate
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginCompatibilityVerifier
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginLoadRuntimeCoordinator
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolver
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolver
import studio.singlethread.lib.framework.bukkit.version.SupportedBukkitRuntimes
import studio.singlethread.lib.framework.bukkit.version.SupportedServerVersions
import studio.singlethread.lib.framework.bukkit.version.UnsupportedServerVersionAction
import studio.singlethread.lib.framework.core.kernel.DefaultSTKernel
import studio.singlethread.lib.framework.core.text.MiniMessageTextService
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.registry.common.provider.ResourceProvider
import studio.singlethread.lib.registry.common.service.ResourceService
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageApi
import java.nio.file.Path
import java.time.Instant
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
abstract class STPlugin(
    private val version: String? = null,
) : JavaPlugin(), STLifecycle {
    final override val kernel: STKernel = DefaultSTKernel()

    private var commandApiLoaded = false
    private var kernelBootstrapped = false
    private var compatibilitySupported = true
    @Volatile
    private var debugLoggingEnabled = false
    private var guiBootstrap: BukkitGuiService? = null
    private var diScanSummary: ComponentScanSummary? = null
    private val bridgeSubscriptions = CopyOnWriteArrayList<BridgeSubscription>()
    private val componentResolver: ReflectiveComponentResolver by lazy(LazyThreadSafetyMode.NONE) {
        ReflectiveComponentResolver(owner = this, kernel = kernel)
    }

    protected val capabilityRegistry: CapabilityRegistry
        get() = kernel.capabilityRegistry

    protected val text: TextService by required { kernel.requireService() }

    protected val translation: TranslationService by required { kernel.requireService() }

    protected val notifier: NotifierService by required { kernel.requireService() }

    protected val scheduler: SchedulerService by required { kernel.requireService() }

    protected val commandRegistrar: CommandRegistrar by required { kernel.requireService() }

    protected val eventRegistrar: EventRegistrar<Listener> by required { kernel.requireService() }

    protected val configService: ConfigService by required { kernel.requireService() }

    protected val configRegistry: ConfigRegistry by required { kernel.requireService() }

    protected val storageApi: StorageApi by required { kernel.requireService() }

    protected val storage: Storage by required { kernel.requireService() }

    protected val bridge: BridgeService by required { kernel.requireService() }

    protected val guiService: StGuiService by required { kernel.requireService() }

    protected val resource: ResourceService by required { kernel.requireService() }

    protected val pluginConfig: PluginFileConfig by required { kernel.requireService() }

    private val bukkitTextParser: BukkitTextParser by required { kernel.requireService() }

    /**
     * Default compatibility policy follows STLib baseline support window.
     *
     * Plugins can override to narrow/expand support range:
     * - `SupportedServerVersions.range("1.20.1", "1.21.1")`
     * - `SupportedServerVersions.exact("1.20.4", "1.21.1")`
     * - `SupportedServerVersions.any()`
     */
    // supported
    protected open fun minecraftVersions(): SupportedServerVersions {
        return SupportedServerVersions.range(
            minInclusive = "1.19.4",
            maxInclusive = "1.21.99",
        )
    }

    /**
     * Short alias for mismatch behavior when minecraft version is unsupported.
     *
     * Preferred in new plugins.
     */
    // unsupported
    protected open fun minecraftMismatchAction(): UnsupportedServerVersionAction {
        return UnsupportedServerVersionAction.DISABLE_PLUGIN
    }

    /**
     * Optional Bukkit runtime policy gate.
     *
     * Default is unrestricted.
     * Example:
     * - `SupportedBukkitRuntimes.only(BukkitRuntime.PAPER, BukkitRuntime.FOLIA)`
     */
    protected open fun supportedRuntimes(): SupportedBukkitRuntimes {
        return SupportedBukkitRuntimes.any()
    }

    /**
     * Action used when runtime does not match [supportedRuntimes].
     */
    protected open fun runtimeMismatchAction(): UnsupportedServerVersionAction {
        return minecraftMismatchAction()
    }

    protected open fun pluginVersion(): String {
        return version
            ?.trim()
            .orEmpty()
            .ifBlank { description.version.trim() }
            .ifBlank { "unknown" }
    }

    protected fun mini(message: String, placeholders: Map<String, String> = emptyMap()): Component {
        return bukkitTextParser.parse(message, placeholders)
    }

    protected fun mini(
        sender: CommandSender,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
        usePlaceholderApi: Boolean = true,
    ): Component {
        return bukkitTextParser.parse(sender, message, placeholders, usePlaceholderApi)
    }

    protected fun gui(
        rows: Int,
        title: String,
        placeholders: Map<String, String> = emptyMap(),
        definition: StGuiDefinition,
    ): StGui {
        return guiService.create(
            rows = rows,
            title = mini(title, placeholders),
            definition = definition,
        )
    }

    protected fun gui(
        rows: Int,
        title: Component,
        definition: StGuiDefinition,
    ): StGui {
        return guiService.create(
            rows = rows,
            title = title,
            definition = definition,
        )
    }

    protected fun gui(
        definition: StGuiDefinition,
    ): StGui {
        return gui(
            rows = 6,
            title = Component.empty(),
            definition = definition,
        )
    }

    protected fun gui(
        title: String,
        size: Int,
        type: InventoryType,
        placeholders: Map<String, String> = emptyMap(),
        definition: StGuiDefinition,
    ): StGui {
        return guiService.create(
            size = size,
            title = mini(title, placeholders),
            type = type,
            definition = definition,
        )
    }

    protected fun gui(
        title: Component,
        size: Int,
        type: InventoryType,
        definition: StGuiDefinition,
    ): StGui {
        return guiService.create(
            size = size,
            title = title,
            type = type,
            definition = definition,
        )
    }

    protected fun translate(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): Component {
        return translationFacade().translate(key, placeholders)
    }

    protected fun translate(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): Component {
        return translationFacade().translate(sender, key, placeholders)
    }

    protected fun sendTranslated(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        translationFacade().sendTranslated(sender, key, placeholders)
    }

    protected fun reloadTranslations() {
        translationFacade().reloadTranslations()
    }

    protected fun broadcast(
        message: String,
        placeholders: Map<String, String> = emptyMap(),
        prefixed: Boolean = false,
    ) {
        val component = if (prefixed) notifier.prefixed(message, placeholders) else notifier.message(message, placeholders)
        server.onlinePlayers.forEach { player -> player.sendMessage(component) }
        server.consoleSender.sendMessage(component)
    }

    protected fun broadcastTranslated(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        prefixed: Boolean = false,
    ) {
        val translated = translation.translate(key = key, placeholders = placeholders)
        broadcast(message = translated, prefixed = prefixed)
    }

    protected fun console(
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        server.consoleSender.sendMessage(mini(message, placeholders))
    }

    protected fun send(
        sender: CommandSender,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        sender.sendMessage(mini(sender, message, placeholders))
    }

    protected fun sync(task: Runnable): ScheduledTask {
        return scheduler.runSync(task)
    }

    protected fun async(task: Runnable): ScheduledTask {
        return scheduler.runAsync(task)
    }

    protected fun later(
        delayTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        return scheduler.runLater(delayTicks, task)
    }

    protected fun timer(
        delayTicks: Long,
        periodTicks: Long,
        task: Runnable,
    ): ScheduledTask {
        return scheduler.runTimer(delayTicks, periodTicks, task)
    }

    protected fun later(
        delay: Long,
        unit: TimeUnit,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runDelayed(DelaySchedule.sync(delay, unit), task)
    }

    protected fun later(
        delay: Duration,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runDelayed(DelaySchedule.sync(delay), task)
    }

    protected fun asyncLater(
        delay: Long,
        unit: TimeUnit,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runDelayed(DelaySchedule.async(delay, unit), task)
    }

    protected fun asyncLater(
        delay: Duration,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runDelayed(DelaySchedule.async(delay), task)
    }

    protected fun timer(
        delay: Long,
        period: Long,
        unit: TimeUnit,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runRepeating(RepeatSchedule.sync(delay, period, unit), task)
    }

    protected fun timer(
        delay: Duration,
        period: Duration,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runRepeating(RepeatSchedule.sync(delay, period), task)
    }

    protected fun asyncTimer(
        delay: Long,
        period: Long,
        unit: TimeUnit,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runRepeating(RepeatSchedule.async(delay, period, unit), task)
    }

    protected fun asyncTimer(
        delay: Duration,
        period: Duration,
        task: Runnable,
    ): ChainedScheduledTask {
        return scheduler.runRepeating(RepeatSchedule.async(delay, period), task)
    }

    protected fun <T : Any> registerConfig(fileName: String, type: Class<T>): T {
        return configRegistry.register(fileName, type)
    }

    protected fun <T> registerConfig(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        return configRegistry.register(fileName, type, migrationPlan)
    }

    protected inline fun <reified T : Any> registerConfig(fileName: String): T {
        return registerConfig(fileName, T::class.java)
    }

    protected inline fun <reified T> registerConfig(
        fileName: String,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        return registerConfig(fileName, T::class.java, migrationPlan)
    }

    protected fun <T : Any> currentConfig(fileName: String, type: Class<T>): T? {
        return configRegistry.current(fileName, type)
    }

    protected inline fun <reified T : Any> currentConfig(fileName: String): T? {
        return currentConfig(fileName, T::class.java)
    }

    protected fun reloadAllConfigs(): Map<String, Any> {
        return configRegistry.reloadAll()
    }

    protected fun bridgeNodeId(): BridgeNodeId {
        return bridge.nodeId()
    }

    protected fun bridgeChannel(
        namespace: String,
        key: String,
    ): BridgeChannel {
        return BridgeChannel.of(namespace, key)
    }

    protected fun publish(
        channel: String,
        payload: String,
    ) {
        bridge.publish(channel, payload)
    }

    protected fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        bridge.publish(channel, payload)
    }

    protected fun <T : Any> publish(
        channel: BridgeChannel,
        payload: T,
        codec: BridgeCodec<T>,
    ) {
        bridge.publish(channel, payload, codec)
    }

    protected fun subscribe(
        channel: String,
        listener: BridgeListener,
    ): BridgeSubscription {
        val subscription = bridge.subscribe(channel, listener)
        bridgeSubscriptions += subscription
        return subscription
    }

    protected fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        val subscription = bridge.subscribe(channel, listener)
        bridgeSubscriptions += subscription
        return subscription
    }

    protected fun <T : Any> subscribe(
        channel: BridgeChannel,
        codec: BridgeCodec<T>,
        listener: BridgePayloadListener<T>,
    ): BridgeSubscription {
        val subscription = bridge.subscribe(channel, codec) { message ->
            listener.onMessage(message.payload)
        }
        bridgeSubscriptions += subscription
        return subscription
    }

    protected fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        val subscription = bridge.respond(channel, requestCodec, responseCodec, handler)
        bridgeSubscriptions += subscription
        return subscription
    }

    protected fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        targetNode: BridgeNodeId? = null,
    ): CompletableFuture<BridgeResponse<Res>> {
        val timeout =
            kernel.service(BridgeRuntimeInfo::class)
                ?.requestTimeoutMillis
                ?: BridgeService.DEFAULT_TIMEOUT_MILLIS
        return request(
            channel = channel,
            payload = payload,
            requestCodec = requestCodec,
            responseCodec = responseCodec,
            timeoutMillis = timeout,
            targetNode = targetNode,
        )
    }

    protected fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long,
        targetNode: BridgeNodeId? = null,
    ): CompletableFuture<BridgeResponse<Res>> {
        return bridge.request(
            channel = channel,
            payload = payload,
            requestCodec = requestCodec,
            responseCodec = responseCodec,
            timeoutMillis = timeoutMillis,
            targetNode = targetNode,
        )
    }

    protected fun unsubscribe(subscription: BridgeSubscription) {
        subscription.unsubscribe()
        bridgeSubscriptions.remove(subscription)
    }

    protected fun command(
        name: String,
        tree: CommandTree,
    ) {
        registerCommandWithMetrics(commandDsl(name, tree))
    }

    protected fun command(command: STCommand<*>) {
        registerCommandWithMetrics(command.definition())
    }

    protected fun <T : STCommand<*>> command(commandClass: Class<T>): T {
        val instance = component(commandClass)
        command(instance)
        return instance
    }

    protected inline fun <reified T : STCommand<*>> command(): T {
        return command(T::class.java)
    }

    private fun registerCommandWithMetrics(definition: CommandDefinition) {
        val instrumented =
            CommandMetricsInstrumenter.instrument(definition) {
                STPlugins.markCommandExecuted(pluginName = this.name, at = Instant.now())
            }
        commandRegistrar.register(instrumented)
        repeat(instrumented.executableEndpointCount()) {
            STPlugins.markCommandRegistered(this.name)
        }
    }

    protected fun permission(node: String): String {
        return PluginConventions.permission(name, node)
    }

    protected fun can(sender: CommandSender, node: String): Boolean {
        return sender.hasPermission(permission(node))
    }

    protected fun configPath(fileName: String): Path {
        return PluginConventions.configPath(dataFolder.toPath(), fileName)
    }

    protected fun <T : Any> loadConfig(fileName: String, type: Class<T>): T {
        return configService.load(configPath(fileName), type)
    }

    protected inline fun <reified T : Any> loadConfig(fileName: String): T {
        return loadConfig(fileName, T::class.java)
    }

    protected fun <T : Any> reloadConfig(fileName: String, type: Class<T>): T {
        return configService.reload(configPath(fileName), type)
    }

    protected fun <T> reloadConfig(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        return configRegistry.reload(fileName, type, migrationPlan)
    }

    protected inline fun <reified T : Any> reloadConfig(fileName: String): T {
        return reloadConfig(fileName, T::class.java)
    }

    protected inline fun <reified T> reloadConfig(
        fileName: String,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig {
        return reloadConfig(fileName, T::class.java, migrationPlan)
    }

    protected fun <T : Any> saveConfig(fileName: String, value: T, type: Class<T>) {
        configService.save(configPath(fileName), value, type)
    }

    protected inline fun <reified T : Any> saveConfig(fileName: String, value: T) {
        saveConfig(fileName, value, T::class.java)
    }

    protected fun registerResourceProvider(provider: ResourceProvider) {
        resource.registerProvider(provider)
    }

    protected fun unregisterResourceProvider(providerId: String): Boolean {
        return resource.unregisterProvider(providerId)
    }

    protected fun listen(listener: Listener) {
        eventRegistrar.listen(listener)
    }

    protected fun <T : STListener<*>> listen(listenerClass: Class<T>): T {
        val listener = component(listenerClass)
        listen(listener)
        return listener
    }

    protected inline fun <reified T : STListener<*>> listen(): T {
        return listen(T::class.java)
    }

    protected fun <T : Any> component(type: Class<T>): T {
        return componentResolver.resolve(type)
    }

    protected inline fun <reified T : Any> component(): T {
        return component(T::class.java)
    }

    protected fun unlisten(listener: Listener) {
        eventRegistrar.unlisten(listener)
    }

    protected fun unlistenAll() {
        eventRegistrar.unlistenAll()
    }

    protected fun <T : STEvent> fire(event: T): T {
        return eventCaller().fire(event)
    }

    protected fun allPlugins(): List<STPluginSnapshot> {
        return STPlugins.all()
    }

    protected fun diComponentSummary(): ComponentScanSummary? {
        return diScanSummary
    }

    protected fun findPlugin(pluginName: String): STPluginSnapshot? {
        return STPlugins.find(pluginName)
    }

    protected fun configureCommandMetrics(enabled: Boolean) {
        STPlugins.configureCommandMetrics(enabled)
    }

    protected fun isCommandMetricsEnabled(): Boolean {
        return STPlugins.isCommandMetricsEnabled()
    }

    protected fun isDebugEnabled(): Boolean {
        return debugLoggingEnabled
    }

    protected fun debug(message: String) {
        if (!debugLoggingEnabled) {
            return
        }
        logger.info("[debug] $message")
    }

    private fun <T> required(resolve: () -> T): CachedServicePropertyDelegate<T> {
        return CachedServicePropertyDelegate(resolve)
    }

    private fun eventCaller(): BukkitEventCaller {
        return kernel.requireService()
    }

    private fun translationFacade(): BukkitTranslationFacade {
        return BukkitTranslationFacade(
            translationService = translation,
            textParser = bukkitTextParser,
        )
    }

    final override fun onLoad() {
        STPlugins.register(descriptor())
        compatibilitySupported = verifyServerCompatibility()
        if (!compatibilitySupported) {
            return
        }

        if (!prepareLoadRuntime()) {
            return
        }
        initialize()
        load()
    }

    final override fun onEnable() {
        if (!compatibilitySupported) {
            logger.severe("Skipping plugin enable because server compatibility check failed")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (!commandApiLoaded) {
            logger.severe("CommandAPI load hook was skipped; disabling plugin")
            server.pluginManager.disablePlugin(this)
            return
        }

        if (!kernelBootstrapped) {
            logger.severe("Kernel bootstrap failed in onLoad; disabling plugin")
            server.pluginManager.disablePlugin(this)
            return
        }

        val commandApiEnabled = runCatching {
            CommandApiLifecycle.onEnable(this)
        }.onFailure { error ->
            logger.severe("CommandAPI enable hook failed: ${error.message}")
        }.isSuccess
        if (!commandApiEnabled) {
            server.pluginManager.disablePlugin(this)
            return
        }

        activateResourceIntegrations()
        activateGui()
        enable()
        STPlugins.markEnabled(name)
        syncCapabilitySummary()
    }

    final override fun onDisable() {
        syncCapabilitySummary()
        STPlugins.markDisabled(name)
        DisablePipeline.run(
            disableAction = { disable() },
            unlistenAllAction = { unlistenAll() },
            cleanupAction = { cleanupRuntimeResources() },
            kernelShutdownAction = { BukkitKernelBootstrapper.shutdown(kernel) },
            commandApiShutdownAction = { CommandApiLifecycle.onDisable() },
            onStepFailure = { step, error ->
                logger.warning("Disable pipeline step '$step' failed: ${error.message ?: "unknown"}")
            },
        )
    }

    private fun prepareLoadRuntime(): Boolean {
        return PluginLoadRuntimeCoordinator(
            loadCommandApi =
                PluginLoadRuntimeCoordinator.Step(
                    run = ::loadCommandApi,
                    onFailure = { logger.severe("Skipping plugin initialize/load because CommandAPI load failed") },
                ),
            registerCoreServices =
                PluginLoadRuntimeCoordinator.Step(
                    run = ::registerCoreServices,
                    onFailure = { logger.severe("Skipping plugin initialize/load because default service registration failed") },
                ),
            bootstrapKernel =
                PluginLoadRuntimeCoordinator.Step(
                    run = ::bootstrapKernel,
                    onFailure = { logger.severe("Skipping plugin initialize/load because kernel bootstrap failed") },
                ),
            bootstrapComponentGraph =
                PluginLoadRuntimeCoordinator.Step(
                    run = ::bootstrapComponentGraph,
                    onFailure = { logger.severe("Skipping plugin initialize/load because DI component scan failed") },
                ),
            refreshRuntimeLoggingSwitches = ::refreshRuntimeLoggingSwitches,
            syncCapabilitySummary = ::syncCapabilitySummary,
        ).prepare()
    }

    private fun loadCommandApi(): Boolean {
        commandApiLoaded =
            runCatching {
                CommandApiLifecycle.onLoad(this)
            }.onFailure { error ->
                logger.severe("CommandAPI load hook failed: ${error.message}")
            }.isSuccess
        return commandApiLoaded
    }

    private fun registerCoreServices(): Boolean {
        return runCatching {
            registerDefaultServices()
        }.onFailure { error ->
            logger.severe("Default service registration failed: ${error.message}")
        }.isSuccess
    }

    private fun bootstrapKernel(): Boolean {
        kernelBootstrapped =
            runCatching {
                BukkitKernelBootstrapper.bootstrap(this, kernel, pluginVersion())
            }.onFailure { error ->
                logger.severe("STPlugin kernel bootstrap failed: ${error.message}")
            }.isSuccess
        return kernelBootstrapped
    }

    private fun bootstrapComponentGraph(): Boolean {
        val packageRoot = javaClass.packageName.orEmpty().trim()
        if (packageRoot.isBlank()) {
            capabilityRegistry.disable(CapabilityNames.RUNTIME_DI, "Plugin package root is blank")
            logger.warning("Skipping DI component scan because plugin package root is blank")
            return true
        }

        return runCatching {
            val summary = componentResolver.scan(packageRoot)
            diScanSummary = summary
            capabilityRegistry.enable(CapabilityNames.RUNTIME_DI)
            if (summary.discovered > 0) {
                logger.info(
                    "DI scan completed: package=$packageRoot, discovered=${summary.discovered}, " +
                        "validated=${summary.validated}, singletons=${summary.singletonComponents}, " +
                        "prototypes=${summary.prototypeComponents}",
                )
            }
        }.onFailure { error ->
            diScanSummary = null
            capabilityRegistry.disable(
                CapabilityNames.RUNTIME_DI,
                "Component scan failed: ${error.message ?: "unknown"}",
            )
            logger.severe("DI component scan failed: ${error.message}")
        }.isSuccess
    }

    protected open fun registerDefaultServices() {
        kernel.registerService(TextService::class, MiniMessageTextService())
        kernel.registerService(
            CommandRegistrar::class,
            BukkitCommandRegistrar(this) { isDebugEnabled() },
        )
        val bukkitEventRegistrar = BukkitEventRegistrar(this)
        val bukkitEventCaller = BukkitEventCaller(this)
        kernel.registerService(EventRegistrar::class, bukkitEventRegistrar)
        kernel.registerService(BukkitEventRegistrar::class, bukkitEventRegistrar)
        kernel.registerService(BukkitEventCaller::class, bukkitEventCaller)
        val guiService = BukkitGuiService(this)
        guiBootstrap = guiService
        kernel.registerService(StGuiService::class, guiService)
        kernel.registerService(BukkitGuiService::class, guiService)
        capabilityRegistry.disable(CapabilityNames.UI_INVENTORY, "Waiting for plugin enable")
    }

    private fun cleanupRuntimeResources() {
        bridgeSubscriptions.toList().forEach { subscription ->
            runCatching { subscription.unsubscribe() }
        }
        bridgeSubscriptions.clear()
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.shutdown() }
        runCatching { guiService.close() }
        guiBootstrap = null
    }

    private fun activateResourceIntegrations() {
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.activate() }
            .onFailure { error ->
                logger.warning("Resource integration runtime activation failed: ${error.message}")
            }
    }

    private fun activateGui() {
        val service = guiBootstrap ?: return
        runCatching {
            service.activate()
        }.onSuccess {
            capabilityRegistry.enable(CapabilityNames.UI_INVENTORY)
            syncCapabilitySummary()
        }.onFailure { error ->
            capabilityRegistry.disable(
                CapabilityNames.UI_INVENTORY,
                "GUI activation failed: ${error.message ?: "unknown"}",
            )
            logger.warning("GUI service activation failed: ${error.message}")
            syncCapabilitySummary()
        }
    }

    private fun syncCapabilitySummary() {
        STPlugins.syncCapabilitySummary(
            pluginName = name,
            capabilitySnapshot = capabilityRegistry.snapshot(),
            at = Instant.now(),
        )
    }

    private fun refreshRuntimeLoggingSwitches() {
        debugLoggingEnabled =
            kernel.service(PluginFileConfig::class)
                ?.plugin
                ?.debug == true
    }

    private fun verifyServerCompatibility(): Boolean {
        val compatibilityVerifier =
            PluginCompatibilityVerifier(
                logger = logger,
                pluginName = description.name,
                pluginVersion = pluginVersion(),
            )
        return compatibilityVerifier.verify(
            minecraftPolicy = minecraftVersions(),
            minecraftResolution = BukkitServerVersionResolver.resolve(server),
            minecraftMismatchAction = minecraftMismatchAction(),
            runtimePolicy = supportedRuntimes(),
            runtimeResolution = BukkitRuntimeResolver.resolve(server),
            runtimeMismatchAction = runtimeMismatchAction(),
        )
    }

    private fun descriptor(): STPluginDescriptor {
        val pluginName = description.name.ifBlank { name }
        val pluginVersion = pluginVersion()
        val mainClass = description.main.ifBlank { javaClass.name }

        return STPluginDescriptor(
            name = pluginName,
            version = pluginVersion,
            mainClass = mainClass,
        )
    }

    companion object {
        @JvmStatic
        fun plugins(): List<STPluginSnapshot> {
            return STPlugins.all()
        }

        @JvmStatic
        fun plugin(name: String): STPluginSnapshot? {
            return STPlugins.find(name)
        }
    }
}
