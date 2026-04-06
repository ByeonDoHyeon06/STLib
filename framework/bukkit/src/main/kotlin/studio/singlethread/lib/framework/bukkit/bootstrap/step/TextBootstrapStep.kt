package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.kernel.requireService
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.scheduler.SchedulerService
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.notifier.BukkitNotifierService
import studio.singlethread.lib.framework.bukkit.scheduler.BukkitSchedulerService
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.text.NoopPlaceholderResolver
import studio.singlethread.lib.framework.bukkit.text.PlaceholderApiResolver
import studio.singlethread.lib.framework.bukkit.text.PlaceholderResolver
import studio.singlethread.lib.framework.bukkit.translation.BukkitTranslationService
import studio.singlethread.lib.framework.bukkit.translation.BundledTranslationInstaller
import studio.singlethread.lib.framework.bukkit.translation.NoopTranslationService
import studio.singlethread.lib.platform.common.capability.CapabilityNames

internal object TextBootstrapStep {
    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        configService: ConfigService,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
    ) {
        val textService = kernel.requireService<TextService>()
        val placeholderResolver = createPlaceholderResolver(plugin, pluginConfig, capabilities)
        kernel.registerService(PlaceholderResolver::class, placeholderResolver)
        kernel.registerService(
            BukkitTextParser::class,
            BukkitTextParser(
                textService = textService,
                placeholderResolver = placeholderResolver,
            ),
        )

        val translationService = createTranslationService(plugin, configService, capabilities)
        kernel.registerService(TranslationService::class, translationService)

        kernel.registerService(
            NotifierService::class,
            BukkitNotifierService(
                textService = textService,
                pluginName = plugin.name,
            ),
        )
        capabilities.enable(CapabilityNames.TEXT_NOTIFIER)

        kernel.registerService(
            SchedulerService::class,
            BukkitSchedulerService(plugin, debugLoggingEnabled = { pluginConfig.plugin.debug }),
        )
        capabilities.enable(CapabilityNames.RUNTIME_SCHEDULER)
    }

    private fun createPlaceholderResolver(
        plugin: JavaPlugin,
        pluginConfig: PluginFileConfig,
        capabilities: CapabilityRegistry,
    ): PlaceholderResolver {
        if (!pluginConfig.dependencies.integrations.placeholderApi) {
            capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "Disabled by config/depend.yml")
            return NoopPlaceholderResolver
        }

        val placeholderPluginInstalled = plugin.server.pluginManager.getPlugin("PlaceholderAPI") != null
        if (!placeholderPluginInstalled) {
            capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "PlaceholderAPI plugin not installed")
            return NoopPlaceholderResolver
        }

        val resolver = PlaceholderApiResolver.createOrNull(plugin.logger)
        if (resolver != null) {
            capabilities.enable(CapabilityNames.TEXT_PLACEHOLDERAPI)
            return resolver
        }

        capabilities.disable(CapabilityNames.TEXT_PLACEHOLDERAPI, "PlaceholderAPI bridge initialization failed")
        return NoopPlaceholderResolver
    }

    private fun createTranslationService(
        plugin: JavaPlugin,
        configService: ConfigService,
        capabilities: CapabilityRegistry,
    ): TranslationService {
        val translationDirectory = plugin.dataFolder.toPath().resolve("translation")
        BundledTranslationInstaller.installMissing(
            plugin = plugin,
            translationDirectory = translationDirectory,
            logger = plugin.logger,
        )

        val service =
            runCatching {
                BukkitTranslationService(
                    configService = configService,
                    configPath = plugin.dataFolder.toPath().resolve("config/translation.yml"),
                    translationDirectory = translationDirectory,
                    logger = plugin.logger,
                )
            }.onFailure { error ->
                plugin.logger.warning("Translation service initialization failed: ${error.message}")
            }.getOrNull()

        if (service != null) {
            capabilities.enable(CapabilityNames.TEXT_TRANSLATION)
            return service
        }

        capabilities.disable(CapabilityNames.TEXT_TRANSLATION, "Translation service bootstrap failed")
        return NoopTranslationService
    }
}
