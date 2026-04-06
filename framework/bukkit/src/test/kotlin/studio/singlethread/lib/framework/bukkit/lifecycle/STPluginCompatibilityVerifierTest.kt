package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.lifecycle.support.PluginCompatibilityVerifier
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntime
import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolution
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolution
import studio.singlethread.lib.framework.bukkit.version.MinecraftVersion
import studio.singlethread.lib.framework.bukkit.version.SupportedBukkitRuntimes
import studio.singlethread.lib.framework.bukkit.version.SupportedServerVersions
import studio.singlethread.lib.framework.bukkit.version.UnsupportedServerVersionAction
import java.util.logging.Logger

class STPluginCompatibilityVerifierTest {
    private val verifier =
        PluginCompatibilityVerifier(
            logger = Logger.getLogger("test"),
            pluginName = "TestPlugin",
            pluginVersion = "1.0.0",
        )

    @Test
    fun `minecraft unresolved should pass compatibility gate`() {
        val compatible =
            verifier.verifyMinecraft(
                policy = SupportedServerVersions.range("1.19.4", "1.21.99"),
                resolved = BukkitServerVersionResolution(resolved = null, candidates = listOf("unknown")),
                mismatchAction = UnsupportedServerVersionAction.DISABLE_PLUGIN,
            )

        assertTrue(compatible)
    }

    @Test
    fun `unsupported minecraft should fail when action is disable`() {
        val compatible =
            verifier.verifyMinecraft(
                policy = SupportedServerVersions.exact("1.20.4"),
                resolved =
                    BukkitServerVersionResolution(
                        resolved = MinecraftVersion.parseOrThrow("1.21.1"),
                        candidates = listOf("1.21.1"),
                    ),
                mismatchAction = UnsupportedServerVersionAction.DISABLE_PLUGIN,
            )

        assertFalse(compatible)
    }

    @Test
    fun `unsupported runtime should pass when action is warn only`() {
        val compatible =
            verifier.verifyRuntime(
                policy = SupportedBukkitRuntimes.only(BukkitRuntime.PAPER),
                resolved = BukkitRuntimeResolution(runtime = BukkitRuntime.FOLIA, hints = listOf("folia")),
                mismatchAction = UnsupportedServerVersionAction.WARN_ONLY,
            )

        assertTrue(compatible)
    }

    @Test
    fun `fully supported environment should pass`() {
        val compatible =
            verifier.verify(
                minecraftPolicy = SupportedServerVersions.range("1.19.4", "1.21.99"),
                minecraftResolution =
                    BukkitServerVersionResolution(
                        resolved = MinecraftVersion.parseOrThrow("1.20.4"),
                        candidates = listOf("1.20.4"),
                    ),
                minecraftMismatchAction = UnsupportedServerVersionAction.DISABLE_PLUGIN,
                runtimePolicy = SupportedBukkitRuntimes.only(BukkitRuntime.PAPER),
                runtimeResolution = BukkitRuntimeResolution(runtime = BukkitRuntime.PAPER, hints = listOf("paper")),
                runtimeMismatchAction = UnsupportedServerVersionAction.DISABLE_PLUGIN,
            )

        assertTrue(compatible)
    }
}
