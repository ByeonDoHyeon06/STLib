package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.event.inventory.InventoryType

fun interface StGuiDefinition {
    fun StGuiBuilder.define()
}

fun interface StGuiPatternDefinition {
    fun StGuiPatternScope.define()
}

fun interface StGuiItemProvider {
    fun render(context: StGuiRenderContext): ItemStack?
}

fun interface StGuiClickHandler {
    fun onClick(context: StGuiClickContext)
}

fun interface StGuiOpenHandler {
    fun onOpen(context: StGuiOpenContext)
}

fun interface StGuiCloseHandler {
    fun onClose(context: StGuiCloseContext)
}

interface StGuiService : AutoCloseable {
    fun create(
        rows: Int,
        title: Component,
        definition: StGuiDefinition,
    ): StGui

    fun create(
        size: Int,
        title: Component,
        type: InventoryType,
        definition: StGuiDefinition,
    ): StGui

    fun open(
        player: Player,
        gui: StGui,
    )

    override fun close()
}

class StGuiRenderContext internal constructor(
    private val gui: StGui,
) {
    fun state(key: String): Any? {
        return gui.stateValue(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateAs(key: String): T? {
        return gui.stateValue(key) as? T
    }

    fun stateInt(key: String): Int? {
        val value = gui.stateValue(key)
        return (value as? Number)?.toInt()
    }
}

class StGuiOpenContext internal constructor(
    val player: Player,
    private val gui: StGui,
) {
    fun state(key: String): Any? {
        return gui.stateValue(key)
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        gui.updateState(key, value)
    }
}

class StGuiCloseContext internal constructor(
    val player: Player,
    private val gui: StGui,
) {
    fun state(key: String): Any? {
        return gui.stateValue(key)
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        gui.updateState(key, value)
    }
}

class StGuiClickContext internal constructor(
    val player: Player,
    val event: InventoryClickEvent,
    private val gui: StGui,
) {
    fun state(key: String): Any? {
        return gui.stateValue(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> stateAs(key: String): T? {
        return gui.stateValue(key) as? T
    }

    fun stateInt(key: String): Int? {
        val value = gui.stateValue(key)
        return (value as? Number)?.toInt()
    }

    fun state(
        key: String,
        value: Any?,
    ) {
        gui.updateState(key, value)
    }

    fun refresh() {
        gui.refresh(player)
    }

    fun reopen() {
        gui.reopen(player)
    }

    fun close() {
        player.closeInventory()
    }
}
