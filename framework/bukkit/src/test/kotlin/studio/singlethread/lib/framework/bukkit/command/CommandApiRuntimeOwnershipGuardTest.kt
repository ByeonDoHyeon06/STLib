package studio.singlethread.lib.framework.bukkit.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.util.Properties

class CommandApiRuntimeOwnershipGuardTest {
    @Test
    fun `claim should accept repeated claims from same runtime`() {
        val properties = Properties()
        val guard = CommandApiRuntimeOwnershipGuard(runtimeId = "runtime-a", properties = properties, propertyKey = "owner")

        guard.claim("plugin-a")
        guard.claim("plugin-b")

        assertEquals("runtime-a", guard.ownerRuntimeId())
    }

    @Test
    fun `claim should reject different runtime owner`() {
        val properties = Properties()
        val guardA = CommandApiRuntimeOwnershipGuard(runtimeId = "runtime-a", properties = properties, propertyKey = "owner")
        val guardB = CommandApiRuntimeOwnershipGuard(runtimeId = "runtime-b", properties = properties, propertyKey = "owner")

        guardA.claim("plugin-a")

        val error =
            assertFailsWith<IllegalStateException> {
                guardB.claim("plugin-b")
            }
        assertTrue(error.message.orEmpty().contains("multiple STLib runtime loaders"))
    }

    @Test
    fun `releaseWhenIdle should clear ownership only when refs are fully released`() {
        val properties = Properties()
        val guard = CommandApiRuntimeOwnershipGuard(runtimeId = "runtime-a", properties = properties, propertyKey = "owner")
        guard.claim("plugin-a")

        guard.releaseWhenIdle(
            SharedLifecycleGate.State(
                loaded = true,
                enabled = true,
                loadRefs = 1,
                enableRefs = 1,
            ),
        )
        assertEquals("runtime-a", guard.ownerRuntimeId())

        guard.releaseWhenIdle(
            SharedLifecycleGate.State(
                loaded = false,
                enabled = false,
                loadRefs = 0,
                enableRefs = 0,
            ),
        )
        assertEquals(null, guard.ownerRuntimeId())
    }
}
