package studio.singlethread.lib.framework.api.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandContextTest {
    @Test
    fun `typed argument helpers should resolve matching values`() {
        val context =
            CommandContext(
                senderName = "tester",
                isPlayer = false,
                arguments =
                    mapOf(
                        "message" to "hello",
                        "count" to 3,
                        "ratio" to 1.5,
                        "flag" to true,
                    ),
                rawArguments = mapOf("message" to "hello"),
                fullInput = "/demo hello",
            )

        assertEquals("hello", context.stringArgument("message"))
        assertEquals(3, context.intArgument("count"))
        assertEquals(1.5, context.doubleArgument("ratio"))
        assertEquals(true, context.booleanArgument("flag"))
        assertEquals(3, context.argument("count", Int::class.java))
        assertEquals("hello", context.rawArgument("message"))
        assertEquals("/demo hello", context.fullInput)
    }

    @Test
    fun `typed argument helpers should return null on type mismatch`() {
        val context = CommandContext(senderName = "tester", isPlayer = false, arguments = mapOf("count" to 3))

        assertNull(context.stringArgument("count"))
        assertNull(context.intArgument("missing"))
        assertNull(context.argument("count", String::class.java))
    }
}
