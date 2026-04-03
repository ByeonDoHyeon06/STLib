package studio.singlethread.lib.framework.bukkit.version

/**
 * Version support policy for STPlugin server compatibility checks.
 *
 * Policy modes:
 * - range mode: min/max inclusive bounds
 * - exact mode: fixed allowlist
 */
data class SupportedServerVersions(
    val minInclusive: MinecraftVersion? = null,
    val maxInclusive: MinecraftVersion? = null,
    val exact: Set<MinecraftVersion> = emptySet(),
) {
    init {
        if (minInclusive != null && maxInclusive != null) {
            require(minInclusive <= maxInclusive) {
                "minInclusive must be <= maxInclusive"
            }
        }
    }

    fun isSupported(version: MinecraftVersion): Boolean {
        if (exact.isNotEmpty()) {
            return exact.contains(version)
        }

        if (minInclusive != null && version < minInclusive) {
            return false
        }
        if (maxInclusive != null && version > maxInclusive) {
            return false
        }
        return true
    }

    fun describe(): String {
        if (exact.isNotEmpty()) {
            return exact.sorted().joinToString(", ")
        }

        val min = minInclusive?.toString() ?: "*"
        val max = maxInclusive?.toString() ?: "*"
        return "$min ~ $max"
    }

    companion object {
        fun any(): SupportedServerVersions {
            return SupportedServerVersions()
        }

        fun range(
            minInclusive: String? = null,
            maxInclusive: String? = null,
        ): SupportedServerVersions {
            return SupportedServerVersions(
                minInclusive = minInclusive?.let(MinecraftVersion::parseOrThrow),
                maxInclusive = maxInclusive?.let(MinecraftVersion::parseOrThrow),
            )
        }

        fun exact(vararg versions: String): SupportedServerVersions {
            require(versions.isNotEmpty()) { "versions must not be empty" }
            val parsed = versions.map(MinecraftVersion::parseOrThrow).toSet()
            return SupportedServerVersions(exact = parsed)
        }
    }
}

