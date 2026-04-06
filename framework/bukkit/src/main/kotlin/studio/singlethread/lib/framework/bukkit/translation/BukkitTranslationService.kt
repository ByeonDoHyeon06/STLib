package studio.singlethread.lib.framework.bukkit.translation

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.yaml.NodeStyle
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import studio.singlethread.lib.framework.api.config.ConfigService
import studio.singlethread.lib.framework.api.translation.TranslationService
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

class BukkitTranslationService(
    private val configService: ConfigService,
    private val configPath: Path,
    private val translationDirectory: Path,
    private val logger: Logger,
) : TranslationService {
    private val lock = Any()
    private val bundles = ConcurrentHashMap<String, Map<String, String>>()
    private val missingWarnings = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var settings = TranslationFileSettings()

    init {
        reload()
    }

    override fun translate(
        key: String,
        locale: String?,
        placeholders: Map<String, String>,
    ): String {
        val normalizedKey = key.trim()
        require(normalizedKey.isNotBlank()) { "translation key must not be blank" }

        val chain = fallbackChain(locale)
        val found = chain.asSequence()
            .mapNotNull { localeId -> bundle(localeId)[normalizedKey] }
            .firstOrNull()

        if (found != null) {
            return applyPlaceholders(found, placeholders)
        }

        warnMissingOnce(normalizedKey, chain)
        return "!$normalizedKey!"
    }

    override fun reload() {
        synchronized(lock) {
            val loadedSettings = configService.load(configPath, TranslationFileSettings::class.java)
            val normalizedSettings = normalizeSettings(loadedSettings)
            settings = normalizedSettings
            configService.save(configPath, normalizedSettings, TranslationFileSettings::class.java)

            Files.createDirectories(translationDirectory)
            bundles.clear()
            missingWarnings.clear()

            requiredLocales(normalizedSettings).forEach { locale ->
                ensureBundleFile(locale)
                bundles[locale] = loadBundle(locale)
            }

            discoverExistingLocales().forEach { locale ->
                bundles.putIfAbsent(locale, loadBundle(locale))
            }
        }
    }

    private fun fallbackChain(requestedLocale: String?): List<String> {
        val current = settings
        val chain = linkedSetOf<String>()

        val normalizedRequested = TranslationLocaleNormalizer.normalize(requestedLocale)
        if (normalizedRequested.isNotBlank()) {
            chain += normalizedRequested
        }

        chain += current.defaultLocale
        if (current.fallbackLocale.isNotBlank()) {
            chain += current.fallbackLocale
        }
        chain += EN_US

        return chain.toList()
    }

    private fun requiredLocales(settings: TranslationFileSettings): Set<String> {
        val locales = linkedSetOf(settings.defaultLocale)
        if (settings.fallbackLocale.isNotBlank()) {
            locales += settings.fallbackLocale
        }
        locales += EN_US
        return locales
    }

    private fun normalizeSettings(source: TranslationFileSettings): TranslationFileSettings {
        val defaultLocale = TranslationLocaleNormalizer.normalize(source.defaultLocale).ifBlank { EN_US }
        val fallbackLocale = TranslationLocaleNormalizer.normalize(source.fallbackLocale)

        return TranslationFileSettings().also {
            it.defaultLocale = defaultLocale
            it.fallbackLocale = fallbackLocale
        }
    }

    private fun ensureBundleFile(locale: String) {
        val path = bundlePath(locale)
        if (Files.exists(path)) {
            return
        }

        Files.createDirectories(path.parent)
        Files.writeString(path, "")
    }

    private fun bundle(locale: String): Map<String, String> {
        return bundles.computeIfAbsent(locale) { loadBundle(it) }
    }

    private fun discoverExistingLocales(): Set<String> {
        if (!Files.exists(translationDirectory)) {
            return emptySet()
        }

        Files.newDirectoryStream(translationDirectory, "*.yml").use { stream ->
            return stream
                .map { file ->
                    file.fileName.toString().removeSuffix(".yml")
                }
                .map(TranslationLocaleNormalizer::normalize)
                .filter { it.isNotBlank() }
                .toSet()
        }
    }

    private fun loadBundle(locale: String): Map<String, String> {
        val path = bundlePath(locale)
        if (!Files.exists(path)) {
            return emptyMap()
        }

        val node = runCatching { loader(path).load() }
            .onFailure { error ->
                logger.warning("Failed to load translation bundle '$locale': ${error.message}")
            }
            .getOrNull()
            ?: return emptyMap()

        val flattened = linkedMapOf<String, String>()
        flatten(node, emptyList(), flattened)
        return flattened
    }

    private fun flatten(
        node: ConfigurationNode,
        path: List<String>,
        out: MutableMap<String, String>,
    ) {
        val childrenMap = node.childrenMap()
        if (childrenMap.isNotEmpty()) {
            childrenMap.forEach { (key, child) ->
                flatten(child, path + key.toString(), out)
            }
            return
        }

        val childrenList = node.childrenList()
        if (childrenList.isNotEmpty()) {
            return
        }

        val key = path.joinToString(".")
        if (key.isBlank()) {
            return
        }

        val value = node.string ?: node.raw()?.toString() ?: return
        out[key] = value
    }

    private fun bundlePath(locale: String): Path {
        return translationDirectory.resolve("$locale.yml")
    }

    private fun loader(path: Path): YamlConfigurationLoader {
        Files.createDirectories(path.parent ?: path.toAbsolutePath().parent)
        return YamlConfigurationLoader.builder()
            .path(path)
            .nodeStyle(NodeStyle.BLOCK)
            .build()
    }

    private fun applyPlaceholders(
        input: String,
        placeholders: Map<String, String>,
    ): String {
        if (placeholders.isEmpty()) {
            return input
        }

        var output = input
        placeholders.forEach { (key, value) ->
            output = output.replace("{$key}", value)
        }
        return output
    }

    private fun warnMissingOnce(key: String, chain: List<String>) {
        val signature = "$key|${chain.joinToString(">")}"
        if (!missingWarnings.add(signature)) {
            return
        }

        logger.warning(
            "Missing translation key '$key' for locale chain [${chain.joinToString(", ")}]",
        )
    }

    companion object {
        const val EN_US: String = "en_us"
    }
}
