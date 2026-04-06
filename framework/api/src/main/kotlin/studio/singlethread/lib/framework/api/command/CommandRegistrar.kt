package studio.singlethread.lib.framework.api.command

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component

interface CommandRegistrar {
    fun register(command: CommandDefinition)
}

enum class CommandSenderConstraint {
    ANY,
    PLAYER_ONLY,
    CONSOLE_ONLY,
}

fun interface CommandRequirement {
    fun test(context: CommandRequirementContext): Boolean
}

data class CommandRequirementContext(
    val senderName: String,
    val isPlayer: Boolean,
    val sender: Any?,
)

enum class CommandArgumentKind {
    STRING,
    GREEDY_STRING,
    INT,
    DOUBLE,
    BOOLEAN,
    PLAYER,
    OFFLINE_PLAYER,
    WORLD,
    LOCATION,
}

fun interface CommandSuggestionProvider {
    fun suggest(context: CommandSuggestionContext): Collection<String>
}

data class CommandSuggestionContext(
    val senderName: String,
    val isPlayer: Boolean,
    val sender: Any?,
    val previousArguments: Map<String, Any?>,
    val previousRawArguments: Map<String, String>,
    val currentInput: String,
    val currentArgument: String,
)

data class CommandArgumentSpec(
    val name: String,
    val kind: CommandArgumentKind,
    val optional: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val dynamicSuggestions: CommandSuggestionProvider? = null,
) {
    init {
        require(name.isNotBlank()) { "argument name must not be blank" }
    }
}

data class CommandNodeSpec(
    val literal: String,
    val permission: String? = null,
    val aliases: List<String> = emptyList(),
    val senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY,
    val requirement: CommandRequirement? = null,
    val arguments: List<CommandArgumentSpec> = emptyList(),
    val children: List<CommandNodeSpec> = emptyList(),
    val executor: CommandExecutor? = null,
) {
    init {
        require(literal.isNotBlank()) { "literal must not be blank" }
    }
}

data class CommandDefinition(
    val name: String,
    val description: String = "",
    val permission: String? = null,
    val aliases: List<String> = emptyList(),
    val senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY,
    val requirement: CommandRequirement? = null,
    val arguments: List<CommandArgumentSpec> = emptyList(),
    val children: List<CommandNodeSpec> = emptyList(),
    val executor: CommandExecutor? = null,
) {
    init {
        require(name.isNotBlank()) { "command name must not be blank" }
    }

    fun executableEndpointCount(): Int {
        val root = if (executor != null) 1 else 0
        return root + children.sumOf(CommandNodeSpec::executableEndpointCount)
    }
}

private fun CommandNodeSpec.executableEndpointCount(): Int {
    val self = if (executor != null) 1 else 0
    return self + children.sumOf(CommandNodeSpec::executableEndpointCount)
}

fun interface CommandExecutor {
    fun execute(context: CommandContext)
}

fun interface CommandResponseChannel {
    fun send(message: String)
}

data class CommandContext(
    val senderName: String,
    val isPlayer: Boolean,
    val sender: Any? = null,
    val audience: Audience? = null,
    val arguments: Map<String, Any?> = emptyMap(),
    val rawArguments: Map<String, String> = emptyMap(),
    val fullInput: String = "",
) {
    var responder: CommandResponseChannel? = null

    fun reply(message: String) {
        responder?.send(message)
    }

    fun reply(component: Component) {
        audience?.sendMessage(component)
    }

    inline fun <reified T> argument(name: String): T? {
        return arguments[name] as? T
    }

    fun <T : Any> argument(
        name: String,
        type: Class<T>,
    ): T? {
        val value = arguments[name] ?: return null
        val targetType = wrapperType(type)
        if (!targetType.isInstance(value)) {
            return null
        }
        @Suppress("UNCHECKED_CAST")
        return targetType.cast(value) as T
    }

    fun stringArgument(name: String): String? {
        return argument(name)
    }

    fun intArgument(name: String): Int? {
        return argument(name)
    }

    fun doubleArgument(name: String): Double? {
        return argument(name)
    }

    fun booleanArgument(name: String): Boolean? {
        return argument(name)
    }

    fun rawArgument(name: String): String? {
        return rawArguments[name]
    }

    private fun <T : Any> wrapperType(type: Class<T>): Class<*> {
        if (!type.isPrimitive) {
            return type
        }
        return when (type) {
            java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
            java.lang.Byte.TYPE -> Byte::class.javaObjectType
            java.lang.Short.TYPE -> Short::class.javaObjectType
            java.lang.Integer.TYPE -> Int::class.javaObjectType
            java.lang.Long.TYPE -> Long::class.javaObjectType
            java.lang.Float.TYPE -> Float::class.javaObjectType
            java.lang.Double.TYPE -> Double::class.javaObjectType
            java.lang.Character.TYPE -> Char::class.javaObjectType
            else -> type
        }
    }
}
