package studio.singlethread.lib.framework.bukkit.config

data class PluginFileConfig(
    val plugin: PluginSettingsFile,
    val storage: StorageFileSettings,
    val dependencies: DependFileSettings,
    val bridge: BridgeFileSettings,
)
