package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
class PluginSettingsFile {
    /**
     * Plugin version used by STPlugin runtime metadata and dashboards.
     *
     * When blank, STPlugin falls back to constructor option or plugin descriptor version.
     */
    var version: String = ""

    /**
     * Enables optional framework debug logs.
     */
    var debug: Boolean = false
}
