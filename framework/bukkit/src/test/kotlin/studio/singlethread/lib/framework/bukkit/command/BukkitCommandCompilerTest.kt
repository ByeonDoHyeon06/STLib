package studio.singlethread.lib.framework.bukkit.command

import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.arguments.BooleanArgument
import dev.jorel.commandapi.arguments.DoubleArgument
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.arguments.LocationArgument
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.arguments.WorldArgument
import dev.jorel.commandapi.executors.CommandArguments
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import studio.singlethread.lib.framework.api.command.CommandArgumentKind
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandNodeSpec
import studio.singlethread.lib.framework.api.command.CommandRequirement
import studio.singlethread.lib.framework.api.command.CommandRequirementContext
import studio.singlethread.lib.framework.api.command.CommandSenderConstraint
import studio.singlethread.lib.framework.api.command.CommandSuggestionContext
import studio.singlethread.lib.framework.api.command.CommandSuggestionProvider
import studio.singlethread.lib.framework.api.command.commandDsl

class BukkitCommandCompilerTest {
    private val compiler = BukkitCommandCompiler()
    private val contextFactory: (CommandSender, CommandArguments) -> CommandContext = { sender, args ->
        CommandContext(
            senderName = sender.name,
            isPlayer = sender is Player,
            sender = sender,
            arguments = args.argsMap(),
            rawArguments = args.rawArgsMap(),
            fullInput = args.fullInput(),
        )
    }
    private val suggestionContextFactory: (SuggestionInfo<CommandSender>) -> CommandSuggestionContext = { info ->
        CommandSuggestionContext(
            senderName = info.sender().name,
            isPlayer = info.sender() is Player,
            sender = info.sender(),
            previousArguments = info.previousArgs().argsMap(),
            previousRawArguments = info.previousArgs().rawArgsMap(),
            currentInput = info.currentInput(),
            currentArgument = info.currentArg(),
        )
    }

    @Test
    fun `compiler should map nested command tree into commandapi subcommands`() {
        val definition =
            commandDsl("test") {
                literal("show") {
                    string("x")
                    string("y")
                    executes { }
                }
                literal("admin") {
                    literal("clear") {
                        executes { }
                    }
                }
                executes { }
            }

        val compiled = compiler.compile(definition, contextFactory, suggestionContextFactory)
        val subcommands = compiled.getSubcommands()

        assertEquals(2, subcommands.size)
        val show = subcommands.first { it.getName() == "show" }
        val admin = subcommands.first { it.getName() == "admin" }
        assertEquals(2, show.getArguments().size)
        assertEquals(1, admin.getSubcommands().size)
        assertEquals("clear", admin.getSubcommands().first().getName())
    }

