package studio.singlethread.lib.framework.bukkit.version

import org.bukkit.Server

data class BukkitRuntimeResolution(
    val runtime: BukkitRuntime,
    val hints: List<String>,
)

object BukkitRuntimeResolver {
    fun resolve(server: Server): BukkitRuntimeResolution {
        val hints = buildHints(server)
        val runtime = resolveRuntime(server, hints)
        return BukkitRuntimeResolution(
            runtime = runtime,
            hints = hints,
        )
    }

    private fun resolveRuntime(
        server: Server,
        hints: List<String>,
    ): BukkitRuntime {
        val joined = hints.joinToString(separator = " ").lowercase()
        if (joined.contains("folia")) {
            return BukkitRuntime.FOLIA
        }
        if (isFoliaRuntime(server)) {
            return BukkitRuntime.FOLIA
        }
        if (joined.contains("paper") || joined.contains("purpur") || joined.contains("pufferfish")) {
            return BukkitRuntime.PAPER
        }
        if (joined.contains("spigot")) {
            return BukkitRuntime.SPIGOT
        }
        if (joined.contains("craftbukkit") || joined.contains("bukkit")) {
            return BukkitRuntime.BUKKIT
        }
        return BukkitRuntime.UNKNOWN
    }

    private fun buildHints(server: Server): List<String> {
        val hints = linkedSetOf<String>()
        server.name.takeIf(String::isNotBlank)?.let(hints::add)
        server.version.takeIf(String::isNotBlank)?.let(hints::add)
        server.bukkitVersion.takeIf(String::isNotBlank)?.let(hints::add)
        return hints.toList()
    }

    private fun isFoliaRuntime(server: Server): Boolean {
        return runCatching {
            Class.forName(
                "io.papermc.paper.threadedregions.RegionizedServer",
                false,
                server.javaClass.classLoader,
            )
        }.isSuccess
    }
}
