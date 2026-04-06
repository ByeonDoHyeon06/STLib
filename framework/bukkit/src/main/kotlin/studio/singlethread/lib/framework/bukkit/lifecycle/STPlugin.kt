package studio.singlethread.lib.framework.bukkit.lifecycle

import org.bukkit.command.CommandSender
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandRegistrar
import studio.singlethread.lib.framework.api.command.STCommand
import studio.singlethread.lib.framework.api.command.CommandTree
import studio.singlethread.lib.framework.api.command.commandDsl
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.di.ComponentScanSummary
import studio.singlethread.lib.framework.api.event.EventRegistrar
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.lifecycle.STLifecycle
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.api.kernel.requireService
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.framework.bukkit.bootstrap.BootstrapDiagnostics
import studio.singlethread.lib.framework.bukkit.bootstrap.BukkitKernelBootstrapper
import studio.singlethread.lib.framework.bukkit.command.CommandApiLifecycle
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.di.ReflectiveComponentResolver
import studio.singlethread.lib.framework.bukkit.event.BukkitEventCaller
import studio.singlethread.lib.framework.bukkit.event.STEvent
import studio.singlethread.lib.framework.bukkit.event.STListener
import studio.singlethread.lib.framework.bukkit.gui.BukkitGuiService
import studio.singlethread.lib.framework.bukkit.gui.STGuiService
import studio.singlethread.lib.framework.bukkit.management.STPluginDescriptor
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPlugins
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.lifecycle.support.CachedServicePropertyDelegate
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginCompatibilityVerifier
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginLoadRuntimeCoordinator
import studio.singlethread.lib.framework.bukkit.lifecycle.support.STPluginRuntimeServices
import studio.singlethread.lib.framework.bukkit.lifecycle.support.STPluginStartupLogger
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolver
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolver
import studio.singlethread.lib.framework.bukkit.version.SupportedBukkitRuntimes
import studio.singlethread.lib.framework.bukkit.version.SupportedServerVersions
import studio.singlethread.lib.framework.bukkit.version.UnsupportedServerVersionAction
import studio.singlethread.lib.framework.core.kernel.DefaultSTKernel
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import studio.singlethread.lib.registry.common.service.ResourceService
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageApi
import java.time.Instant

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
    private var bootstrapDiagnostics: BootstrapDiagnostics? = null
    private var guiBootstrap: BukkitGuiService? = null
    private var diScanSummary: ComponentScanSummary? = null
    private val componentResolver: ReflectiveComponentResolver by lazy(LazyThreadSafetyMode.NONE) {
        ReflectiveComponentResolver(owner = this, kernel = kernel)
    }
    private val runtimeServices: STPluginRuntimeServices by lazy(LazyThreadSafetyMode.NONE) {
        STPluginRuntimeServices(
            plugin = this,
            kernel = kernel,
            capabilityRegistry = capabilityRegistry,
            logger = logger,
        )
    }
    private val startupLogger: STPluginStartupLogger by lazy(LazyThreadSafetyMode.NONE) {
        STPluginStartupLogger(
            logger = logger,
            pluginName = description.name.ifBlank { name },
            debugEnabled = { debugLoggingEnabled },
        )
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

    protected val bridge: studio.singlethread.lib.framework.api.bridge.BridgeService by required { kernel.requireService() }

    protected val gui: STGuiService by required { kernel.requireService() }

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

    protected fun console(message: String, placeholders: Map<String, String> = emptyMap()) {
        server.consoleSender.sendMessage(bukkitTextParser.parse(message, placeholders))
    }

    protected fun send(sender: CommandSender, message: String, placeholders: Map<String, String> = emptyMap()) {
        sender.sendMessage(
            bukkitTextParser.parse(
                sender = sender,
                message = message,
                placeholders = placeholders,
            ),
        )
    }

    protected fun command(name: String, tree: CommandTree) {
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

    protected fun diComponentSummary(): ComponentScanSummary? {
        return diScanSummary
    }

    private fun <T> required(resolve: () -> T): CachedServicePropertyDelegate<T> {
        return CachedServicePropertyDelegate(resolve)
    }

    private fun eventCaller(): BukkitEventCaller {
        return kernel.requireService()
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

        bootstrapDiagnostics?.let(startupLogger::logBootstrapReady)

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
        startupLogger.logEnabledSuccessfully()
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
                val diagnostics = BukkitKernelBootstrapper.bootstrap(this, kernel, pluginVersion())
                bootstrapDiagnostics = diagnostics
            }.onFailure { error ->
                bootstrapDiagnostics = null
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
        guiBootstrap = runtimeServices.registerDefaultServices { debugLoggingEnabled }
    }

    private fun cleanupRuntimeResources() {
        runtimeServices.cleanupRuntimeResources(guiService = kernel.service(STGuiService::class))
        guiBootstrap = null
    }

    private fun activateResourceIntegrations() {
        runtimeServices.activateResourceIntegrations()
    }

    private fun activateGui() {
        runtimeServices.activateGui(guiService = guiBootstrap, syncCapabilitySummary = ::syncCapabilitySummary)
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
