package studio.singlethread.lib.operations

import studio.singlethread.lib.dashboard.STLibDashboardHealthLevel
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.health.STLibHealthSnapshot

class STLibDoctorExecutor(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
) {
    fun execute(
        context: CommandContext,
        snapshot: STLibHealthSnapshot,
    ) {
        logInfo(
            translate(
                "stlib.log.doctor_invoked",
                mapOf("sender" to context.senderName),
            ),
        )

        val degradedPlugins =
            snapshot.plugins
                .asSequence()
                .filter { it.healthLevel == STLibDashboardHealthLevel.DEGRADED }
                .map { plugin -> "${plugin.name}(issues=${plugin.healthIssueCount})" }
                .toList()
        val bridgeIssues = bridgeIssues(snapshot)

        context.reply(
            translate(
                "stlib.command.doctor.generated_at",
                mapOf("time" to snapshot.generatedAt.toString()),
            ),
        )
        context.reply(
            translate(
                "stlib.command.doctor.core",
                mapOf(
                    "profile" to snapshot.dashboardProfile,
                    "dashboard" to snapshot.dashboardAvailable.toString(),
                    "persist_enabled" to snapshot.persistenceEnabled.toString(),
                    "persist_active" to snapshot.persistenceActive.toString(),
                    "scheduler" to snapshot.schedulerEnabled.toString(),
                    "command_metrics" to snapshot.commandMetricsEnabled.toString(),
                    "di_discovered" to snapshot.diDiscovered.toString(),
                    "di_validated" to snapshot.diValidated.toString(),
                ),
            ),
        )
        context.reply(
            translate(
                "stlib.command.doctor.bridge",
                mapOf(
                    "mode" to snapshot.bridgeMode,
                    "distributed" to snapshot.bridgeDistributed.toString(),
                    "redis_connected" to snapshot.bridgeRedisConnected.toString(),
                ),
            ),
        )
        context.reply(
            translate(
                "stlib.command.doctor.bridge_metrics",
                mapOf(
                    "pending" to snapshot.bridgePendingRequests.toString(),
                    "submitted" to snapshot.bridgeRequestSubmitted.toString(),
                    "timeouts" to snapshot.bridgeRequestTimedOut.toString(),
                    "backpressure_rejected" to snapshot.bridgeRequestRejectedBackpressure.toString(),
                    "late_responses" to snapshot.bridgeResponseLate.toString(),
                    "target_mismatch" to snapshot.bridgeResponseTargetMismatched.toString(),
                ),
            ),
        )
        if (bridgeIssues.isNotEmpty()) {
            context.reply(
                translate(
                    "stlib.command.doctor.bridge_issues",
                    mapOf("issues" to bridgeIssues.joinToString(", ")),
                ),
            )
        }
        context.reply(
            translate(
                "stlib.command.doctor.plugins",
                mapOf(
                    "total" to snapshot.plugins.size.toString(),
                    "degraded" to degradedPlugins.size.toString(),
                ),
            ),
        )

        if (degradedPlugins.isEmpty() && bridgeIssues.isEmpty()) {
            context.reply(translate("stlib.command.doctor.healthy", emptyMap()))
            return
        }

        context.reply(
            translate(
                "stlib.command.doctor.degraded_plugins",
                mapOf("plugins" to degradedPlugins.joinToString(", ")),
            ),
        )
    }

    private fun bridgeIssues(snapshot: STLibHealthSnapshot): List<String> {
        val issues = mutableListOf<String>()
        if (snapshot.bridgeDistributed && !snapshot.bridgeRedisConnected) {
            issues += "redisDisconnected"
        }
        if (snapshot.bridgeRequestTimedOut > 0) {
            issues += "timeouts=${snapshot.bridgeRequestTimedOut}"
        }
        if (snapshot.bridgeRequestRejectedBackpressure > 0) {
            issues += "backpressureRejected=${snapshot.bridgeRequestRejectedBackpressure}"
        }
        if (snapshot.bridgeResponseLate > 0) {
            issues += "lateResponses=${snapshot.bridgeResponseLate}"
        }
        if (snapshot.bridgeResponseTargetMismatched > 0) {
            issues += "targetMismatch=${snapshot.bridgeResponseTargetMismatched}"
        }
        return issues
    }
}
