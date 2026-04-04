package studio.singlethread.lib.framework.bukkit.lifecycle

import studio.singlethread.lib.framework.api.command.CommandDefinition
import studio.singlethread.lib.framework.api.command.CommandExecutor
import studio.singlethread.lib.framework.api.command.CommandNodeSpec

internal object CommandMetricsInstrumenter {
    fun instrument(
        definition: CommandDefinition,
        onExecuted: () -> Unit,
    ): CommandDefinition {
        return definition.copy(
            executor = definition.executor?.let { instrumentExecutor(it, onExecuted) },
            children = definition.children.map { instrumentNode(it, onExecuted) },
        )
    }

    private fun instrumentNode(
        node: CommandNodeSpec,
        onExecuted: () -> Unit,
    ): CommandNodeSpec {
        return node.copy(
            executor = node.executor?.let { instrumentExecutor(it, onExecuted) },
            children = node.children.map { instrumentNode(it, onExecuted) },
        )
    }

    private fun instrumentExecutor(
        delegate: CommandExecutor,
        onExecuted: () -> Unit,
    ): CommandExecutor {
        return CommandExecutor { context ->
            onExecuted()
            delegate.execute(context)
        }
    }
}
