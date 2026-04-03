package studio.singlethread.lib.framework.bukkit.translation

import studio.singlethread.lib.framework.api.translation.TranslationService

object NoopTranslationService : TranslationService {
    override fun translate(
        key: String,
        locale: String?,
        placeholders: Map<String, String>,
    ): String {
        return "!$key!"
    }

    override fun reload() = Unit
}

