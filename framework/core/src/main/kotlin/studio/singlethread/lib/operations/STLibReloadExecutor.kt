package studio.singlethread.lib.operations

import studio.singlethread.lib.framework.api.command.CommandContext

data class STLibReloadSnapshot(
    val reloadedConfigCount: Int,
    val dashboardAvailable: Boolean,
    val dashboardProfile: String,
    val persistenceEnabled: Boolean,
    val persistenceActive: Boolean,
    val commandMetricsEnabled: Boolean,
    val schedulerEnabled: Boolean,
    val diDiscovered: Int,
    val diValidated: Int,
    val bridgeMode: String,
    val bridgeDistributed: Boolean,
    val bridgeNodeId: String,
)

class STLibReloadExecutor(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
    private val logWarning: (String) -> Unit,
    private val reload: () -> STLibReloadSnapshot,
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
                            "scheduler" to snapshot.schedulerEnabled.toString(),
                            "di_discovered" to snapshot.diDiscovered.toString(),
                            "di_validated" to snapshot.diValidated.toString(),
                            "bridge_mode" to snapshot.bridgeMode,
                            "bridge_distributed" to snapshot.bridgeDistributed.toString(),
                            "bridge_node" to snapshot.bridgeNodeId,
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
