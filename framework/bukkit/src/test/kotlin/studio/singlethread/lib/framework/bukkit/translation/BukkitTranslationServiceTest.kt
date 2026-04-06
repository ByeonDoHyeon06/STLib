package studio.singlethread.lib.framework.bukkit.translation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.singlethread.lib.configurate.service.ConfigurateConfigService
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class BukkitTranslationServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `translate should resolve locale chain and normalize locale`() {
        val configService = ConfigurateConfigService()
        val configPath = pluginRoot().resolve("config/translation.yml")
        val settings = TranslationFileSettings().also {
            it.defaultLocale = "ko_kr"
            it.fallbackLocale = "ja_jp"
        }
        configService.save(configPath, settings, TranslationFileSettings::class.java)

        writeBundle(
            locale = "ko_kr",
            content =
                """
                example:
                  welcome: "<green>안녕 {player}"
                """.trimIndent(),
        )
        writeBundle(
            locale = "en_us",
            content =
                """
                example:
                  fallback: "<yellow>Hello fallback"
                """.trimIndent(),
        )

        val service = newService(configService, testLogger())

        assertEquals(
            "<green>안녕 Kim",
            service.translate("example.welcome", "ko-KR", mapOf("player" to "Kim")),
        )
        assertEquals(
            "<yellow>Hello fallback",
            service.translate("example.fallback", "fr_fr"),
        )
    }

    @Test
    fun `missing key should return marker and warn once`() {
        val configService = ConfigurateConfigService()
        val logger = testLogger()
        val recorder = RecordingHandler()
        logger.addHandler(recorder)

        val service = newService(configService, logger)

        assertEquals("!example.missing!", service.translate("example.missing", "de_de"))
        assertEquals("!example.missing!", service.translate("example.missing", "de_de"))

        val warnings = recorder.records
            .filter { it.level == Level.WARNING }
            .count { it.message.contains("Missing translation key 'example.missing'") }
        assertEquals(1, warnings)
    }

    @Test
    fun `reload should reset missing key warning cache`() {
        val configService = ConfigurateConfigService()
        val logger = testLogger()
        val recorder = RecordingHandler()
        logger.addHandler(recorder)
        val service = newService(configService, logger)

        assertEquals("!example.reload_missing!", service.translate("example.reload_missing", "en_us"))
        val firstWarnings =
            recorder.records
                .filter { it.level == Level.WARNING }
                .count { it.message.contains("Missing translation key 'example.reload_missing'") }
        assertEquals(1, firstWarnings)

        service.reload()
        assertEquals("!example.reload_missing!", service.translate("example.reload_missing", "en_us"))

        val warningsAfterReload =
            recorder.records
                .filter { it.level == Level.WARNING }
                .count { it.message.contains("Missing translation key 'example.reload_missing'") }
        assertEquals(2, warningsAfterReload)
    }

    @Test
    fun `reload should create base files and apply updated bundle`() {
        val configService = ConfigurateConfigService()
        val service = newService(configService, testLogger())

        assertTrue(Files.exists(pluginRoot().resolve("config/translation.yml")))
        assertTrue(Files.exists(pluginRoot().resolve("translation/en_us.yml")))

        writeBundle(
            locale = "en_us",
            content =
                """
                example:
                  status: "before"
                """.trimIndent(),
        )
        service.reload()
        assertEquals("before", service.translate("example.status", "en_us"))

        writeBundle(
            locale = "en_us",
            content =
                """
                example:
                  status: "after"
                """.trimIndent(),
        )
        service.reload()
        assertEquals("after", service.translate("example.status", "en_us"))
    }

    private fun newService(
        configService: ConfigurateConfigService,
        logger: Logger,
    ): BukkitTranslationService {
        return BukkitTranslationService(
            configService = configService,
            configPath = pluginRoot().resolve("config/translation.yml"),
            translationDirectory = pluginRoot().resolve("translation"),
            logger = logger,
        )
    }

    private fun writeBundle(
        locale: String,
        content: String,
    ) {
        val path = pluginRoot().resolve("translation/$locale.yml")
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }

    private fun pluginRoot(): Path {
        return tempDir.resolve("SamplePlugin")
    }

    private fun testLogger(): Logger {
        return Logger.getLogger("BukkitTranslationServiceTest-${System.nanoTime()}").apply {
            useParentHandlers = false
        }
    }

    private class RecordingHandler : Handler() {
        val records: MutableList<LogRecord> = mutableListOf()

        override fun publish(record: LogRecord) {
            records += record
        }

        override fun flush() = Unit

        override fun close() = Unit
    }
}
