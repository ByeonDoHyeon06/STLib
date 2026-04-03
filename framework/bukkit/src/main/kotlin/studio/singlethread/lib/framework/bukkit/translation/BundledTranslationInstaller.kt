package studio.singlethread.lib.framework.bukkit.translation

import org.bukkit.plugin.java.JavaPlugin
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.jar.JarFile
import java.util.logging.Logger

object BundledTranslationInstaller {
    private const val RESOURCE_PREFIX = "translation/"
    private const val RESOURCE_SUFFIX = ".yml"

    fun installMissing(
        plugin: JavaPlugin,
        translationDirectory: Path,
        logger: Logger,
    ) {
        val resourcePaths = bundledResourcePaths(plugin, logger)
        if (resourcePaths.isEmpty()) {
            return
        }

        runCatching {
            Files.createDirectories(translationDirectory)
            resourcePaths.forEach { resourcePath ->
                val localeFile = localeFileName(resourcePath) ?: return@forEach
                val targetPath = translationDirectory.resolve(localeFile)
                if (Files.exists(targetPath)) {
                    return@forEach
                }

                plugin.getResource(resourcePath)?.use { input ->
                    Files.copy(input, targetPath, StandardCopyOption.REPLACE_EXISTING)
                } ?: logger.warning("Bundled translation resource '$resourcePath' could not be opened")
            }
        }.onFailure { error ->
            logger.warning("Failed to seed translation files from plugin resources: ${error.message}")
        }
    }

    private fun bundledResourcePaths(
        plugin: JavaPlugin,
        logger: Logger,
    ): List<String> {
        val jarPath = runCatching {
            Path.of(plugin.javaClass.protectionDomain.codeSource.location.toURI())
        }.getOrNull() ?: return emptyList()

        if (!Files.isRegularFile(jarPath)) {
            return emptyList()
        }

        return runCatching {
            JarFile(jarPath.toFile()).use { jar ->
                val entries = jar.entries()
                val paths = mutableListOf<String>()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) {
                        continue
                    }

                    val entryName = entry.name
                    if (!entryName.startsWith(RESOURCE_PREFIX) || !entryName.endsWith(RESOURCE_SUFFIX)) {
                        continue
                    }

                    if (localeFileName(entryName) == null) {
                        continue
                    }
                    paths += entryName
                }
                paths.sorted()
            }
        }.onFailure { error ->
            logger.warning("Failed to scan plugin jar for bundled translation files: ${error.message}")
        }.getOrElse { emptyList() }
    }

    private fun localeFileName(resourcePath: String): String? {
        if (!resourcePath.startsWith(RESOURCE_PREFIX) || !resourcePath.endsWith(RESOURCE_SUFFIX)) {
            return null
        }

        val relative = resourcePath.removePrefix(RESOURCE_PREFIX)
        if (relative.isBlank()) {
            return null
        }
        if (relative.contains('/') || relative.contains("..")) {
            return null
        }
        return relative
    }
}
