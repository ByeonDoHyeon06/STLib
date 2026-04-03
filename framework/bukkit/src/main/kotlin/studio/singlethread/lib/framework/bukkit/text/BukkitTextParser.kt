package studio.singlethread.lib.framework.bukkit.text

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import studio.singlethread.lib.framework.api.text.TextService

class BukkitTextParser(
    private val textService: TextService,
    private val placeholderResolver: PlaceholderResolver,
) {
    fun parse(
        message: String,
        placeholders: Map<String, String> = emptyMap(),
    ): Component {
        return textService.parse(message, placeholders)
    }

    fun parse(
        sender: CommandSender?,
        message: String,
        placeholders: Map<String, String> = emptyMap(),
        usePlaceholderApi: Boolean = true,
    ): Component {
        val resolvedMessage =
            if (usePlaceholderApi) {
                placeholderResolver.resolve(sender, message)
            } else {
                message
            }
        return textService.parse(resolvedMessage, placeholders)
    }
}
