package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.InventoryView
import kotlin.reflect.KClass

fun interface STGuiDefinition {
    fun STGuiBuilder.define()
}

fun interface STGuiPatternDefinition {
    fun STGuiPatternScope.define()
}

fun interface STGuiItemProvider {
    fun render(context: STGuiRenderContext): ItemStack?
}

fun interface STGuiClickHandler {
    fun onClick(context: STGuiClickContext)
}

fun interface STGuiOpenHandler {
    fun onOpen(context: STGuiOpenContext)
}

fun interface STGuiCloseHandler {
    fun onClose(context: STGuiCloseContext)
}

class STGuiStateKey<T : Any> constructor(
    val name: String,
    internal val type: KClass<T>,
) {
    init {
        require(name.isNotBlank()) { "state key name must not be blank" }
    }

    override fun toString(): String {
        return "STGuiStateKey(name='$name', type=${type.qualifiedName})"
    }

    companion object {
        inline fun <reified T : Any> of(name: String): STGuiStateKey<T> {
            return STGuiStateKey(name = name, type = T::class)
        }
    }
}

inline fun <reified T : Any> stateKey(name: String): STGuiStateKey<T> {
    return STGuiStateKey.of(name)
}

interface STGuiService : AutoCloseable {
    fun create(
        title: Component,
        size: Int,
        type: InventoryType = InventoryType.CHEST,
        definition: STGuiDefinition,
    ): STGui

    fun open(
        player: Player,
        gui: STGui,
    )

    override fun close()
}

class STGuiRenderContext internal constructor(
    val viewer: Player,
    private val session: STGuiSession,
) {
    fun state(key: String): Any? {
        return session.stateValue(key)
    }

    fun <T : Any> state(key: STGuiStateKey<T>): T? {
        return session.stateValue(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateAs(key: String): T? {
        return session.stateValue(key) as? T
    }

    fun stateInt(key: String): Int? {
        val value = session.stateValue(key)
        return (value as? Number)?.toInt()
    }
}

class STGuiOpenContext internal constructor(
    val player: Player,
    private val session: STGuiSession,
) {
    val viewer: Player
        get() = player

    fun state(key: String): Any? {
        return session.stateValue(key)
    }

    fun <T : Any> state(key: STGuiStateKey<T>): T? {
        return session.stateValue(key)
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        session.updateState(key, value)
    }

    fun <T : Any> state(
        key: STGuiStateKey<T>,
        value: T?,
    ) {
        session.updateState(key, value)
    }
}

class STGuiCloseContext internal constructor(
    val player: Player,
    private val session: STGuiSession,
) {
    val viewer: Player
        get() = player

    fun state(key: String): Any? {
        return session.stateValue(key)
    }

    fun <T : Any> state(key: STGuiStateKey<T>): T? {
        return session.stateValue(key)
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        session.updateState(key, value)
    }

    fun <T : Any> state(
        key: STGuiStateKey<T>,
        value: T?,
    ) {
        session.updateState(key, value)
    }
}

class STGuiClickContext internal constructor(
    val player: Player,
    val event: InventoryClickEvent,
    private val session: STGuiSession,
) {
    val viewer: Player
        get() = player

    var isCancelled: Boolean
        get() = event.isCancelled
        set(value) {
            event.isCancelled = value
        }

    fun state(key: String): Any? {
        return session.stateValue(key)
    }

    fun <T : Any> state(key: STGuiStateKey<T>): T? {
        return session.stateValue(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateAs(key: String): T? {
        return session.stateValue(key) as? T
    }

    fun stateInt(key: String): Int? {
        val value = session.stateValue(key)
        return (value as? Number)?.toInt()
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        session.updateState(key, value)
    }

    fun <T : Any> state(
        key: STGuiStateKey<T>,
        value: T?,
    ) {
        session.updateState(key, value)
    }

    fun refresh() {
        session.refresh()
    }

    fun show(
        key: String,
        value: Any?,
    ) {
        state(key, value)
        refresh()
    }

    fun <T : Any> show(
        key: STGuiStateKey<T>,
        value: T?,
    ) {
        state(key, value)
        refresh()
    }

    fun toggle(
        key: String,
        first: Any,
        second: Any,
    ): Any {
        val current = state(key)
        val next = if (current == first) second else first
        show(key, next)
        return next
    }

    fun cancel() {
        isCancelled = true
    }

    fun allow() {
        isCancelled = false
    }

    fun reopen() {
        session.reopen()
    }

    fun close() {
        player.closeInventory()
    }
}

fun Player.openInventory(gui: STGui): InventoryView? {
    return gui.open(this)
}

fun STGuiService.menu(
    title: Component,
    size: Int,
    type: InventoryType = InventoryType.CHEST,
    definition: STGuiDefinition,
): STGui {
    return create(
        title = title,
        size = size,
        type = type,
        definition = definition,
    )
}
