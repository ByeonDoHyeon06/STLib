package studio.singlethread.lib.command

import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.command.CommandTree

class StlibCommandInstaller(
    private val register: (name: String, tree: CommandTree) -> Unit,
    private val translate: (key: String) -> String,
    private val statusCommand: StlibStatusCommand,
    private val reloadCommand: StlibReloadCommand,
    private val openGuiCommand: StlibOpenGuiCommand,
    private val runtimeSnapshot: () -> StlibRuntimeSnapshot,
) {
    fun install() {
        register(
            "stlib",
            CommandTree {
                permission = "stlib.command"
                description = translate("command.stlib.description")
                literal("reload") {
                    permission = "stlib.command.reload"
                    executes { context ->
                        reloadCommand.execute(context)
                    }
                }
                executes { context ->
                    statusCommand.execute(context, runtimeSnapshot())
                }
            },
        )

        register(
            "stlibgui",
            CommandTree {
                permission = "stlib.command.gui"
                description = translate("command.stlibgui.description")
                executes { context ->
                    openGuiCommand.execute(
                        context = context,
                        player = context.audience as? Player,
                    )
                }
            },
        )
    }
}
