package studio.singlethread.lib.framework.bukkit.gui

import org.bukkit.inventory.ItemStack
import java.util.LinkedHashMap

class STGuiBuilder internal constructor(
    private val size: Int,
) {
    private data class STGuiPatternLayout(
        val symbolSlots: Map<Char, List<Int>>,
    )

    private val staticSlots = LinkedHashMap<Int, STGuiSlotBinding>()
    private val stateViews = LinkedHashMap<String, MutableList<STGuiStateViewBinding>>()
    private val openHandlers = mutableListOf<STGuiOpenHandler>()
    private val closeHandlers = mutableListOf<STGuiCloseHandler>()
    private val globalClickHandlers = mutableListOf<STGuiClickHandler>()
    private val initialState = LinkedHashMap<String, STGuiStateValue>()
    private var patternLayout: STGuiPatternLayout? = null
    private val patternResolvedSymbols = linkedSetOf<Char>()
    private val patternEmptySymbols = linkedSetOf(' ')
    private var cancelAllClicks = true

    init {
        require(size > 0) { "size must be > 0" }
    }

    fun cancelAllClicks(enabled: Boolean = true) {
        cancelAllClicks = enabled
    }

    fun state(
        key: String,
        value: Any,
    ) {
        require(key.isNotBlank()) { "state key must not be blank" }
        initialState[key] = STGuiStateValue.from(value)
    }

    fun <T : Any> state(
        key: STGuiStateKey<T>,
        value: T,
    ) {
        initialState[key.name] = STGuiStateValue.from(value, key)
    }

    fun onOpen(handler: STGuiOpenHandler) {
        openHandlers += handler
    }

    fun onClose(handler: STGuiCloseHandler) {
        closeHandlers += handler
    }

    fun onClick(handler: STGuiClickHandler) {
        globalClickHandlers += handler
    }

    fun slot(
        slot: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        slot(
            slot = slot,
            item = STGuiItemProvider { item?.clone() },
            onClick = onClick,
        )
    }

    fun slot(
        slot: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        validateSlot(slot)
        staticSlots[slot] = STGuiSlotBinding(itemProvider = item, clickHandler = onClick)
    }

    fun set(
        slot: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        slot(slot, item, onClick)
    }

    fun set(
        slot: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        slot(slot, item, onClick)
    }

    fun set(
        row: Int,
        column: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        set(row, column, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        row: Int,
        column: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        validateRowColumn(row, column)
        set(slotIndex(row, column), item, onClick)
    }

    fun set(
        slots: Iterable<Int>,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        set(slots, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        vararg slots: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        set(slots = slots.asList(), item = item, onClick = onClick)
    }

    fun set(
        vararg slots: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        set(slots = slots.asList(), item = item, onClick = onClick)
    }

    fun set(
        slots: Iterable<Int>,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        slots.forEach { slot ->
            set(slot, item, onClick)
        }
    }

    fun set(
        symbol: Char,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        set(symbol, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        symbol: Char,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        require(symbol !in patternEmptySymbols) { "pattern symbol '$symbol' is registered as empty" }
        val mappedSlots = mappedPatternSlots(symbol)
        mappedSlots.forEach { slot ->
            set(slot, item, onClick)
        }
        patternResolvedSymbols += symbol
    }

    fun setSymbols(
        symbols: Iterable<Char>,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        setSymbols(symbols = symbols, item = STGuiItemProvider { item?.clone() }, onClick = onClick)
    }

    fun setSymbols(
        symbols: Iterable<Char>,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        symbols.forEach { symbol ->
            set(symbol, item, onClick)
        }
    }

    fun set(
        vararg symbols: Char,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        setSymbols(symbols = symbols.asList(), item = item, onClick = onClick)
    }

    fun set(
        vararg symbols: Char,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        setSymbols(symbols = symbols.asList(), item = item, onClick = onClick)
    }

    fun empty(symbol: Char) {
        require(mappedPatternSymbols().contains(symbol)) {
            "pattern symbol '$symbol' is not present in current pattern"
        }
        patternEmptySymbols += symbol
    }

    fun pattern(
        vararg lines: String,
    ) {
        validatePattern(lines)
        val symbolSlots = LinkedHashMap<Char, MutableList<Int>>()
        lines.forEachIndexed { row, line ->
            line.forEachIndexed { column, symbol ->
                val slot = slotIndex(row, column)
                symbolSlots.computeIfAbsent(symbol) { mutableListOf() }.add(slot)
            }
        }
        patternLayout =
            STGuiPatternLayout(
                symbolSlots = symbolSlots.mapValues { (_, slots) -> slots.toList() },
            )
        patternResolvedSymbols.clear()
        patternEmptySymbols.clear()
        patternEmptySymbols += ' '
    }

    fun pattern(
        vararg lines: String,
        define: STGuiPatternDefinition,
    ) {
        pattern(*lines)
        val scope = STGuiPatternScope()
        with(define) { scope.define() }
        scope.bindings().forEach { (symbol, binding) ->
            set(symbol, binding.itemProvider, binding.clickHandler)
        }
        scope.emptySymbols().forEach { symbol ->
            empty(symbol)
        }
    }

    fun fill(
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        fill(STGuiItemProvider { item?.clone() }, onClick)
    }

    fun fill(
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        allSlots().forEach { slot ->
            this.slot(slot, item, onClick)
        }
    }

    fun border(
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        border(STGuiItemProvider { item?.clone() }, onClick)
    }

    fun border(
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        val lastRow = maxRows() - 1
        for (row in 0 until maxRows()) {
            for (column in 0 until 9) {
                val slot = slotIndex(row, column)
                if (slot !in allSlots()) {
                    continue
                }
                if (row == 0 || row == lastRow || column == 0 || column == 8) {
                    slot(slot, item, onClick)
                }
            }
        }
    }

    fun row(
        row: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        row(row, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun row(
        row: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        require(row in 0 until maxRows()) { "row must be in range 0..${maxRows() - 1}" }
        val start = row * 9
        val end = minOf(start + 8, size - 1)
        if (start > end) {
            return
        }
        for (slot in start..end) {
            slot(slot, item, onClick)
        }
    }

    fun column(
        column: Int,
        item: ItemStack?,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        column(column, STGuiItemProvider { item?.clone() }, onClick)
    }

    fun column(
        column: Int,
        item: STGuiItemProvider,
        onClick: STGuiClickHandler = DEFAULT_GUI_NOOP_CLICK_HANDLER,
    ) {
        require(column in 0..8) { "column must be in range 0..8" }
        for (row in 0 until maxRows()) {
            val slot = row * 9 + column
            if (slot in allSlots()) {
                slot(slot, item, onClick)
            }
        }
    }

    fun view(
        stateKey: String,
        stateValue: Any,
        define: STGuiDefinition,
    ) {
        require(stateKey.isNotBlank()) { "view state key must not be blank" }
        val stateBinding = STGuiStateValue.from(stateValue)
        val viewBlueprint = createStateViewBlueprint(define)
        val bindings = stateViews.computeIfAbsent(stateKey) { mutableListOf() }
        bindings.removeIf { it.stateValue.matches(stateBinding) }
        bindings += STGuiStateViewBinding(stateValue = stateBinding, blueprint = viewBlueprint)
    }

    fun view(
        stateKey: String,
        stateValues: Iterable<Any>,
        define: STGuiDefinition,
    ) {
        val values = stateValues.toList()
        require(values.isNotEmpty()) { "view state values must not be empty" }
        values.forEach { stateValue ->
            view(
                stateKey = stateKey,
                stateValue = stateValue,
                define = define,
            )
        }
    }

    fun view(
        stateKey: String,
        vararg stateValues: Any,
        define: STGuiDefinition,
    ) {
        view(
            stateKey = stateKey,
            stateValues = stateValues.asList(),
            define = define,
        )
    }

    fun <T : Any> view(
        stateKey: STGuiStateKey<T>,
        stateValue: T,
        define: STGuiDefinition,
    ) {
        val stateBinding = STGuiStateValue.from(stateValue, stateKey)
        val viewBlueprint = createStateViewBlueprint(define)
        val bindings = stateViews.computeIfAbsent(stateKey.name) { mutableListOf() }
        bindings.removeIf { it.stateValue.matches(stateBinding) }
        bindings += STGuiStateViewBinding(stateValue = stateBinding, blueprint = viewBlueprint)
    }

    fun <T : Any> view(
        stateKey: STGuiStateKey<T>,
        stateValues: Iterable<T>,
        define: STGuiDefinition,
    ) {
        val values = stateValues.toList()
        require(values.isNotEmpty()) { "view state values must not be empty" }
        values.forEach { stateValue ->
            view(
                stateKey = stateKey,
                stateValue = stateValue,
                define = define,
            )
        }
    }

    fun <T : Any> view(
        stateKey: STGuiStateKey<T>,
        vararg stateValues: T,
        define: STGuiDefinition,
    ) {
        view(
            stateKey = stateKey,
            stateValues = stateValues.asList(),
            define = define,
        )
    }

    internal fun build(): STGuiBlueprint {
        assertPatternResolved()
        val viewGroups = buildStateViewGroups()
        return STGuiBlueprint(
            size = size,
            cancelAllClicks = cancelAllClicks,
            initialState = initialState.toMap(),
            staticSlots = staticSlots.toMap(),
            stateViews = viewGroups,
            openHandlers = openHandlers.toList(),
            closeHandlers = closeHandlers.toList(),
            globalClickHandlers = globalClickHandlers.toList(),
        )
    }

    private fun buildStateViewGroups(): List<STGuiStateViewGroup> {
        if (stateViews.isEmpty()) {
            return emptyList()
        }

        return stateViews
            .map { (stateKey, views) ->
                STGuiStateViewGroup(
                    stateKey = stateKey,
                    views = views.toList(),
                )
            }
    }

    private fun createStateViewBlueprint(define: STGuiDefinition): STGuiBlueprint {
        val viewBuilder = STGuiBuilder(size)
        with(define) { viewBuilder.define() }
        return viewBuilder.build()
    }

    private fun allSlots(): IntRange {
        return 0 until size
    }

    private fun maxRows(): Int {
        return ((size - 1) / 9) + 1
    }

    private fun validateSlot(slot: Int) {
        require(slot in allSlots()) { "slot index out of bounds: $slot" }
    }

    private fun validateRowColumn(
        row: Int,
        column: Int,
    ) {
        require(row in 0 until maxRows()) { "row must be in range 0..${maxRows() - 1}" }
        require(column in 0..8) { "column must be in range 0..8" }
        require(slotIndex(row, column) in allSlots()) {
            "row=$row column=$column is out of bounds for inventory size $size"
        }
    }

    private fun slotIndex(
        row: Int,
        column: Int,
    ): Int {
        return row * 9 + column
    }

    private fun validatePattern(lines: Array<out String>) {
        require(lines.isNotEmpty()) { "pattern lines must not be empty" }
        require(lines.size <= maxRows()) { "pattern row count must be <= ${maxRows()}" }
        lines.forEachIndexed { row, line ->
            require(line.length <= 9) { "pattern line length must be <= 9 at row $row" }
            line.forEachIndexed { column, _ ->
                val slot = slotIndex(row, column)
                require(slot in allSlots()) {
                    "pattern cell row=$row column=$column exceeds inventory size $size"
                }
            }
        }
    }

    private fun mappedPatternSlots(symbol: Char): List<Int> {
        val layout = patternLayout ?: error("pattern is not defined, call pattern(...) first")
        val slots = layout.symbolSlots[symbol]
        require(!slots.isNullOrEmpty()) {
            "pattern symbol '$symbol' is not present in current pattern"
        }
        return slots
    }

    private fun mappedPatternSymbols(): Set<Char> {
        return patternLayout?.symbolSlots?.keys ?: emptySet()
    }

    private fun assertPatternResolved() {
        val layout = patternLayout ?: return
        val unresolvedSymbols =
            layout.symbolSlots
                .keys
                .filterNot { symbol -> symbol in patternResolvedSymbols || symbol in patternEmptySymbols }
        require(unresolvedSymbols.isEmpty()) {
            "pattern symbols are not mapped: ${unresolvedSymbols.joinToString(", ")}"
        }
    }
}
