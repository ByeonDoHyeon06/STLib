package studio.singlethread.lib.operations

import studio.singlethread.lib.framework.api.command.CommandContext

class STLibStatusExecutor(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
) {
    fun execute(
        context: CommandContext,
        snapshot: STLibStatusSnapshot,
    ) {
        logInfo(
            translate(
                "stlib.log.command_invoked",
                mapOf("sender" to context.senderName),
            ),
        )

        context.reply(
            translate(
                "stlib.command.backend",
                mapOf("backend" to snapshot.storageBackend),
            ),
        )
        context.reply(
            translate(
                "stlib.command.plugin_count",
                mapOf("count" to snapshot.plugins.size.toString()),
            ),
        )

        snapshot.plugins.forEach { plugin ->
            val line =
                translate(
                    "stlib.command.plugin_entry",
                    mapOf(
                        "name" to plugin.name,
                        "version" to plugin.version,
                        "status" to plugin.status,
                    ),
                )
            context.reply(line)
            logInfo(line)
        }
    }
}
