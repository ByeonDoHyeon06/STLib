package studio.singlethread.lib.framework.api.command

private class CommandNodeDslSupport(
    private val state: MutableCommandNodeState,
) {
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
}

class CommandDslBuilder internal constructor(
    private val name: String,
) {
    var description: String = ""

    private val state: MutableCommandNodeState = MutableCommandNodeState()
    private val dslSupport: CommandNodeDslSupport = CommandNodeDslSupport(state)

    var permission: String?
        get() = dslSupport.permission
        set(value) {
            dslSupport.permission = value
        }

    fun aliases(vararg values: String) {
        dslSupport.aliases(*values)
    }

    fun sender(constraint: CommandSenderConstraint) {
        dslSupport.sender(constraint)
    }

    fun requires(requirement: CommandRequirement) {
        dslSupport.requires(requirement)
    }

    fun argument(
        kind: CommandArgumentKind,
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.argument(kind, name, optional, suggestions, dynamicSuggestions)
    }

    fun string(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.string(name, optional, suggestions, dynamicSuggestions)
    }

    fun greedyString(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.greedyString(name, optional, suggestions, dynamicSuggestions)
    }

    fun int(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.int(name, optional, suggestions, dynamicSuggestions)
    }

    fun double(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.double(name, optional, suggestions, dynamicSuggestions)
    }

    fun boolean(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.boolean(name, optional, suggestions, dynamicSuggestions)
    }

    fun player(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.player(name, optional, suggestions, dynamicSuggestions)
    }

    fun offlinePlayer(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.offlinePlayer(name, optional, suggestions, dynamicSuggestions)
    }

    fun world(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.world(name, optional, suggestions, dynamicSuggestions)
    }

    fun location(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.location(name, optional, suggestions, dynamicSuggestions)
    }

    fun executes(executor: CommandExecutor) {
        dslSupport.executes(executor)
    }

    fun literal(
        literal: String,
        definition: CommandNodeTree,
    ) {
        state.children += buildChildNode(path = name, literal = literal, definition = definition)
    }

    internal fun build(): CommandDefinition {
        val snapshot = state.snapshot()
        CommandDefinitionValidator.validateArgumentOrdering(snapshot.arguments, scope = "command '$name'")
        CommandDefinitionValidator.validateOwnAliases(
            primaryToken = name,
            aliases = snapshot.aliases,
            scope = "command '$name'",
        )

        val definition =
            CommandDefinition(
                name = name,
                description = description,
                permission = snapshot.permission,
                aliases = snapshot.aliases,
                senderConstraint = snapshot.senderConstraint,
                requirement = snapshot.requirement,
                arguments = snapshot.arguments,
                children = snapshot.children,
                executor = snapshot.executor,
            )

        require(definition.executableEndpointCount() > 0) {
            "Command '$name' must have at least one executes { ... } endpoint"
        }
        CommandDefinitionValidator.validateSiblingTokenUniqueness(definition.children, scope = "command '$name'")

        return definition
    }
}

class CommandNodeDslBuilder internal constructor(
    val path: String,
    private val literal: String,
) {
    private val state: MutableCommandNodeState = MutableCommandNodeState()
    private val dslSupport: CommandNodeDslSupport = CommandNodeDslSupport(state)

    var permission: String?
        get() = dslSupport.permission
        set(value) {
            dslSupport.permission = value
        }

    fun aliases(vararg values: String) {
        dslSupport.aliases(*values)
    }

    fun sender(constraint: CommandSenderConstraint) {
        dslSupport.sender(constraint)
    }

    fun requires(requirement: CommandRequirement) {
        dslSupport.requires(requirement)
    }

    fun argument(
        kind: CommandArgumentKind,
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.argument(kind, name, optional, suggestions, dynamicSuggestions)
    }

    fun string(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.string(name, optional, suggestions, dynamicSuggestions)
    }

    fun greedyString(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.greedyString(name, optional, suggestions, dynamicSuggestions)
    }

    fun int(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.int(name, optional, suggestions, dynamicSuggestions)
    }

    fun double(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.double(name, optional, suggestions, dynamicSuggestions)
    }

    fun boolean(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.boolean(name, optional, suggestions, dynamicSuggestions)
    }

    fun player(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.player(name, optional, suggestions, dynamicSuggestions)
    }

    fun offlinePlayer(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.offlinePlayer(name, optional, suggestions, dynamicSuggestions)
    }

    fun world(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.world(name, optional, suggestions, dynamicSuggestions)
    }

    fun location(
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        dslSupport.location(name, optional, suggestions, dynamicSuggestions)
    }

    fun executes(executor: CommandExecutor) {
        dslSupport.executes(executor)
    }

    fun literal(
        literal: String,
        definition: CommandNodeTree,
    ) {
        state.children += buildChildNode(path = path, literal = literal, definition = definition)
    }

    internal fun build(): CommandNodeSpec {
        val snapshot = state.snapshot()
        CommandDefinitionValidator.validateArgumentOrdering(snapshot.arguments, scope = "node '$path'")
        CommandDefinitionValidator.validateOwnAliases(
            primaryToken = literal,
            aliases = snapshot.aliases,
            scope = "node '$path'",
        )

        val node =
            CommandNodeSpec(
                literal = literal,
                permission = snapshot.permission,
                aliases = snapshot.aliases,
                senderConstraint = snapshot.senderConstraint,
                requirement = snapshot.requirement,
                arguments = snapshot.arguments,
                children = snapshot.children,
                executor = snapshot.executor,
            )

        require(node.executor != null || node.children.isNotEmpty()) {
            "Node '$path' must define executes { ... } or contain child literals"
        }
        CommandDefinitionValidator.validateSiblingTokenUniqueness(node.children, scope = "node '$path'")

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
) {
    fun snapshot(): CommandNodeStateSnapshot {
        return CommandNodeStateSnapshot(
            permission = permission,
            aliases = aliases.toList(),
            senderConstraint = senderConstraint,
            requirement = requirement,
            arguments = arguments.toList(),
            children = children.toList(),
            executor = executor,
        )
    }
}

private data class CommandNodeStateSnapshot(
    val permission: String?,
    val aliases: List<String>,
    val senderConstraint: CommandSenderConstraint,
    val requirement: CommandRequirement?,
    val arguments: List<CommandArgumentSpec>,
    val children: List<CommandNodeSpec>,
    val executor: CommandExecutor?,
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
