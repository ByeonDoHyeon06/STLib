package studio.singlethread.lib.framework.bukkit.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedLifecycleGateTest {
    @Test
    fun `onLoad calls underlying action only once`() {
        val gate = SharedLifecycleGate()
        var loads = 0

        gate.onLoad { loads++ }
        gate.onLoad { loads++ }

        assertEquals(1, loads)
        assertEquals(2, gate.snapshot().loadRefs)
        assertTrue(gate.snapshot().loaded)
    }

    @Test
    fun `onEnable calls underlying action only once while refs remain`() {
        val gate = SharedLifecycleGate()
        var enables = 0

        gate.onLoad {}
        gate.onLoad {}
        gate.onEnable { enables++ }
        gate.onEnable { enables++ }

        assertEquals(1, enables)
        assertEquals(2, gate.snapshot().enableRefs)
        assertTrue(gate.snapshot().enabled)
    }

    @Test
    fun `onDisable calls underlying action only when last enable ref is released`() {
        val gate = SharedLifecycleGate()
        var disables = 0

        gate.onLoad {}
        gate.onLoad {}
        gate.onEnable {}
        gate.onEnable {}

        gate.onDisable { disables++ }
        assertEquals(0, disables)
        assertEquals(1, gate.snapshot().enableRefs)

        gate.onDisable { disables++ }
        assertEquals(1, disables)

        val state = gate.snapshot()
        assertFalse(state.loaded)
        assertFalse(state.enabled)
        assertEquals(0, state.loadRefs)
        assertEquals(0, state.enableRefs)
    }

    @Test
    fun `onEnable before onLoad throws`() {
        val gate = SharedLifecycleGate()

        assertFailsWith<IllegalStateException> {
            gate.onEnable {}
        }
    }

    @Test
    fun `onEnableEnsuringLoaded should recover when load phase is missing`() {
        val gate = SharedLifecycleGate()
        var loads = 0
        var enables = 0

        gate.onEnableEnsuringLoaded(
            loadAction = { loads++ },
            enableAction = { enables++ },
        )

        assertEquals(1, loads)
        assertEquals(1, enables)

        val state = gate.snapshot()
        assertTrue(state.loaded)
        assertTrue(state.enabled)
        assertEquals(1, state.loadRefs)
        assertEquals(1, state.enableRefs)
    }

    @Test
    fun `failed ensure-load does not mutate state`() {
        val gate = SharedLifecycleGate()

        assertFailsWith<IllegalStateException> {
            gate.onEnableEnsuringLoaded(
                loadAction = { throw IllegalStateException("boom") },
                enableAction = {},
            )
        }

        val state = gate.snapshot()
        assertFalse(state.loaded)
        assertFalse(state.enabled)
        assertEquals(0, state.loadRefs)
        assertEquals(0, state.enableRefs)
    }

    @Test
    fun `failed onLoad does not change state`() {
        val gate = SharedLifecycleGate()

        assertFailsWith<IllegalStateException> {
            gate.onLoad {
                throw IllegalStateException("boom")
            }
        }

        val state = gate.snapshot()
        assertFalse(state.loaded)
        assertFalse(state.enabled)
        assertEquals(0, state.loadRefs)
        assertEquals(0, state.enableRefs)
    }
}
