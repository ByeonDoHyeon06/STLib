package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.dashboard.STLibDashboardHealthLevel
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.api.command.CommandResponseChannel
import studio.singlethread.lib.health.STLibHealthSnapshot
import studio.singlethread.lib.health.STLibPluginHealthSnapshot
import studio.singlethread.lib.operations.STLibDoctorExecutor
import java.time.Instant

class STLibDoctorExecutorTest {
    @Test
    fun `execute should print core bridge and degraded plugin diagnostics`() {
        val replies = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val command =
            STLibDoctorExecutor(
                translate = { key, placeholders ->
                    when (key) {
                        "stlib.log.doctor_invoked" -> "doctor:${placeholders.getValue("sender")}"
                        "stlib.command.doctor.generated_at" -> "time=${placeholders.getValue("time")}"
                        "stlib.command.doctor.core" ->
                            "core:${placeholders.getValue("profile")}:${placeholders.getValue("dashboard")}:${placeholders.getValue("persist_enabled")}:${placeholders.getValue("persist_active")}:${placeholders.getValue("scheduler")}:${placeholders.getValue("command_metrics")}:${placeholders.getValue("di_discovered")}:${placeholders.getValue("di_validated")}"
                        "stlib.command.doctor.bridge" ->
                            "bridge:${placeholders.getValue("mode")}:${placeholders.getValue("distributed")}:${placeholders.getValue("redis_connected")}"
                        "stlib.command.doctor.bridge_metrics" ->
                            "metrics:${placeholders.getValue("pending")}:${placeholders.getValue("submitted")}:${placeholders.getValue("timeouts")}:${placeholders.getValue("backpressure_rejected")}:${placeholders.getValue("late_responses")}:${placeholders.getValue("target_mismatch")}"
                        "stlib.command.doctor.bridge_issues" ->
                            "bridgeIssues:${placeholders.getValue("issues")}"
                        "stlib.command.doctor.plugins" ->
                            "plugins:${placeholders.getValue("total")}:${placeholders.getValue("degraded")}"
                        "stlib.command.doctor.degraded_plugins" ->
                            "degraded:${placeholders.getValue("plugins")}"
                        else -> key
                    }
                },
                logInfo = logs::add,
            )

        val snapshot =
            STLibHealthSnapshot(
                generatedAt = Instant.parse("2026-04-06T09:00:00Z"),
                dashboardProfile = "core_ops",
                dashboardAvailable = true,
                persistenceEnabled = false,
                persistenceActive = false,
                commandMetricsEnabled = false,
                schedulerEnabled = true,
                diDiscovered = 5,
                diValidated = 5,
                bridgeMode = "redis",
                bridgeDistributed = true,
                bridgeRedisConnected = false,
                bridgePendingRequests = 2,
                bridgeRequestSubmitted = 11,
                bridgeRequestTimedOut = 1,
                bridgeRequestRejectedBackpressure = 3,
                bridgeResponseLate = 4,
                bridgeResponseTargetMismatched = 5,
                plugins =
                    listOf(
                        STLibPluginHealthSnapshot(
                            name = "Alpha",
                            version = "1.0.0",
                            healthLevel = STLibDashboardHealthLevel.HEALTHY,
                            healthIssueCount = 0,
                            capabilityEnabledCount = 4,
                            capabilityDisabledCount = 0,
                        ),
                        STLibPluginHealthSnapshot(
                            name = "Beta",
                            version = "1.0.0",
                            healthLevel = STLibDashboardHealthLevel.DEGRADED,
                            healthIssueCount = 2,
                            capabilityEnabledCount = 3,
                            capabilityDisabledCount = 1,
                        ),
                    ),
            )

        command.execute(context(replies), snapshot)

        assertEquals(listOf("doctor:tester"), logs)
        assertEquals(
            listOf(
                "time=2026-04-06T09:00:00Z",
                "core:core_ops:true:false:false:true:false:5:5",
                "bridge:redis:true:false",
                "metrics:2:11:1:3:4:5",
                "bridgeIssues:redisDisconnected, timeouts=1, backpressureRejected=3, lateResponses=4, targetMismatch=5",
                "plugins:2:1",
                "degraded:Beta(issues=2)",
            ),
            replies,
        )
    }

    @Test
    fun `execute should print healthy message when no degraded plugin exists`() {
        val replies = mutableListOf<String>()
        val command =
            STLibDoctorExecutor(
                translate = { key, placeholders ->
                    when (key) {
                        "stlib.command.doctor.generated_at" -> "time=${placeholders.getValue("time")}"
                        "stlib.command.doctor.core" -> "core"
                        "stlib.command.doctor.bridge" -> "bridge"
                        "stlib.command.doctor.bridge_metrics" -> "metrics"
                        "stlib.command.doctor.bridge_issues" -> "bridge_issues"
                        "stlib.command.doctor.plugins" -> "plugins"
                        "stlib.command.doctor.healthy" -> "healthy"
                        else -> key
                    }
                },
                logInfo = {},
            )

        val snapshot =
            STLibHealthSnapshot(
                generatedAt = Instant.parse("2026-04-06T10:00:00Z"),
                dashboardProfile = "core_ops",
                dashboardAvailable = true,
                persistenceEnabled = false,
                persistenceActive = false,
                commandMetricsEnabled = false,
                schedulerEnabled = true,
                diDiscovered = 0,
                diValidated = 0,
                bridgeMode = "local",
                bridgeDistributed = false,
                bridgeRedisConnected = false,
                bridgePendingRequests = 0,
                bridgeRequestSubmitted = 0,
                bridgeRequestTimedOut = 0,
                bridgeRequestRejectedBackpressure = 0,
                bridgeResponseLate = 0,
                bridgeResponseTargetMismatched = 0,
                plugins = emptyList(),
            )

        command.execute(context(replies), snapshot)

        assertEquals("healthy", replies.last())
    }

    private fun context(replies: MutableList<String>): CommandContext {
        return CommandContext(
            senderName = "tester",
            isPlayer = false,
        ).also { commandContext ->
            commandContext.responder = CommandResponseChannel(replies::add)
        }
    }
}
