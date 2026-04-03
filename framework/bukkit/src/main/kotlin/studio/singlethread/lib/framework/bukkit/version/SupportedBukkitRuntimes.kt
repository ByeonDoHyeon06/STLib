package studio.singlethread.lib.framework.bukkit.version

data class SupportedBukkitRuntimes(
    val allowed: Set<BukkitRuntime> = emptySet(),
) {
    fun isSupported(runtime: BukkitRuntime): Boolean {
        if (allowed.isEmpty()) {
            return true
        }
        return allowed.contains(runtime)
    }

    fun isAny(): Boolean {
        return allowed.isEmpty()
    }

    fun describe(): String {
        if (allowed.isEmpty()) {
            return "any"
        }
        return allowed.sortedBy { it.name }.joinToString(", ") { it.name.lowercase() }
    }

    companion object {
        fun any(): SupportedBukkitRuntimes {
            return SupportedBukkitRuntimes()
        }

        fun only(vararg runtimes: BukkitRuntime): SupportedBukkitRuntimes {
            require(runtimes.isNotEmpty()) { "runtimes must not be empty" }
            return SupportedBukkitRuntimes(runtimes.toSet())
        }
    }
}
