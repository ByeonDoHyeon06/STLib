package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.event.Listener
import studio.singlethread.lib.framework.bukkit.lifecycle.STPlugin

/**
 * Bukkit listener base with typed plugin access.
 *
 * Example:
 * `class JoinListener(plugin: MyPlugin) : STListener<MyPlugin>(plugin)`
 */
abstract class STListener<out P : STPlugin>(
    protected val plugin: P,
) : Listener

