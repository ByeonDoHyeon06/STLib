package studio.singlethread.lib.framework.core.text

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MiniMessageTextServiceTest {
    private val service = MiniMessageTextService()

    @Test
    fun `parse should normalize camel case placeholder key`() {
        val component = service.parse("<main_class>", mapOf("mainClass" to "studio.singlethread.lib.STLib"))
        assertEquals(Component.text("studio.singlethread.lib.STLib"), component)
    }

    @Test
    fun `parse should ignore blank placeholder keys without throwing`() {
        assertDoesNotThrow {
            service.parse("<green>ok</green>", mapOf("   " to "value"))
        }
    }
}

