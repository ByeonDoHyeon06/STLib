package studio.singlethread.lib.framework.api.command

class CommandDslBuilder internal constructor(
    private val name: String,
) {
    var description: String = ""
    var permission: String? = null

    private val aliases = linkedSetOf<String>()
    private var senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY
    private var requirement: CommandRequirement? = null
    private val arguments = mutableListOf<CommandArgumentSpec>()
    private val children = mutableListOf<CommandNodeSpec>()
    private var executor: CommandExecutor? = null

    fun aliases(vararg values: String) {
        aliases += sanitizeAliases(values.asList())
    }

    fun sender(constraint: CommandSenderConstraint) {
        senderConstraint = constraint
    }

    fun requires(requirement: CommandRequirement) {
        this.requirement = requirement
    }

    fun requires(predicate: (CommandRequirementContext) -> Boolean) {
        requires(CommandRequirement(predicate))
    }

    fun argument(
        kind: CommandArgumentKind,
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        arguments +=
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

    fun literal(
        literal: String,
        builder: CommandNodeDslBuilder.() -> Unit,
    ) {
        val nodeBuilder = CommandNodeDslBuilder(path = "$name $literal", literal = literal)
        nodeBuilder.builder()
        children += nodeBuilder.build()
    }

    fun executes(executor: CommandExecutor) {
        this.executor = executor
    }

    fun executes(executor: (CommandContext) -> Unit) {
        this.executor = CommandExecutor(executor)
    }

    internal fun build(): CommandDefinition {
        validateArgumentOrdering(arguments, scope = "command '$name'")
        validateOwnAliases(primaryToken = name, aliases = aliases.toList(), scope = "command '$name'")

        val definition =
            CommandDefinition(
                name = name,
                description = description,
                permission = permission,
                aliases = aliases.toList(),
                senderConstraint = senderConstraint,
                requirement = requirement,
                arguments = arguments.toList(),
                children = children.toList(),
                executor = executor,
            )

        require(definition.executableEndpointCount() > 0) {
            "Command '$name' must have at least one executes { ... } endpoint"
        }
        validateSiblingTokenUniqueness(definition.children, scope = "command '$name'")

        return definition
    }
}

class CommandNodeDslBuilder internal constructor(
    private val path: String,
    private val literal: String,
) {
    var permission: String? = null

    private val aliases = linkedSetOf<String>()
    private var senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY
    private var requirement: CommandRequirement? = null
    private val arguments = mutableListOf<CommandArgumentSpec>()
    private val children = mutableListOf<CommandNodeSpec>()
    private var executor: CommandExecutor? = null

    fun aliases(vararg values: String) {
        aliases += sanitizeAliases(values.asList())
    }

    fun sender(constraint: CommandSenderConstraint) {
        senderConstraint = constraint
    }

    fun requires(requirement: CommandRequirement) {
        this.requirement = requirement
    }

    fun requires(predicate: (CommandRequirementContext) -> Boolean) {
        requires(CommandRequirement(predicate))
    }

    fun argument(
        kind: CommandArgumentKind,
        name: String,
        optional: Boolean = false,
        suggestions: List<String> = emptyList(),
        dynamicSuggestions: CommandSuggestionProvider? = null,
    ) {
        arguments +=
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

    fun literal(
        literal: String,
        builder: CommandNodeDslBuilder.() -> Unit,
    ) {
        val childPath = "$path $literal"
        val nodeBuilder = CommandNodeDslBuilder(path = childPath, literal = literal)
        nodeBuilder.builder()
        children += nodeBuilder.build()
    }

    fun executes(executor: CommandExecutor) {
        this.executor = executor
    }

    fun executes(executor: (CommandContext) -> Unit) {
        this.executor = CommandExecutor(executor)
    }

    internal fun build(): CommandNodeSpec {
        validateArgumentOrdering(arguments, scope = "node '$path'")
        validateOwnAliases(primaryToken = literal, aliases = aliases.toList(), scope = "node '$path'")

        val node =
            CommandNodeSpec(
                literal = literal,
                permission = permission,
                aliases = aliases.toList(),
                senderConstraint = senderConstraint,
                requirement = requirement,
                arguments = arguments.toList(),
                children = children.toList(),
                executor = executor,
            )

        require(node.executor != null || node.children.isNotEmpty()) {
            "Node '$path' must define executes { ... } or contain child literals"
        }
        validateSiblingTokenUniqueness(node.children, scope = "node '$path'")

        return node
    }
}

fun commandDsl(
    name: String,
    builder: CommandDslBuilder.() -> Unit,
): CommandDefinition {
    require(name.isNotBlank()) { "command name must not be blank" }
    val dslBuilder = CommandDslBuilder(name)
    dslBuilder.builder()
    return dslBuilder.build()
}

private fun validateArgumentOrdering(
    arguments: List<CommandArgumentSpec>,
    scope: String,
) {
    var optionalStarted = false
    arguments.forEach { argument ->
        if (argument.optional) {
            optionalStarted = true
            return@forEach
        }
        require(!optionalStarted) {
            "required argument '${argument.name}' cannot appear after optional argument in $scope"
        }
    }
}

private fun sanitizeAliases(values: List<String>): List<String> {
    val deduped = linkedMapOf<String, String>()
    values
        .map(String::trim)
        .filter(String::isNotEmpty)
        .forEach { alias ->
            deduped.putIfAbsent(normalizeToken(alias), alias)
        }
    return deduped.values.toList()
}

private fun validateOwnAliases(
    primaryToken: String,
    aliases: List<String>,
    scope: String,
) {
    val normalizedPrimary = normalizeToken(primaryToken)
    aliases.forEach { alias ->
        require(normalizeToken(alias) != normalizedPrimary) {
            "alias '$alias' in $scope duplicates primary token '$primaryToken'"
        }
    }
}

private fun validateSiblingTokenUniqueness(
    siblings: List<CommandNodeSpec>,
    scope: String,
) {
    val seen = linkedMapOf<String, String>()
    siblings.forEach { node ->
        val tokens = sequenceOf(node.literal) + node.aliases.asSequence()
        tokens.forEach { token ->
            val normalized = normalizeToken(token)
            val previousOwner = seen.putIfAbsent(normalized, node.literal)
            require(previousOwner == null) {
                "duplicate command token '$token' in $scope: '$previousOwner' conflicts with '${node.literal}'"
            }
        }
    }
}

private fun normalizeToken(value: String): String {
    return value.trim().lowercase()
}
