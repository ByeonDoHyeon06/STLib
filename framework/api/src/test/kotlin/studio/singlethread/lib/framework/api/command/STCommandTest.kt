package studio.singlethread.lib.framework.api.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class STCommandTest {
    @Test
    fun `stcommand should build command definition through dsl`() {
        val command = DemoCommand(plugin = DemoPlugin(name = "UnitPlugin"))

        val definition = command.definition()

        assertEquals("demo", definition.name)
        assertEquals("demo command", definition.description)
        assertEquals("demo.use", definition.permission)
        assertEquals(listOf("d"), definition.aliases)
        assertEquals(CommandSenderConstraint.PLAYER_ONLY, definition.senderConstraint)
        assertNotNull(definition.requirement)
        assertEquals(2, definition.executableEndpointCount())
        assertEquals(1, definition.children.size)
        assertEquals("show", definition.children.first().literal)
    }

    private data class DemoPlugin(
        val name: String,
    )

    private class DemoCommand(
        plugin: DemoPlugin,
    ) : STCommand<DemoPlugin>(plugin) {
        override val name: String = "demo"
        override val description: String = "demo command"
        override val permission: String = "demo.use"
        override val senderConstraint: CommandSenderConstraint = CommandSenderConstraint.PLAYER_ONLY

        override fun aliases(): List<String> {
            return listOf("d")
        }

        override fun requirement(): CommandRequirement {
            return CommandRequirement { context -> context.senderName.startsWith(plugin.name.first().toString()) }
        }

        override fun build(builder: CommandDslBuilder) {
            builder.literal("show") {
                string("target")
                executes { }
            }
            builder.executes { }
        }
    }
}

