package studio.singlethread.lib.framework.bukkit.lifecycle

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandDslBuilder
import studio.singlethread.lib.framework.api.command.CommandRegistrar
import studio.singlethread.lib.framework.api.command.STCommand
import studio.singlethread.lib.framework.api.command.commandDsl
import studio.singlethread.lib.framework.api.config.ConfigMigrationPlan
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.config.VersionedConfig
import studio.singlethread.lib.framework.api.event.EventRegistrar
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.lifecycle.STLifecycle
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.api.kernel.requireService
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bootstrap.BukkitKernelBootstrapper
import studio.singlethread.lib.framework.bukkit.command.BukkitCommandRegistrar
import studio.singlethread.lib.framework.bukkit.command.CommandApiLifecycle
import studio.singlethread.lib.framework.bukkit.config.BukkitConfigRegistry
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.di.ReflectiveComponentResolver
import studio.singlethread.lib.framework.bukkit.event.BukkitEventCaller
import studio.singlethread.lib.framework.bukkit.event.BukkitEventRegistrar
import studio.singlethread.lib.framework.bukkit.event.STEvent
import studio.singlethread.lib.framework.bukkit.event.STListener
import studio.singlethread.lib.framework.bukkit.inventory.BukkitInventoryUiService
import studio.singlethread.lib.framework.bukkit.inventory.InventoryUiService
import studio.singlethread.lib.framework.bukkit.inventory.StMenu
import studio.singlethread.lib.framework.bukkit.inventory.StMenuBuilder
import studio.singlethread.lib.framework.bukkit.lifecycle.toolkit.InventoryUiToolkit
import studio.singlethread.lib.framework.bukkit.management.STPluginDescriptor
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPlugins
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.bukkit.support.PluginConventions
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.translation.BukkitTranslationFacade
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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KProperty

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
    private var inventoryUiBootstrap: BukkitInventoryUiService? = null
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

    protected val eventRegistrar: EventRegistrar by required { kernel.requireService() }

    protected val configService: ConfigService by required { kernel.requireService() }

    protected val configRegistry: ConfigRegistry by required { kernel.requireService() }

    protected val storageApi: StorageApi by required { kernel.requireService() }

    protected val storage: Storage by required { kernel.requireService() }

    protected val bridge: BridgeService by required { kernel.requireService() }

    protected val inventoryUi: InventoryUiService by required { kernel.requireService() }

    protected val resource: ResourceService by required { kernel.requireService() }

    protected val pluginConfig: PluginFileConfig by required { kernel.requireService() }

    private val bukkitTextParser: BukkitTextParser by required { kernel.requireService() }

    /**
     * UI operations are grouped to keep STPlugin focused on lifecycle concerns.
     */
    protected val ui: InventoryUiToolkit by lazy(LazyThreadSafetyMode.NONE) {
        InventoryUiToolkit(
            inventoryUiService = inventoryUi,
            parseTitle = ::mini,
        )
    }

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

    protected fun tell(
        target: Audience,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        notifier.send(target, message, placeholders)
    }

    protected fun announce(
        target: Audience,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        notifier.sendPrefixed(target, message, placeholders)
    }

    protected fun tellTranslated(
        target: Audience,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
        prefixed: Boolean = false,
    ) {
        val translated = translation.translate(key, null, placeholders)
        if (prefixed) {
            notifier.sendPrefixed(target, translated)
            return
        }
        notifier.send(target, translated)
    }

    protected fun actionBar(
        target: Audience,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        notifier.actionBar(target, message, placeholders)
    }

    protected fun title(
        target: Audience,
        title: String,
        subtitle: String = "",
        placeholders: Map<String, String> = emptyMap(),
    ) {
        notifier.title(target, title, subtitle, placeholders)
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

    protected fun publish(
        channel: String,
        payload: String,
    ) {
        bridge.publish(channel, payload)
    }

    protected fun subscribe(
        channel: String,
        listener: (channel: String, payload: String) -> Unit,
    ): BridgeSubscription {
        val subscription = bridge.subscribe(channel) { incomingChannel, payload ->
            listener(incomingChannel, payload)
        }
        bridgeSubscriptions += subscription
        return subscription
    }

    protected fun unsubscribe(subscription: BridgeSubscription) {
        subscription.unsubscribe()
        bridgeSubscriptions.remove(subscription)
    }

    protected fun console(message: String, placeholders: Map<String, String> = emptyMap()) {
        server.consoleSender.sendMessage(mini(message, placeholders))
    }

    protected fun send(sender: CommandSender, message: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(mini(sender, message, placeholders))
    }

    protected fun command(
        name: String,
        builder: CommandDslBuilder.() -> Unit,
    ) {
        registerCommandWithMetrics(commandDsl(name, builder))
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
        val instrumented = instrumentCommandDefinition(definition)
        commandRegistrar.register(instrumented)
        repeat(instrumented.executableEndpointCount()) {
            STPlugins.markCommandRegistered(this.name)
        }
    }

    private fun instrumentCommandDefinition(definition: CommandDefinition): CommandDefinition {
        return definition.copy(
            executor = definition.executor?.let(::instrumentExecutor),
            children = definition.children.map(::instrumentNode),
        )
    }

    private fun instrumentNode(
        node: studio.singlethread.lib.framework.api.command.CommandNodeSpec,
    ): studio.singlethread.lib.framework.api.command.CommandNodeSpec {
        return node.copy(
            executor = node.executor?.let(::instrumentExecutor),
            children = node.children.map(::instrumentNode),
        )
    }

    private fun instrumentExecutor(
        delegate: studio.singlethread.lib.framework.api.command.CommandExecutor,
    ): studio.singlethread.lib.framework.api.command.CommandExecutor {
        return studio.singlethread.lib.framework.api.command.CommandExecutor { context ->
            STPlugins.markCommandExecuted(pluginName = this.name, at = Instant.now())
            delegate.execute(context)
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

    private fun <T> required(resolve: () -> T): RequiredService<T> {
        return RequiredService(resolve)
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
        activateInventoryUi()
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
        if (!loadCommandApi()) {
            logger.severe("Skipping plugin initialize/load because CommandAPI load failed")
            return false
        }
        if (!registerCoreServices()) {
            logger.severe("Skipping plugin initialize/load because default service registration failed")
            return false
        }
        if (!bootstrapKernel()) {
            logger.severe("Skipping plugin initialize/load because kernel bootstrap failed")
            return false
        }
        refreshRuntimeLoggingSwitches()
        syncCapabilitySummary()
        return true
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
        val inventoryUi = BukkitInventoryUiService(this)
        inventoryUiBootstrap = inventoryUi
        kernel.registerService(InventoryUiService::class, inventoryUi)
        kernel.registerService(BukkitInventoryUiService::class, inventoryUi)
        capabilityRegistry.disable(CapabilityNames.UI_INVENTORY, "Waiting for plugin enable")
    }

    private fun cleanupRuntimeResources() {
        bridgeSubscriptions.toList().forEach { subscription ->
            runCatching { subscription.unsubscribe() }
        }
        bridgeSubscriptions.clear()
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.shutdown() }
        runCatching { inventoryUi.close() }
        inventoryUiBootstrap = null
    }

    private fun activateResourceIntegrations() {
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.activate() }
            .onFailure { error ->
                logger.warning("Resource integration runtime activation failed: ${error.message}")
            }
    }

    private fun activateInventoryUi() {
        val service = inventoryUiBootstrap ?: return
        runCatching {
            service.activate()
        }.onSuccess {
            capabilityRegistry.enable(CapabilityNames.UI_INVENTORY)
            syncCapabilitySummary()
        }.onFailure { error ->
            capabilityRegistry.disable(
                CapabilityNames.UI_INVENTORY,
                "Inventory UI activation failed: ${error.message ?: "unknown"}",
            )
            logger.warning("Inventory UI service activation failed: ${error.message}")
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
        if (!verifyMinecraftVersionCompatibility()) {
            return false
        }
        return verifyBukkitRuntimeCompatibility()
    }

    private fun verifyMinecraftVersionCompatibility(): Boolean {
        val policy = minecraftVersions()
        val resolved = BukkitServerVersionResolver.resolve(server)
        val serverVersion = resolved.resolved

        if (serverVersion == null) {
            logger.warning(
                "Unable to resolve minecraft version for '${description.name}'. " +
                    "Skipping compatibility gate. Candidates=${resolved.candidates}",
            )
            return true
        }

        if (policy.isSupported(serverVersion)) {
            return true
        }

        val baseMessage =
            "Unsupported minecraft version '$serverVersion' for '${description.name}' v${pluginVersion()}. " +
                "Supported versions: ${policy.describe()}"

        return when (minecraftMismatchAction()) {
            UnsupportedServerVersionAction.WARN_ONLY -> {
                logger.warning(baseMessage)
                true
            }

            UnsupportedServerVersionAction.DISABLE_PLUGIN -> {
                logger.severe("$baseMessage. Plugin will remain disabled.")
                false
            }
        }
    }

    private fun verifyBukkitRuntimeCompatibility(): Boolean {
        val policy = supportedRuntimes()
        if (policy.isAny()) {
            return true
        }

        val resolved = BukkitRuntimeResolver.resolve(server)
        if (policy.isSupported(resolved.runtime)) {
            return true
        }

        val baseMessage =
            "Unsupported bukkit runtime '${resolved.runtime.name.lowercase()}' for '${description.name}' v${pluginVersion()}. " +
                "Supported runtimes: ${policy.describe()} (hints=${resolved.hints})"

        return when (runtimeMismatchAction()) {
            UnsupportedServerVersionAction.WARN_ONLY -> {
                logger.warning(baseMessage)
                true
            }

            UnsupportedServerVersionAction.DISABLE_PLUGIN -> {
                logger.severe("$baseMessage. Plugin will remain disabled.")
                false
            }
        }
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

    private class RequiredService<T>(
        private val resolve: () -> T,
    ) {
        operator fun getValue(thisRef: STPlugin, property: KProperty<*>): T {
            return resolve()
        }
    }
}
