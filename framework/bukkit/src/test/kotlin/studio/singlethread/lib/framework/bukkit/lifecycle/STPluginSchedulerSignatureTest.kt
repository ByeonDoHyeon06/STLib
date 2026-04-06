package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class STPluginSchedulerSignatureTest {
    @Test
    fun `scheduler wrappers should be removed from STPlugin surface`() {
        val methods = STPlugin::class.java.declaredMethods

        val wrapperMethods =
            methods.filter { it.name in setOf("sync", "async", "later", "timer", "asyncLater", "asyncTimer") }
        assertTrue(wrapperMethods.isEmpty(), "STPlugin should expose scheduler service instead of wrapper helpers")
    }

    @Test
    fun `STPlugin scheduler surface should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STPlugin::class.java.declaredMethods.any { method ->
                method.name in setOf("sync", "async", "later", "timer", "asyncLater", "asyncTimer") &&
                    method.parameterTypes.any { it.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }
}
