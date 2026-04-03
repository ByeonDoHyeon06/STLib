package studio.singlethread.lib.framework.bukkit.version

/**
 * Parsed Minecraft semantic-like version used for compatibility checks.
 *
 * Examples:
 * - `1.20`
 * - `1.20.1`
 * - strings containing version tokens such as `git-Paper-17 (MC: 1.20.1)`
 */
data class MinecraftVersion(
    val major: Int,
    val minor: Int,
    val patch: Int = 0,
) : Comparable<MinecraftVersion> {
    init {
        require(major >= 0) { "major must be >= 0" }
        require(minor >= 0) { "minor must be >= 0" }
        require(patch >= 0) { "patch must be >= 0" }
    }

    override fun compareTo(other: MinecraftVersion): Int {
        if (major != other.major) {
            return major.compareTo(other.major)
        }
        if (minor != other.minor) {
            return minor.compareTo(other.minor)
        }
        return patch.compareTo(other.patch)
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        private val TOKEN = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

        fun parse(raw: String): MinecraftVersion? {
            val match = TOKEN.find(raw) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: 0
            return MinecraftVersion(
                major = major,
                minor = minor,
                patch = patch,
            )
        }

        fun parseOrThrow(raw: String): MinecraftVersion {
            return parse(raw)
                ?: throw IllegalArgumentException("Unable to parse minecraft version from '$raw'")
        }
    }
}
