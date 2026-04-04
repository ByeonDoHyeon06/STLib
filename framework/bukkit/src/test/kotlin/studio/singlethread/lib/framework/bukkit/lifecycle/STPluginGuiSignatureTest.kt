package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.gui.StGuiBuilder
import studio.singlethread.lib.framework.bukkit.gui.StGuiService

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
            StGuiService::class.java.methods.any { method ->
                method.name == "create" &&
                    method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }

    @Test
    fun `gui builder page and pattern contracts should not expose kotlin function types`() {
        val hasKotlinFunctionParam =
            StGuiBuilder::class.java.methods.any { method ->
                method.name in setOf("page", "pageDefault", "pattern") &&
                    method.parameterTypes.any { type -> type.name.startsWith("kotlin.jvm.functions.") }
            }
        assertTrue(!hasKotlinFunctionParam)
    }
}
