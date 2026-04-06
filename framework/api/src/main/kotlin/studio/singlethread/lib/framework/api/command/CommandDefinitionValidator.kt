package studio.singlethread.lib.framework.api.command

object CommandDefinitionValidator {
    fun sanitizeAliases(values: List<String>): List<String> {
        val deduped = linkedMapOf<String, String>()
        values
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { alias ->
                deduped.putIfAbsent(normalizeToken(alias), alias)
            }
        return deduped.values.toList()
    }

    fun validateArgumentOrdering(
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

    fun validateOwnAliases(
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

    fun validateSiblingTokenUniqueness(
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

    fun validateDefinition(definition: CommandDefinition) {
        validateOwnAliases(
            primaryToken = definition.name,
            aliases = definition.aliases,
            scope = "command '${definition.name}'",
        )
        validateSiblingTokenUniqueness(
            siblings = definition.children,
            scope = "command '${definition.name}'",
        )
        definition.children.forEach { child ->
            validateNode(node = child, path = "${definition.name} ${child.literal}")
        }
    }

    fun validateNode(
        node: CommandNodeSpec,
        path: String,
    ) {
        validateOwnAliases(
            primaryToken = node.literal,
            aliases = node.aliases,
            scope = "node '$path'",
        )
        validateSiblingTokenUniqueness(node.children, scope = "node '$path'")
        node.children.forEach { child ->
            validateNode(child, "$path ${child.literal}")
        }
    }

    private fun normalizeToken(value: String): String {
        return value.trim().lowercase()
    }
}
