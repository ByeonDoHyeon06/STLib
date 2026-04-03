package studio.singlethread.lib.framework.bukkit.version

import org.bukkit.Server

data class BukkitServerVersionResolution(
    val resolved: MinecraftVersion?,
    val candidates: List<String>,
)

object BukkitServerVersionResolver {
    fun resolve(server: Server): BukkitServerVersionResolution {
        val candidates = buildCandidateStrings(server)
        val resolved = candidates.asSequence().mapNotNull(MinecraftVersion::parse).firstOrNull()
        return BukkitServerVersionResolution(
            resolved = resolved,
            candidates = candidates,
        )
    }

    private fun buildCandidateStrings(server: Server): List<String> {
        val out = linkedSetOf<String>()
        reflectMinecraftVersion(server)?.takeIf(String::isNotBlank)?.let(out::add)
        server.bukkitVersion.takeIf(String::isNotBlank)?.let(out::add)
        server.version.takeIf(String::isNotBlank)?.let(out::add)
        return out.toList()
    }

    private fun reflectMinecraftVersion(server: Server): String? {
        return runCatching {
            val method = server.javaClass.getMethod("getMinecraftVersion")
            method.invoke(server) as? String
        }.getOrNull()
    }
}

