package studio.singlethread.lib.framework.core.text

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.singlethread.lib.framework.api.text.TextService

class MiniMessageTextService(
    private val miniMessage: MiniMessage = MiniMessage.miniMessage(),
) : TextService {
    override fun parse(message: String): Component {
        return miniMessage.deserialize(message)
    }

    override fun parse(message: String, placeholders: Map<String, String>): Component {
        if (placeholders.isEmpty()) {
            return parse(message)
        }

        val resolver = TagResolver.builder().apply {
            placeholders.forEach { (key, value) ->
                val normalizedKey = normalizePlaceholderKey(key)
                if (normalizedKey.isBlank()) {
                    return@forEach
                }
                resolver(Placeholder.parsed(normalizedKey, value))
            }
        }.build()

        return miniMessage.deserialize(message, resolver)
    }

    private fun normalizePlaceholderKey(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            return ""
        }

        val camelToSnake = trimmed.replace(CAMEL_CASE_BOUNDARY, "$1_$2")
        val lowered = camelToSnake.lowercase()
        val normalized = lowered.replace(INVALID_TAG_CHARS, "_")
        return normalized.replace(DUPLICATE_UNDERSCORES, "_").trim('_')
    }

    private companion object {
        private val CAMEL_CASE_BOUNDARY = Regex("([a-z0-9])([A-Z])")
        private val INVALID_TAG_CHARS = Regex("[^a-z0-9_-]")
        private val DUPLICATE_UNDERSCORES = Regex("_+")
    }
}
