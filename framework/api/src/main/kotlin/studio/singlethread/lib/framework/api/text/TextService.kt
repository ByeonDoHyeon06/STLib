package studio.singlethread.lib.framework.api.text

import net.kyori.adventure.text.Component

interface TextService {
    fun parse(message: String): Component

    fun parse(message: String, placeholders: Map<String, String>): Component
}
