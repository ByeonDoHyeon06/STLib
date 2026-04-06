package studio.singlethread.lib.operations

import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.command.CommandContext

interface STLibCommandRuntime {
    fun commandDescription(key: String): String

    fun executeStatus(context: CommandContext)

    fun executeReload(context: CommandContext)

    fun executeDoctor(context: CommandContext)

    fun executeOpenGui(
        context: CommandContext,
        player: Player?,
    )
}
