package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.api.command.CommandResponseChannel
import studio.singlethread.lib.operations.STLibReloadExecutor
import studio.singlethread.lib.operations.STLibReloadSnapshot

class STLibReloadExecutorTest {
    @Test
    fun `execute should reply success message when reload succeeds`() {
        val replies = mutableListOf<String>()
        val command =
            STLibReloadExecutor(
                translate = { key, placeholders ->
                    when (key) {
                        "stlib.command.reload.success" ->
                            "ok:${placeholders["configs"]}:${placeholders["profile"]}:${placeholders["dashboard"]}:${placeholders["persist"]}:${placeholders["persist_active"]}:${placeholders["command_metrics"]}:${placeholders["scheduler"]}:${placeholders["di_discovered"]}:${placeholders["di_validated"]}:${placeholders["bridge_mode"]}:${placeholders["bridge_distributed"]}:${placeholders["bridge_node"]}"
                        else -> key
                    }
                },
                logInfo = {},
                logWarning = {},
                reload = {
                    STLibReloadSnapshot(
                        reloadedConfigCount = 3,
                        dashboardAvailable = true,
                        dashboardProfile = "core_ops",
                        persistenceEnabled = false,
                        persistenceActive = false,
                        commandMetricsEnabled = false,
                        schedulerEnabled = true,
                        diDiscovered = 4,
                        diValidated = 4,
                        bridgeMode = "local",
                        bridgeDistributed = false,
                        bridgeNodeId = "stlib-25565",
                    )
                },
            )

        command.execute(context(replies))

        assertEquals(listOf("ok:3:core_ops:true:false:false:false:true:4:4:local:false:stlib-25565"), replies)
    }

    @Test
    fun `execute should reply failure message when reload fails`() {
        val replies = mutableListOf<String>()
        val command =
            STLibReloadExecutor(
                translate = { key, placeholders ->
                    when (key) {
                        "stlib.command.reload.failed" -> "fail:${placeholders["reason"]}"
                        else -> key
                    }
                },
                logInfo = {},
                logWarning = {},
                reload = { error("boom") },
            )

        command.execute(context(replies))

        assertEquals(listOf("fail:boom"), replies)
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
