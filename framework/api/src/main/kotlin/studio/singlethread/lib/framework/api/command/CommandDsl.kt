package studio.singlethread.lib.framework.api.command

abstract class CommandNodeDslScope {
    private val state: MutableCommandNodeState = MutableCommandNodeState()

    var permission: String?
        get() = state.permission
        set(value) {
            state.permission = value
        }

    fun aliases(vararg values: String) {
        state.aliases += CommandDefinitionValidator.sanitizeAliases(values.asList())
    }

    fun sender(constraint: CommandSenderConstraint) {
        state.senderConstraint = constraint
    }

    fun requires(requirement: CommandRequirement) {
        state.requirement = requirement
    }

    fun argument(
        kind: CommandArgumentKind,
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        state.arguments +=
            CommandArgumentSpec(
                name = name,
                kind = kind,
                optional = optional,
                suggestions = suggestions.toList(),
                dynamicSuggestions = dynamicSuggestions,
            )
    }

    fun string(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.STRING,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun greedyString(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.GREEDY_STRING,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun int(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.INT,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun double(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.DOUBLE,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun boolean(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.BOOLEAN,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun player(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.PLAYER,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun offlinePlayer(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.OFFLINE_PLAYER,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun world(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.WORLD,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun location(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        argument(
            kind = CommandArgumentKind.LOCATION,
            name = name,
            optional = optional,
            suggestions = suggestions,
            dynamicSuggestions = dynamicSuggestions,
        )
    }

    fun executes(executor: CommandExecutor) {
        state.executor = executor
    }

    protected fun addChild(node: CommandNodeSpec) {
        state.children += node
    }

    protected fun snapshotPermission(): String? = state.permission

    protected fun snapshotAliases(): List<String> = state.aliases.toList()

    protected fun snapshotSenderConstraint(): CommandSenderConstraint = state.senderConstraint

    protected fun snapshotRequirement(): CommandRequirement? = state.requirement

    protected fun snapshotArguments(): List<CommandArgumentSpec> = state.arguments.toList()

    protected fun snapshotChildren(): List<CommandNodeSpec> = state.children.toList()

    protected fun snapshotExecutor(): CommandExecutor? = state.executor
}

class CommandDslBuilder internal constructor(
    private val name: String,
) : CommandNodeDslScope() {
    var description: String = ""

    fun literal(
        literal: String,
        definition: CommandNodeTree,
    ) {
        addChild(buildChildNode(path = name, literal = literal, definition = definition))
    }

    internal fun build(): CommandDefinition {
        val aliases = snapshotAliases()
        val arguments = snapshotArguments()
        val children = snapshotChildren()
        CommandDefinitionValidator.validateArgumentOrdering(arguments, scope = "command '$name'")
        CommandDefinitionValidator.validateOwnAliases(
            primaryToken = name,
            aliases = aliases,
            scope = "command '$name'",
        )

        val definition =
            CommandDefinition(
                name = name,
                description = description,
                permission = snapshotPermission(),
                aliases = aliases,
                senderConstraint = snapshotSenderConstraint(),
                requirement = snapshotRequirement(),
                arguments = arguments,
                children = children,
                executor = snapshotExecutor(),
            )

        require(definition.executableEndpointCount() > 0) {
            "Command '$name' must have at least one executes { ... } endpoint"
        }
        CommandDefinitionValidator.validateSiblingTokenUniqueness(children, scope = "command '$name'")

        return definition
    }
}

class CommandNodeDslBuilder internal constructor(
    val path: String,
    private val literal: String,
) : CommandNodeDslScope() {
    fun literal(
        literal: String,
        definition: CommandNodeTree,
    ) {
        addChild(buildChildNode(path = path, literal = literal, definition = definition))
    }

    internal fun build(): CommandNodeSpec {
        val aliases = snapshotAliases()
        val arguments = snapshotArguments()
        val children = snapshotChildren()
        CommandDefinitionValidator.validateArgumentOrdering(arguments, scope = "node '$path'")
        CommandDefinitionValidator.validateOwnAliases(
            primaryToken = literal,
            aliases = aliases,
            scope = "node '$path'",
        )

        val node =
            CommandNodeSpec(
                literal = literal,
                permission = snapshotPermission(),
                aliases = aliases,
                senderConstraint = snapshotSenderConstraint(),
                requirement = snapshotRequirement(),
                arguments = arguments,
                children = children,
                executor = snapshotExecutor(),
            )

        require(node.executor != null || node.children.isNotEmpty()) {
            "Node '$path' must define executes { ... } or contain child literals"
        }
        CommandDefinitionValidator.validateSiblingTokenUniqueness(children, scope = "node '$path'")

        return node
    }
}

fun commandDsl(
    name: String,
    tree: CommandTree,
): CommandDefinition {
    require(name.isNotBlank()) { "command name must not be blank" }
    val dslBuilder = CommandDslBuilder(name)
    with(tree) { dslBuilder.define() }
    return dslBuilder.build()
}

private data class MutableCommandNodeState(
    var permission: String? = null,
    var senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY,
    var requirement: CommandRequirement? = null,
    val aliases: LinkedHashSet<String> = linkedSetOf(),
    val arguments: MutableList<CommandArgumentSpec> = mutableListOf(),
    val children: MutableList<CommandNodeSpec> = mutableListOf(),
    var executor: CommandExecutor? = null,
)

private fun buildChildNode(
    path: String,
    literal: String,
    definition: CommandNodeTree,
): CommandNodeSpec {
    val childPath = "$path $literal"
    val nodeBuilder = CommandNodeDslBuilder(path = childPath, literal = literal)
    with(definition) { nodeBuilder.define() }
    return nodeBuilder.build()
}
