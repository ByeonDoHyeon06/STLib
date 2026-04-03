package studio.singlethread.lib.command

import studio.singlethread.lib.framework.api.command.CommandContext

data class StlibReloadSnapshot(
    val reloadedConfigCount: Int,
    val dashboardAvailable: Boolean,
    val dashboardProfile: String,
    val persistenceEnabled: Boolean,
    val persistenceActive: Boolean,
    val commandMetricsEnabled: Boolean,
)

class StlibReloadCommand(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
    private val logWarning: (String) -> Unit,
    private val reload: () -> StlibReloadSnapshot,
) {
    fun execute(context: CommandContext) {
        logInfo(
            translate(
                "stlib.log.reload_invoked",
                mapOf("sender" to context.senderName),
            ),
        )

        runCatching(reload)
            .onSuccess { snapshot ->
                context.reply(
                    translate(
                        "stlib.command.reload.success",
                        mapOf(
                            "configs" to snapshot.reloadedConfigCount.toString(),
                            "profile" to snapshot.dashboardProfile,
                            "dashboard" to snapshot.dashboardAvailable.toString(),
                            "persist" to snapshot.persistenceEnabled.toString(),
                            "persist_active" to snapshot.persistenceActive.toString(),
                            "command_metrics" to snapshot.commandMetricsEnabled.toString(),
                        ),
                    ),
                )
            }.onFailure { error ->
                logWarning("STLib reload failed: ${error.message}")
                context.reply(
                    translate(
                        "stlib.command.reload.failed",
                        mapOf("reason" to (error.message ?: "unknown")),
                    ),
                )
            }
    }
}
