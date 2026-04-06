package studio.singlethread.lib.framework.bukkit.translation

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.verify
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser
import studio.singlethread.lib.framework.bukkit.text.NoopPlaceholderResolver
import studio.singlethread.lib.framework.core.text.MiniMessageTextService
import java.util.Locale

class BukkitTranslationFacadeTest {
    @Test
    fun `translate sender should prioritize player locale`() {
        val translationService = CapturingTranslationService()
        val facade = BukkitTranslationFacade(translationService, defaultTextParser())
        val player = Mockito.mock(Player::class.java)
        Mockito.`when`(player.locale()).thenReturn(Locale.forLanguageTag("ko-KR"))

        facade.translate(player, "example.welcome", mapOf("player" to "Kim"))

        assertEquals("ko-KR", translationService.lastLocale)
    }

    @Test
    fun `translate sender should use null locale for non-player senders`() {
        val translationService = CapturingTranslationService()
        val facade = BukkitTranslationFacade(translationService, defaultTextParser())
        val sender = Mockito.mock(CommandSender::class.java)

        facade.translate(sender, "example.welcome")

        assertEquals(null, translationService.lastLocale)
    }

    @Test
    fun `sendTranslated should delegate component send to sender`() {
        val translationService = CapturingTranslationService()
        val facade = BukkitTranslationFacade(translationService, defaultTextParser())
        val sender = Mockito.mock(CommandSender::class.java)

        facade.sendTranslated(sender, "example.welcome", mapOf("player" to "Kim"))

        verify(sender).sendMessage(any(Component::class.java))
    }

    @Test
    fun `reloadTranslations should delegate to translation service`() {
        val translationService = CapturingTranslationService()
        val facade = BukkitTranslationFacade(translationService, defaultTextParser())

        facade.reloadTranslations()

        assertEquals(1, translationService.reloadCount)
    }

    private class CapturingTranslationService : TranslationService {
        var lastLocale: String? = null
        var reloadCount: Int = 0

        override fun translate(
            key: String,
            locale: String?,
            placeholders: Map<String, String>,
        ): String {
            lastLocale = locale
            return "<green>Hello {player}"
        }

        override fun reload() {
            reloadCount++
        }
    }

    private fun defaultTextParser(): BukkitTextParser {
        return BukkitTextParser(
            textService = MiniMessageTextService(),
            placeholderResolver = NoopPlaceholderResolver,
        )
    }
}
