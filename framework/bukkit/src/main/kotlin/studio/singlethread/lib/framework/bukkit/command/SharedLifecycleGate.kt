package studio.singlethread.lib.framework.bukkit.command

import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIBukkitConfig
import org.bukkit.plugin.java.JavaPlugin

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

internal object CommandApiLifecycle {
    private val gate = SharedLifecycleGate()

    fun onLoad(plugin: JavaPlugin) {
        gate.onLoad {
            CommandAPI.onLoad(config(plugin))
        }
    }

    fun onEnable(plugin: JavaPlugin) {
        gate.onEnableEnsuringLoaded(
            loadAction = {
                CommandAPI.onLoad(config(plugin))
            },
        ) {
            CommandAPI.onEnable()
        }
    }

    fun onDisable() {
        gate.onDisable {
            CommandAPI.onDisable()
        }
    }

    private fun config(plugin: JavaPlugin): CommandAPIBukkitConfig {
        return CommandAPIBukkitConfig(plugin).silentLogs(true)
    }
}
