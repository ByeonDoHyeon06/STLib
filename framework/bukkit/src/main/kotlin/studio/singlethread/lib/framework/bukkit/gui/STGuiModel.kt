package studio.singlethread.lib.framework.bukkit.gui

import org.bukkit.inventory.ItemStack
import java.util.LinkedHashMap
import kotlin.reflect.KClass

internal val DEFAULT_GUI_NOOP_CLICK_HANDLER = STGuiClickHandler { }

internal data class STGuiBlueprint(
    val size: Int,
    val cancelAllClicks: Boolean,
    val initialState: Map<String, STGuiStateValue>,
    val staticSlots: Map<Int, STGuiSlotBinding>,
    val stateViews: List<STGuiStateViewGroup>,
    val openHandlers: List<STGuiOpenHandler>,
    val closeHandlers: List<STGuiCloseHandler>,
    val globalClickHandlers: List<STGuiClickHandler>,
)

internal data class STGuiStateViewGroup(
    val stateKey: String,
    val views: List<STGuiStateViewBinding>,
)

internal data class STGuiStateViewBinding(
    val stateValue: STGuiStateValue,
    val blueprint: STGuiBlueprint,
)

internal data class STGuiStateValue(
    val value: Any,
    val type: KClass<*>,
) {
    fun matches(other: STGuiStateValue?): Boolean {
        if (other == null) {
            return false
        }
        return value == other.value && type == other.type
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cast(targetType: KClass<T>): T? {
        if (!targetType.isInstance(value)) {
            return null
        }
        return value as T
    }

    companion object {
        fun from(value: Any): STGuiStateValue {
            return STGuiStateValue(value = value, type = value::class)
        }

        fun <T : Any> from(
            value: T,
            key: STGuiStateKey<T>,
        ): STGuiStateValue {
            return STGuiStateValue(value = value, type = key.type)
        }
    }
}

internal data class STGuiSlotBinding(
    val itemProvider: STGuiItemProvider,
    val clickHandler: STGuiClickHandler,
)

class STGuiPatternScope internal constructor() {
    private val bindings = LinkedHashMap<Char, STGuiSlotBinding>()
    private val emptySymbols = linkedSetOf(' ')

    fun key(
        symbol: Char,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        key(symbol, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        symbol: Char,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        key(symbol, item, onClick)
    }

    fun key(
        symbol: Char,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        require(symbol !in emptySymbols) { "symbol '$symbol' is registered as empty" }
        bindings[symbol] = STGuiSlotBinding(itemProvider = item, clickHandler = onClick)
    }

    fun set(
        symbol: Char,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        key(symbol, item, onClick)
    }

    fun empty(symbol: Char) {
        require(symbol !in bindings) { "symbol '$symbol' is already bound" }
        emptySymbols += symbol
    }

    internal fun bindings(): Map<Char, STGuiSlotBinding> {
        return bindings.toMap()
    }

    internal fun emptySymbols(): Set<Char> {
        return emptySymbols.toSet()
    }
}
