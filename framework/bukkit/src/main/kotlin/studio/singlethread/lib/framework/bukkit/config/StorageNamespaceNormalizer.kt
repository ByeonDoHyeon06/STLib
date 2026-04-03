package studio.singlethread.lib.framework.bukkit.config

internal object StorageNamespaceNormalizer {
    fun normalize(input: String, fallback: String): String {
        val base = input.ifBlank { fallback }
        val sanitized = base.lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitized.isNotBlank()) {
            return sanitized
        }

        return "stlib"
    }
}

