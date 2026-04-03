package studio.singlethread.lib.framework.bukkit.translation

internal object TranslationLocaleNormalizer {
    fun normalize(locale: String?): String {
        return locale
            ?.trim()
            ?.lowercase()
            ?.replace('-', '_')
            ?.takeIf { it.isNotBlank() }
            ?: ""
    }
}

