package studio.singlethread.lib.command

import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.command.CommandContext
import studio.singlethread.lib.operations.STLibCommandRuntime

class STLibCommandClassTest {
    @Test
    fun `root command should expose reload doctor and gui nodes with expected permissions`() {
        val runtime = FakeRuntime()
        val command = STLibRootCommand(runtime)

        val definition = command.definition()
        assertEquals("stlib", definition.name)
        assertEquals("stlib.command", definition.permission)
        assertEquals("desc:command.stlib.description", definition.description)

        val nodes = definition.children.associateBy { node -> node.literal }
        assertEquals("stlib.command.reload", nodes.getValue("reload").permission)
        assertEquals("stlib.command.doctor", nodes.getValue("doctor").permission)
        assertEquals("stlib.command.gui", nodes.getValue("gui").permission)
        assertNotNull(definition.executor)
    }

    @Test
    fun `root command executors should delegate to runtime operations including gui`() {
        val runtime = FakeRuntime()
        val command = STLibRootCommand(runtime)
        val definition = command.definition()
        val context = CommandContext(senderName = "tester", isPlayer = false)

        definition.executor!!.execute(context)
        definition.children.first { it.literal == "reload" }.executor!!.execute(context)
        definition.children.first { it.literal == "doctor" }.executor!!.execute(context)
        definition.children.first { it.literal == "gui" }.executor!!.execute(context)

        assertEquals(1, runtime.statusCalls)
        assertEquals(1, runtime.reloadCalls)
        assertEquals(1, runtime.doctorCalls)
        assertEquals(1, runtime.openGuiCalls)
        assertEquals(null, runtime.lastPlayer)
    }

    private class FakeRuntime : STLibCommandRuntime {
        var statusCalls: Int = 0
        var reloadCalls: Int = 0
        var doctorCalls: Int = 0
        var openGuiCalls: Int = 0
        var lastPlayer: Player? = null

        override fun commandDescription(key: String): String {
            return "desc:$key"
        }

        override fun executeStatus(context: CommandContext) {
            statusCalls += 1
        }

        override fun executeReload(context: CommandContext) {
            reloadCalls += 1
        }

        override fun executeDoctor(context: CommandContext) {
            doctorCalls += 1
        }

        override fun executeOpenGui(
            context: CommandContext,
            player: Player?,
        ) {
            openGuiCalls += 1
            lastPlayer = player
        }
    }
}
