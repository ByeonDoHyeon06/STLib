package studio.singlethread.lib.configurate.service

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import studio.singlethread.lib.framework.api.config.ConfigService
import java.nio.file.Files
import java.nio.file.Path

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
            }
            return loaded
        }

        val defaultValue = createDefault(type)
        node.set(type, defaultValue)
        loader.save(node)
        return defaultValue
    }

    override fun <T : Any> save(path: Path, value: T, type: Class<T>) {
        val loader = loader(path)
        val node = loader.load()
        node.set(type, value)
        loader.save(node)
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

        return runCatching {
            Files.size(path) == 0L
        }.getOrDefault(false)
    }
}
