package studio.singlethread.lib.framework.api.notifier

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component

interface NotifierService {
    fun message(template: String, placeholders: Map<String, String> = emptyMap()): Component

    fun prefixed(template: String, placeholders: Map<String, String> = emptyMap()): Component

    fun send(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())

    fun sendPrefixed(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())

    fun actionBar(target: Audience, template: String, placeholders: Map<String, String> = emptyMap())

    fun title(
        target: Audience,
        titleTemplate: String,
        subtitleTemplate: String = "",
        placeholders: Map<String, String> = emptyMap(),
    )
}
