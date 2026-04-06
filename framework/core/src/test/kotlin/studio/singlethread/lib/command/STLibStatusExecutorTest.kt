package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.framework.api.command.CommandResponseChannel
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import studio.singlethread.lib.operations.STLibStatusExecutor
import studio.singlethread.lib.operations.STLibStatusPlugin
import studio.singlethread.lib.operations.STLibStatusSnapshot

class STLibStatusExecutorTest {
    @Test
    fun `execute should emit translated status lines and plugin entries`() {
        val replies = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val command = STLibStatusExecutor(
            translate = { key, placeholders ->
                when (key) {
                    "stlib.log.command_invoked" -> "/stlib invoked by ${placeholders.getValue("sender")}"
                    "stlib.command.backend" -> "backend=${placeholders.getValue("backend")}"
                    "stlib.command.plugin_count" -> "plugins=${placeholders.getValue("count")}"
                    "stlib.command.plugin_entry" ->
                        "${placeholders.getValue("name")}:${placeholders.getValue("version")}:${placeholders.getValue("status")}"

                    else -> key
                }
            },
            logInfo = { message -> logs.add(message) },
        )

        val context = CommandContext(senderName = "Tester", isPlayer = true).also {
            it.responder = CommandResponseChannel(replies::add)
        }
        val snapshot = STLibStatusSnapshot(
            storageBackend = "json",
            plugins = listOf(
                STLibStatusPlugin(name = "STLib", version = "1.0.0", status = STPluginStatus.ENABLED),
                STLibStatusPlugin(name = "Example", version = "0.1.0", status = STPluginStatus.LOADED),
            ),
        )

        command.execute(context, snapshot)

        assertEquals(
            listOf(
                "backend=json",
                "plugins=2",
                "STLib:1.0.0:enabled",
                "Example:0.1.0:loaded",
            ),
            replies,
        )
        assertEquals(
            listOf(
                "/stlib invoked by Tester",
                "STLib:1.0.0:enabled",
                "Example:0.1.0:loaded",
            ),
            logs,
        )
    }
}
