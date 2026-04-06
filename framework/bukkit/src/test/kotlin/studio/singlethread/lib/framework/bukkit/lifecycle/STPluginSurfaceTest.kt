package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class STPluginSurfaceTest {
    @Test
    fun `legacy convenience wrappers should stay removed`() {
        val methodNames = STPlugin::class.java.declaredMethods.map { it.name }.toSet()
        val removedWrappers =
            setOf(
                "mini",
                "gui",
                "translate",
                "sendTranslated",
                "reloadTranslations",
                "broadcast",
                "broadcastTranslated",
                "sync",
                "async",
                "later",
                "timer",
                "asyncLater",
                "asyncTimer",
                "registerConfig",
                "currentConfig",
                "reloadAllConfigs",
                "loadConfig",
                "reloadConfig",
                "saveConfig",
                "configPath",
                "bridgeNodeId",
                "bridgeChannel",
                "publish",
                "subscribe",
                "respond",
                "request",
                "unsubscribe",
                "registerResourceProvider",
                "unregisterResourceProvider",
                "allPlugins",
                "findPlugin",
                "configureCommandMetrics",
                "isCommandMetricsEnabled",
                "isDebugEnabled",
                "debug",
            )

        val leaked = removedWrappers.filter(methodNames::contains)
        assertTrue(leaked.isEmpty(), "STPlugin leaked removed wrappers: $leaked")
    }

    @Test
    fun `slim plugin orchestration helpers should stay present`() {
        val methodNames = STPlugin::class.java.declaredMethods.map { it.name }.toSet()
        val expectedCoreHelpers =
            setOf(
                "command",
                "listen",
                "unlisten",
                "unlistenAll",
                "component",
                "fire",
                "send",
                "console",
            )
        val missing = expectedCoreHelpers.filterNot(methodNames::contains)
        assertTrue(missing.isEmpty(), "STPlugin lost core helper methods: $missing")
    }
}
