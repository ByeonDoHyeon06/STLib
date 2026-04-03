package studio.singlethread.lib.framework.bukkit.text

import org.bukkit.command.CommandSender

interface PlaceholderResolver {
    fun resolve(
        sender: CommandSender?,
        message: String,
    ): String
}
