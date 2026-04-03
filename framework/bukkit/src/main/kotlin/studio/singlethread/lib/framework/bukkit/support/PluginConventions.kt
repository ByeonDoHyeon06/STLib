package studio.singlethread.lib.framework.bukkit.support

import java.nio.file.Path
import java.nio.file.Paths

internal object PluginConventions {
    fun permission(pluginName: String, node: String): String {
        val prefix = pluginName.trim().lowercase()
        require(prefix.isNotBlank()) { "pluginName must not be blank" }

        val normalizedNode = node.trim().lowercase().trim('.')
        if (normalizedNode.isBlank()) {
            return prefix
        }
        if (normalizedNode.startsWith("$prefix.")) {
            return normalizedNode
        }
        return "$prefix.$normalizedNode"
    }

    fun configPath(dataDirectory: Path, fileName: String): Path {
        val normalizedName = fileName.trim()
        require(normalizedName.isNotBlank()) { "fileName must not be blank" }

        val withExtension =
            if (normalizedName.endsWith(".yml", ignoreCase = true)) {
                normalizedName
            } else {
                "$normalizedName.yml"
            }

        val relative = Paths.get(withExtension).normalize()
        require(!relative.isAbsolute) { "fileName must be relative path" }
        require(!relative.startsWith("..")) { "fileName must not escape config directory" }

        return dataDirectory
            .resolve("config")
            .resolve(relative)
            .normalize()
    }
}

