package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class PluginSettingsFile {
    /**
     * Plugin version used by STPlugin runtime metadata and dashboards.
     *
     * When blank, STPlugin falls back to constructor option or plugin descriptor version.
     */
    @field:Comment("Plugin semantic version. Leave blank to use STPlugin constructor fallback or descriptor version.")
    var version: String = ""

    /**
     * Enables optional framework debug logs.
     */
    @field:Comment("Enable framework debug logging.")
    var debug: Boolean = false
}
