package studio.singlethread.lib.framework.bukkit.lifecycle.support

import studio.singlethread.lib.framework.bukkit.version.BukkitRuntimeResolution
import studio.singlethread.lib.framework.bukkit.version.BukkitServerVersionResolution
import studio.singlethread.lib.framework.bukkit.version.SupportedBukkitRuntimes
import studio.singlethread.lib.framework.bukkit.version.SupportedServerVersions
import studio.singlethread.lib.framework.bukkit.version.UnsupportedServerVersionAction
import java.util.logging.Logger

internal class PluginCompatibilityVerifier(
    private val logger: Logger,
    private val pluginName: String,
    private val pluginVersion: String,
) {
    fun verify(
        minecraftPolicy: SupportedServerVersions,
        minecraftResolution: BukkitServerVersionResolution,
        minecraftMismatchAction: UnsupportedServerVersionAction,
        runtimePolicy: SupportedBukkitRuntimes,
        runtimeResolution: BukkitRuntimeResolution,
        runtimeMismatchAction: UnsupportedServerVersionAction,
    ): Boolean {
        if (!verifyMinecraft(minecraftPolicy, minecraftResolution, minecraftMismatchAction)) {
            return false
        }
        return verifyRuntime(runtimePolicy, runtimeResolution, runtimeMismatchAction)
    }

    fun verifyMinecraft(
        policy: SupportedServerVersions,
        resolved: BukkitServerVersionResolution,
        mismatchAction: UnsupportedServerVersionAction,
    ): Boolean {
        val serverVersion = resolved.resolved
        if (serverVersion == null) {
            logger.warning(
                "Unable to resolve minecraft version for '$pluginName'. " +
                    "Skipping compatibility gate. Candidates=${resolved.candidates}",
            )
            return true
        }

        if (policy.isSupported(serverVersion)) {
            return true
        }

        val baseMessage =
            "Unsupported minecraft version '$serverVersion' for '$pluginName' v$pluginVersion. " +
                "Supported versions: ${policy.describe()}"

        return when (mismatchAction) {
            UnsupportedServerVersionAction.WARN_ONLY -> {
                logger.warning(baseMessage)
                true
            }

            UnsupportedServerVersionAction.DISABLE_PLUGIN -> {
                logger.severe("$baseMessage. Plugin will remain disabled.")
                false
            }
        }
    }

    fun verifyRuntime(
        policy: SupportedBukkitRuntimes,
        resolved: BukkitRuntimeResolution,
        mismatchAction: UnsupportedServerVersionAction,
    ): Boolean {
        if (policy.isAny()) {
            return true
        }

        if (policy.isSupported(resolved.runtime)) {
            return true
        }

        val baseMessage =
            "Unsupported bukkit runtime '${resolved.runtime.name.lowercase()}' for '$pluginName' v$pluginVersion. " +
                "Supported runtimes: ${policy.describe()} (hints=${resolved.hints})"

        return when (mismatchAction) {
            UnsupportedServerVersionAction.WARN_ONLY -> {
                logger.warning(baseMessage)
                true
            }

            UnsupportedServerVersionAction.DISABLE_PLUGIN -> {
                logger.severe("$baseMessage. Plugin will remain disabled.")
                false
            }
        }
    }
}
