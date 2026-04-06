package studio.singlethread.lib.framework.bukkit.text

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import studio.singlethread.lib.framework.api.text.TextService

class BukkitTextParserTest {
    @Test
    fun `parse with sender should apply placeholder resolver when enabled`() {
        val textService = CapturingTextService()
        val parser = BukkitTextParser(textService, PrefixingPlaceholderResolver())
        val sender = mock(CommandSender::class.java)

        parser.parse(sender, "hello", mapOf("name" to "st"))

        assertEquals("[papi]hello", textService.lastMessage)
        assertEquals(mapOf("name" to "st"), textService.lastPlaceholders)
    }

    @Test
    fun `parse with sender should bypass placeholder resolver when disabled`() {
        val textService = CapturingTextService()
        val resolver = PrefixingPlaceholderResolver()
        val parser = BukkitTextParser(textService, resolver)
        val sender = mock(CommandSender::class.java)

        parser.parse(sender, "hello", mapOf("name" to "st"), usePlaceholderApi = false)

        assertEquals("hello", textService.lastMessage)
        assertTrue(resolver.wasInvoked.not())
    }

    @Test
    fun `parse without sender should not invoke placeholder resolver`() {
        val textService = CapturingTextService()
        val resolver = PrefixingPlaceholderResolver()
        val parser = BukkitTextParser(textService, resolver)

        parser.parse("hello", mapOf("name" to "st"))

        assertEquals("hello", textService.lastMessage)
        assertFalse(resolver.wasInvoked)
    }

    private class CapturingTextService : TextService {
        var lastMessage: String? = null
        var lastPlaceholders: Map<String, String>? = null

        override fun parse(message: String): Component {
            lastMessage = message
            lastPlaceholders = emptyMap()
            return Component.text(message)
        }

        override fun parse(
            message: String,
            placeholders: Map<String, String>,
        ): Component {
            lastMessage = message
            lastPlaceholders = placeholders
            return Component.text(message)
        }
    }

    private class PrefixingPlaceholderResolver : PlaceholderResolver {
        var wasInvoked: Boolean = false

        override fun resolve(
            sender: CommandSender?,
            message: String,
        ): String {
            wasInvoked = true
            return "[papi]$message"
        }
    }
}
