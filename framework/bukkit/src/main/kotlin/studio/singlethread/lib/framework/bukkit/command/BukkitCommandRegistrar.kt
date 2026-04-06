package studio.singlethread.lib.framework.bukkit.command

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.SuggestionInfo
import dev.jorel.commandapi.arguments.Argument
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.BooleanArgument
import dev.jorel.commandapi.arguments.DoubleArgument
import dev.jorel.commandapi.arguments.GreedyStringArgument
import dev.jorel.commandapi.arguments.IntegerArgument
import dev.jorel.commandapi.arguments.LocationArgument
import dev.jorel.commandapi.arguments.LocationType
import dev.jorel.commandapi.arguments.OfflinePlayerArgument
import dev.jorel.commandapi.arguments.PlayerArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.arguments.WorldArgument
import dev.jorel.commandapi.executors.CommandArguments
import dev.jorel.commandapi.executors.CommandExecutor as ApiCommandExecutor
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.command.CommandArgumentKind
import studio.singlethread.lib.framework.api.command.CommandArgumentSpec
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandDefinitionValidator
import studio.singlethread.lib.framework.api.command.CommandNodeSpec
import studio.singlethread.lib.framework.api.command.CommandRegistrar
import studio.singlethread.lib.framework.api.command.CommandResponseChannel
import studio.singlethread.lib.framework.api.command.CommandRequirement
import studio.singlethread.lib.framework.api.command.CommandRequirementContext
import studio.singlethread.lib.framework.api.command.CommandSenderConstraint
import studio.singlethread.lib.framework.api.command.CommandSuggestionContext
import java.util.function.Predicate

class BukkitCommandRegistrar(
    private val plugin: JavaPlugin,
    private val debugEnabled: () -> Boolean = { false },
) : CommandRegistrar {
    private val miniMessage: MiniMessage = MiniMessage.miniMessage()
    private val compiler =
        BukkitCommandCompiler { message, error ->
            if (!debugEnabled()) {
                return@BukkitCommandCompiler
            }
            if (error == null) {
                plugin.logger.info("[debug] $message")
            } else {
                plugin.logger.info("[debug] $message (${error.message ?: error::class.simpleName})")
            }
        }

    override fun register(command: CommandDefinition) {
        compiler.compile(command, ::commandContext, ::suggestionContext).register(plugin)
    }

    private fun commandContext(
        sender: CommandSender,
        commandArgs: CommandArguments,
    ): CommandContext {
        return CommandContext(
            senderName = sender.name,
            isPlayer = sender is Player,
            sender = sender,
            audience = sender as? Audience,
            arguments = commandArgs.argsMap(),
            rawArguments = commandArgs.rawArgsMap(),
            fullInput = commandArgs.fullInput(),
        ).also { commandContext ->
            commandContext.responder = CommandResponseChannel { message -> sendReply(sender, message) }
        }
    }

    private fun suggestionContext(info: SuggestionInfo<CommandSender>): CommandSuggestionContext {
        val sender = info.sender()
        val previousArgs = info.previousArgs()
        return CommandSuggestionContext(
            senderName = sender.name,
            isPlayer = sender is Player,
            sender = sender,
            previousArguments = previousArgs.argsMap(),
            previousRawArguments = previousArgs.rawArgsMap(),
            currentInput = info.currentInput(),
            currentArgument = info.currentArg(),
        )
    }

    private fun sendReply(
        sender: CommandSender,
        message: String,
    ) {
        val audience = sender as? Audience
        if (audience == null) {
            sender.sendMessage(message)
            return
        }

        runCatching {
            audience.sendMessage(miniMessage.deserialize(message))
        }.onFailure {
            sender.sendMessage(message)
        }
    }
}

