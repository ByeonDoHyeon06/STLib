package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.config.ConfigRegistry
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.bukkit.config.BukkitConfigRegistry
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfig
import studio.singlethread.lib.framework.bukkit.config.PluginFileConfigLoader
import studio.singlethread.lib.platform.common.capability.CapabilityNames

internal data class ConfigBootstrapResult(
    val configService: ConfigService,
    val pluginConfig: PluginFileConfig,
)

internal object ConfigBootstrapStep {
    fun bootstrap(
        plugin: JavaPlugin,
        kernel: STKernel,
        capabilities: CapabilityRegistry,
        pluginVersion: String,
    ): ConfigBootstrapResult {
        val configService = ConfigurateConfigService()
        kernel.registerService(ConfigService::class, configService)
        capabilities.enable(CapabilityNames.CONFIG_CONFIGURATE)

        kernel.registerService(
            ConfigRegistry::class,
            BukkitConfigRegistry(
                configService = configService,
                dataDirectory = plugin.dataFolder.toPath(),
                logWarning = plugin.logger::warning,
            ),
        )
        capabilities.enable(CapabilityNames.CONFIG_REGISTRY)

        val pluginConfig =
            PluginFileConfigLoader(
                configService = configService,
                dataDirectory = plugin.dataFolder.toPath(),
                pluginName = plugin.name,
                pluginVersion = pluginVersion,
            ).load()
        kernel.registerService(PluginFileConfig::class, pluginConfig)

        return ConfigBootstrapResult(
            configService = configService,
            pluginConfig = pluginConfig,
        )
    }
}
