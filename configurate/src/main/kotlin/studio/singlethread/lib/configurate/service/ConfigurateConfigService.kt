package studio.singlethread.lib.configurate.service

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import studio.singlethread.lib.framework.api.config.ConfigService
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.lang.reflect.Modifier
import kotlin.io.path.bufferedReader

class ConfigurateConfigService : ConfigService {
    override fun <T : Any> load(path: Path, type: Class<T>): T {
        val existedBeforeLoad = Files.exists(path)
        val loader = loader(path)
        val node = loader.load()
        val loaded = node.get(type)
        if (loaded != null) {
            if (shouldMaterializeDefaults(path, existedBeforeLoad)) {
                node.set(type, loaded)
                loader.save(node)
                applyCommentHints(path, type)
            }
            return loaded
        }

        val defaultValue = createDefault(type)
        node.set(type, defaultValue)
        loader.save(node)
        applyCommentHints(path, type)
        return defaultValue
    }

    override fun <T : Any> save(path: Path, value: T, type: Class<T>) {
        val loader = loader(path)
        val node = loader.load()
        node.set(type, value)
        loader.save(node)
        applyCommentHints(path, type)
    }

    override fun <T : Any> reload(path: Path, type: Class<T>): T {
        return load(path, type)
    }

    private fun loader(path: Path): YamlConfigurationLoader {
        Files.createDirectories(path.parent ?: path.toAbsolutePath().parent)

        return YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions { options ->
                options.shouldCopyDefaults(true)
            }
            .build()
    }

    private fun <T : Any> createDefault(type: Class<T>): T {
        return runCatching {
            val constructor = type.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance()
        }.getOrElse { error ->
            throw IllegalStateException(
                "Unable to construct default config for ${type.name}; add a no-arg constructor or pre-create config file.",
                error,
            )
        }
    }

    private fun shouldMaterializeDefaults(path: Path, existedBeforeLoad: Boolean): Boolean {
        if (!existedBeforeLoad) {
            return true
        }

        val isEmpty =
            runCatching {
                Files.size(path) == 0L
            }.getOrDefault(false)
        if (isEmpty) {
            return true
        }

        return !hasYamlComments(path)
    }

    private fun hasYamlComments(path: Path): Boolean {
        return runCatching {
            path.bufferedReader().useLines { lines ->
                lines.any { line ->
                    line.trimStart().startsWith("#")
                }
            }
        }.getOrDefault(false)
    }

    private fun applyCommentHints(path: Path, type: Class<*>) {
        val comments = collectCommentHints(type)
        if (comments.isEmpty()) {
            return
        }

        runCatching {
            val lines = Files.readAllLines(path)
            val rewritten = injectComments(lines, comments)
            if (rewritten == lines) {
                return
            }
            Files.write(path, rewritten, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        }
    }

    private fun collectCommentHints(type: Class<*>): Map<String, String> {
        val collected = LinkedHashMap<String, String>()
        collectCommentHintsRecursive(
            type = type,
            prefix = "",
            output = collected,
            visited = mutableSetOf(),
        )
        return collected
    }

    private fun collectCommentHintsRecursive(
        type: Class<*>,
        prefix: String,
        output: MutableMap<String, String>,
        visited: MutableSet<Class<*>>,
    ) {
        if (!visited.add(type)) {
            return
        }

        type.declaredFields
            .filterNot { field ->
                field.isSynthetic || Modifier.isStatic(field.modifiers)
            }.forEach { field ->
                val path =
                    if (prefix.isEmpty()) {
                        field.name
                    } else {
                        "$prefix.${field.name}"
                    }

                val comment = field.getAnnotation(Comment::class.java)?.value?.trim().orEmpty()
                if (comment.isNotEmpty()) {
                    output[path] = comment
                }

                if (field.type.isAnnotationPresent(ConfigSerializable::class.java)) {
                    collectCommentHintsRecursive(
                        type = field.type,
                        prefix = path,
                        output = output,
                        visited = visited,
                    )
                }
            }

        visited.remove(type)
    }

    private fun injectComments(
        lines: List<String>,
        comments: Map<String, String>,
    ): List<String> {
        if (lines.isEmpty()) {
            return lines
        }

        val output = ArrayList<String>(lines.size + comments.size * 2)
        val keyStack = ArrayDeque<Pair<Int, String>>()
        val keyPattern = Regex("""^(\s*)([A-Za-z0-9_-]+):(?:\s*.*)?$""")

        lines.forEach { line ->
            val match = keyPattern.matchEntire(line)
            if (match == null) {
                output += line
                return@forEach
            }

            val indent = match.groupValues[1].length
            val key = match.groupValues[2]
            while (keyStack.isNotEmpty() && indent <= keyStack.last().first) {
                keyStack.removeLast()
            }

            val path =
                if (keyStack.isEmpty()) {
                    key
                } else {
                    "${keyStack.last().second}.$key"
                }
            val comment = comments[path]
            if (!comment.isNullOrBlank() && !hasInlineCommentBlock(output, indent)) {
                val prefix = " ".repeat(indent)
                comment
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { commentLine ->
                        output += "$prefix# $commentLine"
                    }
            }

            output += line
            if (line.trimEnd().endsWith(":")) {
                keyStack.addLast(indent to path)
            }
        }

        return output
    }

    private fun hasInlineCommentBlock(
        output: List<String>,
        indent: Int,
    ): Boolean {
        var index = output.lastIndex
        while (index >= 0) {
            val line = output[index]
            if (line.isBlank()) {
                index -= 1
                continue
            }
            return line.trimStart().startsWith("#") && line.takeWhile { it == ' ' }.length == indent
        }
        return false
    }
}
