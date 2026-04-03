package studio.singlethread.lib.framework.api.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class CommandDslTest {
    @Test
    fun `command dsl should build nested literals and argument kinds`() {
        val definition =
            commandDsl("test") {
                description = "test command"
                permission = "test.use"
                aliases("t", "tt")
                sender(CommandSenderConstraint.ANY)

                literal("show") {
                    string("x")
                    string("y")
                    executes { }
                }

                literal("target") {
                    player("player")
                    world("world")
                    location("spawn")
                    executes { }
                }

                executes { }
            }

        assertEquals("test command", definition.description)
        assertEquals("test.use", definition.permission)
        assertEquals(listOf("t", "tt"), definition.aliases)
        assertEquals(3, definition.executableEndpointCount())
        assertEquals(2, definition.children.size)

        val show = definition.children.first { it.literal == "show" }
        assertEquals(2, show.arguments.size)
        assertEquals(CommandArgumentKind.STRING, show.arguments[0].kind)
        assertEquals(CommandArgumentKind.STRING, show.arguments[1].kind)

        val target = definition.children.first { it.literal == "target" }
        assertEquals(CommandArgumentKind.PLAYER, target.arguments[0].kind)
        assertEquals(CommandArgumentKind.WORLD, target.arguments[1].kind)
        assertEquals(CommandArgumentKind.LOCATION, target.arguments[2].kind)
    }

    @Test
    fun `command dsl should support nested subcommands recursively`() {
        val definition =
            commandDsl("root") {
                literal("admin") {
                    literal("cache") {
                        literal("clear") {
                            executes { }
                        }
                    }
                }
            }

        val admin = definition.children.first()
        val cache = admin.children.first()
        val clear = cache.children.first()

        assertEquals("admin", admin.literal)
        assertEquals("cache", cache.literal)
        assertEquals("clear", clear.literal)
        assertNotNull(clear.executor)
    }

    @Test
    fun `command dsl should represent show hide clear command set`() {
        val definition =
            commandDsl("test") {
                literal("show") {
                    string("x")
                    string("y")
                    executes { }
                }
                literal("hide") {
                    executes { }
                }
                literal("clear") {
                    executes { }
                }
            }

        assertEquals(3, definition.children.size)
        assertEquals(3, definition.executableEndpointCount())
        assertEquals(listOf("show", "hide", "clear"), definition.children.map { it.literal })
    }

    @Test
    fun `command dsl should support dynamic suggestions`() {
        val provider =
            CommandSuggestionProvider { context ->
                if (context.currentArgument.startsWith("a")) listOf("alpha") else emptyList()
            }

        val definition =
            commandDsl("demo") {
                string("key", dynamicSuggestions = provider)
                executes { }
            }

        val argument = definition.arguments.first()
        assertNotNull(argument.dynamicSuggestions)
        assertEquals(CommandArgumentKind.STRING, argument.kind)
    }

    @Test
    fun `command dsl should fail when required argument appears after optional`() {
        assertThrows(IllegalArgumentException::class.java) {
            commandDsl("invalid") {
                string("optional", optional = true)
                int("required")
                executes { }
            }
        }
    }

    @Test
    fun `command dsl should fail when node required argument appears after optional`() {
        assertThrows(IllegalArgumentException::class.java) {
            commandDsl("invalid-node") {
                literal("show") {
                    string("optional", optional = true)
                    int("required")
                    executes { }
                }
            }
        }
    }

    @Test
    fun `command dsl should fail when command has no executable endpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            commandDsl("empty") {
                literal("show") {
                    string("target")
                }
            }
        }
    }

    @Test
    fun `command dsl should fail when sibling literals or aliases collide`() {
        assertThrows(IllegalArgumentException::class.java) {
            commandDsl("demo") {
                literal("show") {
                    aliases("s")
                    executes { }
                }
                literal("s") {
                    executes { }
                }
            }
        }
    }

    @Test
    fun `command dsl should fail when alias duplicates its primary token`() {
        assertThrows(IllegalArgumentException::class.java) {
            commandDsl("demo") {
                aliases("Demo")
                executes { }
            }
        }
    }

    @Test
    fun `command dsl should allow nullable permission and requirement`() {
        val definition =
            commandDsl("demo") {
                executes { }
            }

        assertNull(definition.permission)
        assertNull(definition.requirement)
    }
}