internal class BukkitCommandCompiler(
    private val warn: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {
    fun compile(
        definition: CommandDefinition,
        contextFactory: (CommandSender, CommandArguments) -> CommandContext,
        suggestionContextFactory: (SuggestionInfo<CommandSender>) -> CommandSuggestionContext,
    ): CommandAPICommand {
        validateDefinition(definition)
        var command = CommandAPICommand(definition.name)
        command = applyCommandMeta(
            command = command,
            description = definition.description,
            permission = definition.permission,
            aliases = definition.aliases,
            senderConstraint = definition.senderConstraint,
            requirement = definition.requirement,
        )
        command = applyArguments(command = command, arguments = definition.arguments, suggestionContextFactory = suggestionContextFactory)
        definition.executor?.let { rootExecutor ->
            command = command.executes(executor(rootExecutor, contextFactory))
        }

        definition.children.forEach { child ->
            command = command.withSubcommand(compileNode(child, contextFactory, suggestionContextFactory))
        }
        return command
    }

    private fun compileNode(
        node: CommandNodeSpec,
        contextFactory: (CommandSender, CommandArguments) -> CommandContext,
        suggestionContextFactory: (SuggestionInfo<CommandSender>) -> CommandSuggestionContext,
    ): CommandAPICommand {
        var command = CommandAPICommand(node.literal)
        command = applyCommandMeta(
            command = command,
            description = "",
            permission = node.permission,
            aliases = node.aliases,
            senderConstraint = node.senderConstraint,
            requirement = node.requirement,
        )
        command = applyArguments(command = command, arguments = node.arguments, suggestionContextFactory = suggestionContextFactory)
        node.executor?.let { nodeExecutor ->
            command = command.executes(executor(nodeExecutor, contextFactory))
        }
        node.children.forEach { child ->
            command = command.withSubcommand(compileNode(child, contextFactory, suggestionContextFactory))
        }
        return command
    }

    private fun executor(
        executor: studio.singlethread.lib.framework.api.command.CommandExecutor,
        contextFactory: (CommandSender, CommandArguments) -> CommandContext,
    ): ApiCommandExecutor {
        return ApiCommandExecutor { sender, commandArgs ->
            executor.execute(contextFactory(sender, commandArgs))
        }
    }

    private fun applyCommandMeta(
        command: CommandAPICommand,
        description: String,
        permission: String?,
        aliases: List<String>,
        senderConstraint: CommandSenderConstraint,
        requirement: CommandRequirement?,
    ): CommandAPICommand {
        var updated = command
        if (!permission.isNullOrBlank()) {
            updated = updated.withPermission(permission)
        }
        if (description.isNotBlank()) {
            updated = runCatching {
                val method = updated.javaClass.getMethod("withShortDescription", String::class.java)
                @Suppress("UNCHECKED_CAST")
                method.invoke(updated, description) as CommandAPICommand
            }.getOrDefault(updated)
        }
        val sanitizedAliases = aliases.map(String::trim).filter(String::isNotEmpty).distinct()
        if (sanitizedAliases.isNotEmpty()) {
            updated = updated.withAliases(*sanitizedAliases.toTypedArray())
        }

        val predicate = commandPredicate(senderConstraint, requirement)
        if (predicate != null) {
            updated = updated.withRequirement(predicate)
        }
        return updated
    }

    private fun applyArguments(
        command: CommandAPICommand,
        arguments: List<CommandArgumentSpec>,
        suggestionContextFactory: (SuggestionInfo<CommandSender>) -> CommandSuggestionContext,
    ): CommandAPICommand {
        var updated = command
        var optionalStarted = false
        arguments.forEach { argument ->
            if (argument.optional) {
                optionalStarted = true
            } else {
                require(!optionalStarted) {
                    "required argument '${argument.name}' cannot be declared after an optional argument"
                }
            }

            val commandApiArgument = toCommandApiArgument(argument, suggestionContextFactory)
            updated =
                if (argument.optional) {
                    updated.withOptionalArguments(commandApiArgument)
                } else {
                    updated.withArguments(commandApiArgument)
                }
        }
        return updated
    }

    private fun toCommandApiArgument(
        argument: CommandArgumentSpec,
        suggestionContextFactory: (SuggestionInfo<CommandSender>) -> CommandSuggestionContext,
    ): Argument<*> {
        val base: Argument<*> =
            when (argument.kind) {
                CommandArgumentKind.STRING -> StringArgument(argument.name)
                CommandArgumentKind.GREEDY_STRING -> GreedyStringArgument(argument.name)
                CommandArgumentKind.INT -> IntegerArgument(argument.name)
                CommandArgumentKind.DOUBLE -> DoubleArgument(argument.name)
                CommandArgumentKind.BOOLEAN -> BooleanArgument(argument.name)
                CommandArgumentKind.PLAYER -> PlayerArgument(argument.name)
                CommandArgumentKind.OFFLINE_PLAYER -> OfflinePlayerArgument(argument.name)
                CommandArgumentKind.WORLD -> WorldArgument(argument.name)
                CommandArgumentKind.LOCATION -> LocationArgument(argument.name, LocationType.PRECISE_POSITION)
            }

        val staticSuggestions = argument.suggestions.map(String::trim).filter(String::isNotEmpty)
        val dynamicSuggestions = argument.dynamicSuggestions
        if (staticSuggestions.isEmpty() && dynamicSuggestions == null) {
            return base
        }

        val mergedSuggestions =
            ArgumentSuggestions.stringCollection<CommandSender> { info ->
                val dynamic = resolveDynamicSuggestions(argument, suggestionContextFactory(info))
                (staticSuggestions + dynamic).distinct()
            }
        return runCatching {
            base.replaceSuggestions(mergedSuggestions)
        }.onFailure { error ->
            warn(
                "Suggestion override failed for argument '${argument.name}' (${argument.kind}); using CommandAPI defaults",
                error,
            )
        }.getOrDefault(base)
    }

    internal fun resolveDynamicSuggestions(
        argument: CommandArgumentSpec,
        context: CommandSuggestionContext,
    ): List<String> {
        val provider = argument.dynamicSuggestions ?: return emptyList()
        return runCatching {
            provider.suggest(context)
        }.onFailure { error ->
            warn(
                "Dynamic suggestion provider failed for argument '${argument.name}' (${argument.kind})",
                error,
            )
        }.getOrDefault(emptyList())
            .map(String::trim)
            .filter(String::isNotEmpty)
    }

    internal fun mappedArgumentClass(kind: CommandArgumentKind): Class<out Argument<*>> {
        return when (kind) {
            CommandArgumentKind.STRING -> StringArgument::class.java
            CommandArgumentKind.GREEDY_STRING -> GreedyStringArgument::class.java
            CommandArgumentKind.INT -> IntegerArgument::class.java
            CommandArgumentKind.DOUBLE -> DoubleArgument::class.java
            CommandArgumentKind.BOOLEAN -> BooleanArgument::class.java
            CommandArgumentKind.PLAYER -> PlayerArgument::class.java
            CommandArgumentKind.OFFLINE_PLAYER -> OfflinePlayerArgument::class.java
            CommandArgumentKind.WORLD -> WorldArgument::class.java
            CommandArgumentKind.LOCATION -> LocationArgument::class.java
        }
    }

    private fun commandPredicate(
        senderConstraint: CommandSenderConstraint,
        requirement: CommandRequirement?,
    ): Predicate<CommandSender>? {
        if (senderConstraint == CommandSenderConstraint.ANY && requirement == null) {
            return null
        }

        return Predicate { sender ->
            isSenderAllowed(senderConstraint, sender) &&
                (requirement?.test(
                    CommandRequirementContext(
                        senderName = sender.name,
                        isPlayer = sender is Player,
                        sender = sender,
                    ),
                ) ?: true)
        }
    }

    private fun isSenderAllowed(
        senderConstraint: CommandSenderConstraint,
        sender: CommandSender,
    ): Boolean {
        return when (senderConstraint) {
            CommandSenderConstraint.ANY -> true
            CommandSenderConstraint.PLAYER_ONLY -> sender is Player
            CommandSenderConstraint.CONSOLE_ONLY -> sender is ConsoleCommandSender
        }
    }

    private fun validateDefinition(definition: CommandDefinition) {
        CommandDefinitionValidator.validateDefinition(definition)
    }
}
