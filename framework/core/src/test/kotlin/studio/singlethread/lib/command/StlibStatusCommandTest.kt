package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.command.CommandContext

class StlibStatusCommandTest {
    @Test
    fun `execute should emit translated status lines and plugin entries`() {
        val replies = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val command = StlibStatusCommand(
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
            it.responder = replies::add
        }
        val snapshot = StlibRuntimeSnapshot(
            storageBackend = "json",
            plugins = listOf(
                StlibPluginStatus(name = "STLib", version = "1.0.0", status = "enabled"),
                StlibPluginStatus(name = "Example", version = "0.1.0", status = "loaded"),
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
