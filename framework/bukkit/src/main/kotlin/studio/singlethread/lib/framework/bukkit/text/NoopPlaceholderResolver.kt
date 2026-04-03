package studio.singlethread.lib.framework.bukkit.text

import org.bukkit.command.CommandSender

object NoopPlaceholderResolver : PlaceholderResolver {
    override fun resolve(
        sender: CommandSender?,
        message: String,
    ): String {
        return message
    }
}
