package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class STPluginSchedulerSignatureTest {
    @Test
    fun `scheduler helper methods should expose runnable signatures`() {
        val methods = STPlugin::class.java.declaredMethods
        val sync = methods.first { it.name == "sync" && it.parameterCount == 1 }
        val async = methods.first { it.name == "async" && it.parameterCount == 1 }
        val later = methods.first { it.name == "later" && it.parameterCount == 2 }
        val timer = methods.first { it.name == "timer" && it.parameterCount == 3 }

        assertEquals(Runnable::class.java, sync.parameterTypes[0])
        assertEquals(Runnable::class.java, async.parameterTypes[0])
        assertEquals(Runnable::class.java, later.parameterTypes[1])
        assertEquals(Runnable::class.java, timer.parameterTypes[2])
    }

    @Test
    fun `scheduler helper methods should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STPlugin::class.java.declaredMethods.any { method ->
                method.name in setOf("sync", "async", "later", "timer") &&
                    method.parameterTypes.any { it.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }
}
