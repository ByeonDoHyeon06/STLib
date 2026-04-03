package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.TimeUnit

class STPluginSchedulerSignatureTest {
    @Test
    fun `scheduler helper methods should expose runnable signatures`() {
        val methods = STPlugin::class.java.declaredMethods
        val sync = methods.first { it.name == "sync" && it.parameterCount == 1 }
        val async = methods.first { it.name == "async" && it.parameterCount == 1 }
        val later = methods.first { it.name == "later" && it.parameterCount == 2 }
        val timer = methods.first { it.name == "timer" && it.parameterCount == 3 }
        val laterWithUnit = methods.first { it.name == "later" && it.parameterCount == 3 }
        val asyncLaterWithUnit = methods.first { it.name == "asyncLater" && it.parameterCount == 3 }
        val asyncLaterWithDuration = methods.first { it.name == "asyncLater" && it.parameterCount == 2 }
        val timerWithUnit = methods.first { it.name == "timer" && it.parameterCount == 4 }
        val asyncTimerWithUnit = methods.first { it.name == "asyncTimer" && it.parameterCount == 4 }
        val asyncTimerWithDuration = methods.first { it.name == "asyncTimer" && it.parameterCount == 3 }

        assertEquals(Runnable::class.java, sync.parameterTypes[0])
        assertEquals(Runnable::class.java, async.parameterTypes[0])
        assertEquals(Runnable::class.java, later.parameterTypes[1])
        assertEquals(Runnable::class.java, timer.parameterTypes[2])
        assertEquals(TimeUnit::class.java, laterWithUnit.parameterTypes[1])
        assertEquals(Runnable::class.java, laterWithUnit.parameterTypes[2])
        assertEquals(TimeUnit::class.java, asyncLaterWithUnit.parameterTypes[1])
        assertEquals(Runnable::class.java, asyncLaterWithUnit.parameterTypes[2])
        assertEquals(Duration::class.java, asyncLaterWithDuration.parameterTypes[0])
        assertEquals(Runnable::class.java, asyncLaterWithDuration.parameterTypes[1])
        assertEquals(TimeUnit::class.java, timerWithUnit.parameterTypes[2])
        assertEquals(Runnable::class.java, timerWithUnit.parameterTypes[3])
        assertEquals(TimeUnit::class.java, asyncTimerWithUnit.parameterTypes[2])
        assertEquals(Runnable::class.java, asyncTimerWithUnit.parameterTypes[3])
        assertEquals(Duration::class.java, asyncTimerWithDuration.parameterTypes[0])
        assertEquals(Duration::class.java, asyncTimerWithDuration.parameterTypes[1])
        assertEquals(Runnable::class.java, asyncTimerWithDuration.parameterTypes[2])
    }

    @Test
    fun `scheduler helper methods should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STPlugin::class.java.declaredMethods.any { method ->
                method.name in setOf("sync", "async", "later", "timer", "asyncLater", "asyncTimer") &&
                    method.parameterTypes.any { it.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }
}
