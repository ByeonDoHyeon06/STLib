package studio.singlethread.lib.framework.api.translation

interface TranslationService {
    fun translate(
        key: String,
        locale: String? = null,
        placeholders: Map<String, String> = emptyMap(),
    ): String

    fun reload()
}

