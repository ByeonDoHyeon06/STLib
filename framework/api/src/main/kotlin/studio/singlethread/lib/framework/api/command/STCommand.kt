package studio.singlethread.lib.framework.api.command

/**
 * Class-based command definition with plugin-owner injection.
 *
 * Designed to coexist with command DSL:
 * - DSL: `command("test") { ... }`
 * - Class: `command<TestCommand>()`
 */
abstract class STCommand<out P : Any>(
    protected val plugin: P,
) {
    abstract val name: String

    open val description: String = ""
    open val permission: String? = null
    open val senderConstraint: CommandSenderConstraint = CommandSenderConstraint.ANY

    open fun aliases(): List<String> = emptyList()

    open fun requirement(): CommandRequirement? = null

    protected abstract fun build(builder: CommandDslBuilder)

    fun definition(): CommandDefinition {
        val owner = this
        return commandDsl(name) {
            description = owner.description
            permission = owner.permission

            val aliasValues = owner.aliases().map(String::trim).filter(String::isNotEmpty)
            if (aliasValues.isNotEmpty()) {
                aliases(*aliasValues.toTypedArray())
            }

            sender(owner.senderConstraint)
            owner.requirement()?.let(::requires)
            owner.build(this)
        }
    }
}

