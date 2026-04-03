package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.command.CommandContext

class StlibReloadCommandTest {
    @Test
    fun `execute should reply success message when reload succeeds`() {
        val replies = mutableListOf<String>()
        val command =
            StlibReloadCommand(
                translate = { key, placeholders ->
                    when (key) {
                        "stlib.command.reload.success" ->
                            "ok:${placeholders["configs"]}:${placeholders["profile"]}:${placeholders["dashboard"]}:${placeholders["persist"]}:${placeholders["persist_active"]}:${placeholders["command_metrics"]}"
                        else -> key
                    }
                },
                logInfo = {},
                logWarning = {},
                reload = {
                    StlibReloadSnapshot(
                        reloadedConfigCount = 3,
                        dashboardAvailable = true,
                        dashboardProfile = "core_ops",
                        persistenceEnabled = false,
                        persistenceActive = false,
                        commandMetricsEnabled = false,
                    )
                },
            )

        command.execute(context(replies))

        assertEquals(listOf("ok:3:core_ops:true:false:false:false"), replies)
    }

    @Test
    fun `execute should reply failure message when reload fails`() {
        val replies = mutableListOf<String>()
        val command =
            StlibReloadCommand(
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
            commandContext.responder = replies::add
        }
    }
}
