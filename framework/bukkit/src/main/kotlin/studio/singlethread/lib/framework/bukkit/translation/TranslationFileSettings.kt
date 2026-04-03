package studio.singlethread.lib.framework.bukkit.translation

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
class TranslationFileSettings {
    var defaultLocale: String = "en_us"
    var fallbackLocale: String = ""
}

