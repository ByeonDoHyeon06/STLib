package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Base class for ST custom Bukkit events.
 *
 * Subclasses must provide their own handler list:
 * ```
 * class ExampleEvent : STEvent() {
 *     companion object {
 *         @JvmStatic
 *         private val HANDLERS = HandlerList()
 *
 *         @JvmStatic
 *         fun getHandlerList(): HandlerList = HANDLERS
 *     }
 *
 *     override fun getHandlers(): HandlerList = HANDLERS
 * }
 * ```
 */
abstract class STEvent(
    isAsync: Boolean = false,
) : Event(isAsync) {
    abstract override fun getHandlers(): HandlerList
}

