package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.gui.STGuiBuilder
import studio.singlethread.lib.framework.bukkit.gui.STGuiService

class STPluginGuiSignatureTest {
    @Test
    fun `gui helpers should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STPlugin::class.java.declaredMethods.any { method ->
                method.name == "gui" &&
                    method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }

    @Test
    fun `gui service contract should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STGuiService::class.java.methods.any { method ->
                method.name == "create" &&
                    method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }

    @Test
    fun `gui builder view and pattern contracts should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            STGuiBuilder::class.java.methods.any { method ->
                method.name in setOf("view", "pattern") &&
                    method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }

    @Test
    fun `gui top-level helpers should not expose kotlin function types`() {
        val helperClass = Class.forName("studio.singlethread.lib.framework.bukkit.gui.STGuiApiKt")
        val hasKotlinFunctionParam =
            helperClass.methods.any { method ->
                method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") } ||
                    method.returnType.name.startsWith("kotlin.jvm.functions.")
            }
        assertTrue(!hasKotlinFunctionParam)
    }
}
