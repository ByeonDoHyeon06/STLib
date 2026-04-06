package studio.singlethread.lib.framework.bukkit.command

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import org.bukkit.plugin.java.JavaPlugin
import java.util.Properties

internal class SharedLifecycleGate {
    private val lock = Any()
    private var loaded = false
    private var enabled = false
    private var loadRefs = 0
    private var enableRefs = 0

    fun onLoad(loadAction: () -> Unit) {
        synchronized(lock) {
            if (!loaded) {
                loadAction()
                loaded = true
            }
            loadRefs++
        }
    }

    fun onEnable(enableAction: () -> Unit) {
        synchronized(lock) {
            check(loaded) { "shared lifecycle must be loaded before enable" }

            if (!enabled) {
                enableAction()
                enabled = true
            }
            enableRefs++
        }
    }

    fun onEnableEnsuringLoaded(
        loadAction: () -> Unit,
        enableAction: () -> Unit,
    ) {
        synchronized(lock) {
            if (!loaded) {
                loadAction()
                loaded = true
                if (loadRefs == 0) {
                    loadRefs = 1
                }
            }

            if (!enabled) {
                enableAction()
                enabled = true
            }
            enableRefs++
        }
    }

    fun onDisable(disableAction: () -> Unit) {
        var disableError: Throwable? = null
        synchronized(lock) {
            if (enableRefs > 0) {
                enableRefs--
            }

            if (enabled && enableRefs == 0) {
                runCatching(disableAction).onFailure { disableError = it }
                enabled = false
            }

            if (loadRefs > 0) {
                loadRefs--
            }
            if (loadRefs == 0) {
                loaded = false
            }
        }

        disableError?.let { throw it }
    }

    fun snapshot(): State {
        synchronized(lock) {
            return State(
                loaded = loaded,
                enabled = enabled,
                loadRefs = loadRefs,
                enableRefs = enableRefs,
            )
        }
    }

    data class State(
        val loaded: Boolean,
        val enabled: Boolean,
        val loadRefs: Int,
        val enableRefs: Int,
    )
}

internal class CommandApiRuntimeOwnershipGuard(
    private val runtimeId: String,
    private val properties: Properties = System.getProperties(),
    private val propertyKey: String = OWNER_PROPERTY_KEY,
) {
    fun claim(pluginName: String) {
        synchronized(properties) {
            val existing = properties.getProperty(propertyKey)
            if (existing == null) {
                properties.setProperty(propertyKey, runtimeId)
                return
            }

            check(existing == runtimeId) {
                "Detected multiple STLib runtime loaders for CommandAPI " +
                    "(plugin=$pluginName, ownerRuntime=$existing, currentRuntime=$runtimeId). " +
                    "Do not shade STLib into consumer plugins; use compileOnly + depend."
            }
        }
    }

    fun releaseWhenIdle(state: SharedLifecycleGate.State) {
        if (state.loadRefs > 0 || state.enableRefs > 0) {
            return
        }
        synchronized(properties) {
            if (properties.getProperty(propertyKey) == runtimeId) {
                properties.remove(propertyKey)
            }
        }
    }

    fun ownerRuntimeId(): String? {
        synchronized(properties) {
            return properties.getProperty(propertyKey)
        }
    }

    companion object {
        const val OWNER_PROPERTY_KEY: String = "studio.singlethread.lib.commandapi.runtime.owner"
    }
}

internal object CommandApiLifecycle {
    private val gate = SharedLifecycleGate()
    private val runtimeGuard =
        CommandApiRuntimeOwnershipGuard(
            runtimeId = buildRuntimeId(),
        )

    fun onLoad(plugin: JavaPlugin) {
        runtimeGuard.claim(plugin.name)
        gate.onLoad {
            CommandAPI.onLoad(config(plugin))
        }
    }

    fun onEnable(plugin: JavaPlugin) {
        runtimeGuard.claim(plugin.name)
        gate.onEnableEnsuringLoaded(
            loadAction = {
                CommandAPI.onLoad(config(plugin))
            },
        ) {
            CommandAPI.onEnable()
        }
    }

    fun onDisable() {
        var failure: Throwable? = null
        try {
            gate.onDisable {
                CommandAPI.onDisable()
            }
        } catch (error: Throwable) {
            failure = error
        } finally {
            runtimeGuard.releaseWhenIdle(gate.snapshot())
        }
        failure?.let { throw it }
    }

    private fun config(plugin: JavaPlugin): CommandAPIBukkitConfig {
        return CommandAPIBukkitConfig(plugin).silentLogs(true)
    }

    private fun buildRuntimeId(): String {
        val classLoader = CommandApiLifecycle::class.java.classLoader
        val classLoaderId = classLoader?.let(System::identityHashCode) ?: 0
        return "stlib-runtime-$classLoaderId"
    }
}
