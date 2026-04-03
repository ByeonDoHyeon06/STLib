package studio.singlethread.lib.framework.bukkit.translation

import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.translation.TranslationService
import studio.singlethread.lib.framework.bukkit.text.BukkitTextParser

class BukkitTranslationFacade(
    private val translationService: TranslationService,
    private val textParser: BukkitTextParser,
) {
    fun translate(
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): Component {
        val message = translationService.translate(key = key, locale = null, placeholders = placeholders)
        return textParser.parse(message, placeholders)
    }

    fun translate(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ): Component {
        val locale = senderLocale(sender)
        val message = translationService.translate(key = key, locale = locale, placeholders = placeholders)
        return textParser.parse(sender, message, placeholders, usePlaceholderApi = true)
    }

    fun sendTranslated(
        sender: CommandSender,
        key: String,
        placeholders: Map<String, String> = emptyMap(),
    ) {
        sender.sendMessage(translate(sender, key, placeholders))
    }

    fun reloadTranslations() {
        translationService.reload()
    }

    internal fun senderLocale(sender: CommandSender): String? {
        val player = sender as? Player ?: return null
        return runCatching { player.locale() }
            .getOrNull()
            ?.toLanguageTag()
            ?.takeIf { it.isNotBlank() }
    }
}