    @Test
    fun `compiler should map supported argument kinds`() {
        val dynamicSuggestions = CommandSuggestionProvider { _ -> listOf("dynamic") }
        assertEquals(StringArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.STRING))
        assertEquals(GreedyStringArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.GREEDY_STRING))
        assertEquals(IntegerArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.INT))
        assertEquals(DoubleArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.DOUBLE))
        assertEquals(BooleanArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.BOOLEAN))
        assertEquals(PlayerArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.PLAYER))
        assertEquals(OfflinePlayerArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.OFFLINE_PLAYER))
        assertEquals(WorldArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.WORLD))
        assertEquals(LocationArgument::class.java, compiler.mappedArgumentClass(CommandArgumentKind.LOCATION))

        assertSingleArgumentKind(CommandArgumentKind.STRING, StringArgument::class.java)
        assertSingleArgumentKind(CommandArgumentKind.GREEDY_STRING, GreedyStringArgument::class.java)
        assertSingleArgumentKind(CommandArgumentKind.INT, IntegerArgument::class.java)
        assertSingleArgumentKind(CommandArgumentKind.DOUBLE, DoubleArgument::class.java)
        assertSingleArgumentKind(CommandArgumentKind.BOOLEAN, BooleanArgument::class.java)

        val stringArgumentWithSuggestions =
            studio.singlethread.lib.framework.api.command.CommandArgumentSpec(
                name = "filter",
                kind = CommandArgumentKind.STRING,
                suggestions = listOf("static"),
                dynamicSuggestions = dynamicSuggestions,
            )
        val withSuggestions =
            compiler.compile(
                CommandDefinition(
                    name = "suggest",
                    arguments = listOf(stringArgumentWithSuggestions),
                    executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
                ),
                contextFactory,
                suggestionContextFactory,
            )
        assertTrue(withSuggestions.getArguments().first().getOverriddenSuggestions().isPresent)
    }

    @Test
    fun `compiler should combine sender constraint and requires predicate`() {
        val requirement = CommandRequirement { context: CommandRequirementContext ->
            context.senderName.startsWith("A")
        }
        val definition =
            CommandDefinition(
                name = "guarded",
                senderConstraint = CommandSenderConstraint.PLAYER_ONLY,
                requirement = requirement,
                executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
            )

        val compiled = compiler.compile(definition, contextFactory, suggestionContextFactory)
        val predicate = compiled.getRequirements()

        val allowedPlayer = Mockito.mock(Player::class.java)
        Mockito.`when`(allowedPlayer.name).thenReturn("Alex")

        val blockedPlayer = Mockito.mock(Player::class.java)
        Mockito.`when`(blockedPlayer.name).thenReturn("Bob")

        val console = Mockito.mock(ConsoleCommandSender::class.java)
        Mockito.`when`(console.name).thenReturn("Console")

        assertNotNull(predicate)
        assertTrue(predicate.test(allowedPlayer))
        assertFalse(predicate.test(blockedPlayer))
        assertFalse(predicate.test(console))
    }

    @Test
    fun `compiler should preserve aliases on root and child nodes`() {
        val definition =
            CommandDefinition(
                name = "origin",
                aliases = listOf("o"),
                children =
                    listOf(
                        CommandNodeSpec(
                            literal = "show",
                            aliases = listOf("s"),
                            executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
                        ),
                    ),
                executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
            )

        val compiled = compiler.compile(definition, contextFactory, suggestionContextFactory)
        assertEquals(listOf("o"), compiled.getAliases().toList())

        val child = compiled.getSubcommands().first()
        assertEquals(listOf("s"), child.getAliases().toList())
    }

    @Test
    fun `compiler should fail fast when sibling tokens collide`() {
        val invalid =
            CommandDefinition(
                name = "demo",
                children =
                    listOf(
                        CommandNodeSpec(
                            literal = "show",
                            aliases = listOf("s"),
                            executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
                        ),
                        CommandNodeSpec(
                            literal = "s",
                            executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
                        ),
                    ),
                executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
            )

        assertThrows(IllegalArgumentException::class.java) {
            compiler.compile(invalid, contextFactory, suggestionContextFactory)
        }
    }

    @Test
    fun `compiler should isolate dynamic suggestion provider failures`() {
        val warnings = mutableListOf<String>()
        val warningCompiler =
            BukkitCommandCompiler { message, _ ->
                warnings += message
            }
        val argument =
            studio.singlethread.lib.framework.api.command.CommandArgumentSpec(
                name = "filter",
                kind = CommandArgumentKind.STRING,
                suggestions = listOf("static"),
                dynamicSuggestions = CommandSuggestionProvider { throw IllegalStateException("boom") },
            )
        val context =
            CommandSuggestionContext(
                senderName = "Console",
                isPlayer = false,
                sender = null,
                previousArguments = emptyMap(),
                previousRawArguments = emptyMap(),
                currentInput = "",
                currentArgument = "f",
            )

        val resolved = warningCompiler.resolveDynamicSuggestions(argument, context)

        assertTrue(resolved.isEmpty())
        assertTrue(warnings.any { it.contains("Dynamic suggestion provider failed") })
    }

    private fun assertSingleArgumentKind(
        kind: CommandArgumentKind,
        expectedType: Class<*>,
    ) {
        val definition =
            CommandDefinition(
                name = "single-${kind.name.lowercase()}",
                arguments = listOf(studio.singlethread.lib.framework.api.command.CommandArgumentSpec("value", kind)),
                executor = studio.singlethread.lib.framework.api.command.CommandExecutor { },
            )
        val compiled = compiler.compile(definition, contextFactory, suggestionContextFactory)
        assertTrue(expectedType.isInstance(compiled.getArguments().first()))
    }
}
