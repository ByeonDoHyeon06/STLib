package studio.singlethread.lib.framework.bukkit.command

import org.bukkit.Location
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.command.CommandContext

fun CommandContext.playerArgument(name: String): Player? {
    return argument(name)
}

fun CommandContext.offlinePlayerArgument(name: String): OfflinePlayer? {
    return argument(name)
}

fun CommandContext.worldArgument(name: String): World? {
    return argument(name)
}

fun CommandContext.locationArgument(name: String): Location? {
    return argument(name)
}
