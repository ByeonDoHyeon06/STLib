package studio.singlethread.lib.framework.bukkit.notifier

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import studio.singlethread.lib.framework.api.notifier.NotifierService
import studio.singlethread.lib.framework.api.text.TextService

class BukkitNotifierService(
    private val textService: TextService,
    private val pluginName: String,
) : NotifierService {
    override fun message(
        template: String,
        placeholders: Map<String, String>,
    ): Component {
        return textService.parse(template, placeholders)
    }

    override fun prefixed(
        template: String,
        placeholders: Map<String, String>,
    ): Component {
        val prefixedTemplate = "${prefixTemplate()}$template"
        return textService.parse(prefixedTemplate, placeholders)
    }

    override fun send(
        target: Audience,
        template: String,
        placeholders: Map<String, String>,
    ) {
        target.sendMessage(message(template, placeholders))
    }

    override fun sendPrefixed(
        target: Audience,
        template: String,
        placeholders: Map<String, String>,
    ) {
        target.sendMessage(prefixed(template, placeholders))
    }

    override fun actionBar(
        target: Audience,
        template: String,
        placeholders: Map<String, String>,
    ) {
        target.sendActionBar(message(template, placeholders))
    }

    override fun title(
        target: Audience,
        titleTemplate: String,
        subtitleTemplate: String,
        placeholders: Map<String, String>,
    ) {
        val title = message(titleTemplate, placeholders)
        val subtitle = message(subtitleTemplate, placeholders)
        target.showTitle(Title.title(title, subtitle))
    }

    private fun prefixTemplate(): String {
        return "<gray>[<yellow>${pluginName.trim()}</yellow>]</gray> "
    }
}
