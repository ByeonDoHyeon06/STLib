package studio.singlethread.lib.framework.bukkit.translation

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class TranslationFileSettings {
    @field:Comment("Default locale used when sender locale is unavailable. Example: ko_kr, en_us")
    var defaultLocale: String = "en_us"

    @field:Comment("Optional intermediate fallback locale. Leave blank to skip.")
    var fallbackLocale: String = ""
}
