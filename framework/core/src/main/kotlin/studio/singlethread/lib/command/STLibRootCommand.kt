package studio.singlethread.lib.command

import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.command.CommandDslBuilder
import studio.singlethread.lib.framework.api.command.STCommand
import studio.singlethread.lib.operations.STLibCommandRuntime

class STLibRootCommand(
    runtime: STLibCommandRuntime,
) : STCommand<STLibCommandRuntime>(runtime) {
    override val name: String = "stlib"
    override val permission: String = "stlib.command"
    override val description: String
        get() = plugin.commandDescription("command.stlib.description")

    override fun build(builder: CommandDslBuilder) {
        builder.literal("reload") {
            permission = "stlib.command.reload"
            executes { context ->
                plugin.executeReload(context)
            }
        }
        builder.literal("doctor") {
            permission = "stlib.command.doctor"
            executes { context ->
                plugin.executeDoctor(context)
            }
        }
        builder.literal("gui") {
            permission = "stlib.command.gui"
            executes { context ->
                plugin.executeOpenGui(
                    context = context,
                    player = context.audience as? Player,
                )
            }
        }
        builder.executes { context ->
            plugin.executeStatus(context)
        }
    }
}
