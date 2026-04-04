package studio.singlethread.lib.framework.bukkit.gui

import org.bukkit.inventory.ItemStack
import java.util.LinkedHashMap

private val NOOP_CLICK_HANDLER = StGuiClickHandler { }

class StGuiBuilder internal constructor(
    private val rows: Int,
) {
    private data class StGuiPatternLayout(
        val symbolSlots: Map<Char, List<Int>>,
    )

    private val staticSlots = LinkedHashMap<Int, StGuiSlotBinding>()
    private val statePages = LinkedHashMap<String, MutableList<StGuiStatePageBinding>>()
    private val defaultStatePages = LinkedHashMap<String, StGuiBlueprint>()
    private val openHandlers = mutableListOf<StGuiOpenHandler>()
    private val closeHandlers = mutableListOf<StGuiCloseHandler>()
    private val globalClickHandlers = mutableListOf<StGuiClickHandler>()
    private val initialState = LinkedHashMap<String, Any>()
    private var patternLayout: StGuiPatternLayout? = null
    private val patternResolvedSymbols = linkedSetOf<Char>()
    private val patternEmptySymbols = linkedSetOf(' ')
    private var cancelAllClicks = true

    fun cancelAllClicks(enabled: Boolean = true) {
        cancelAllClicks = enabled
    }

    fun state(
        key: String,
        value: Any,
    ) {
        require(key.isNotBlank()) { "state key must not be blank" }
        initialState[key] = value
    }

    fun onOpen(handler: StGuiOpenHandler) {
        openHandlers += handler
    }

    fun onClose(handler: StGuiCloseHandler) {
        closeHandlers += handler
    }

    fun onClick(handler: StGuiClickHandler) {
        globalClickHandlers += handler
    }

    fun slot(
        slot: Int,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        slot(
            slot = slot,
            item = StGuiItemProvider { item?.clone() },
            onClick = onClick,
        )
    }

    fun slot(
        slot: Int,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        validateSlot(slot)
        staticSlots[slot] = StGuiSlotBinding(itemProvider = item, clickHandler = onClick)
    }

    fun set(
        slot: Int,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        slot(slot, item, onClick)
    }

    fun set(
        slot: Int,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        slot(slot, item, onClick)
    }

    fun set(
        row: Int,
        column: Int,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        set(row, column, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        row: Int,
        column: Int,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        validateRowColumn(row, column)
        set(slotIndex(row, column), item, onClick)
    }

    fun set(
        slots: Iterable<Int>,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        set(slots, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        slots: Iterable<Int>,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        slots.forEach { slot ->
            set(slot, item, onClick)
        }
    }

    fun set(
        symbol: Char,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        set(symbol, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        symbol: Char,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        require(symbol !in patternEmptySymbols) { "pattern symbol '$symbol' is registered as empty" }
        val mappedSlots = mappedPatternSlots(symbol)
        mappedSlots.forEach { slot ->
            set(slot, item, onClick)
        }
        patternResolvedSymbols += symbol
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
            StGuiPatternLayout(
                symbolSlots = symbolSlots.mapValues { (_, slots) -> slots.toList() },
            )
        patternResolvedSymbols.clear()
        patternEmptySymbols.clear()
        patternEmptySymbols += ' '
    }

    fun pattern(
        vararg lines: String,
        define: StGuiPatternDefinition,
    ) {
        pattern(*lines)
        val scope = StGuiPatternScope()
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
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        fill(StGuiItemProvider { item?.clone() }, onClick)
    }

    fun fill(
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        allSlots().forEach { slot ->
            this.slot(slot, item, onClick)
        }
    }

    fun border(
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        border(StGuiItemProvider { item?.clone() }, onClick)
    }

    fun border(
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        val width = 9
        val lastRow = rows - 1
        for (row in 0 until rows) {
            for (column in 0 until width) {
                if (row == 0 || row == lastRow || column == 0 || column == width - 1) {
                    slot(row * width + column, item, onClick)
                }
            }
        }
    }

    fun row(
        row: Int,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        row(row, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun row(
        row: Int,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        require(row in 0 until rows) { "row must be in range 0..${rows - 1}" }
        for (column in 0 until 9) {
            slot(row * 9 + column, item, onClick)
        }
    }

    fun column(
        column: Int,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        column(column, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun column(
        column: Int,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        require(column in 0..8) { "column must be in range 0..8" }
        for (row in 0 until rows) {
            slot(row * 9 + column, item, onClick)
        }
    }

    fun page(
        stateKey: String,
        stateValue: Any,
        define: StGuiDefinition,
    ) {
        require(stateKey.isNotBlank()) { "page state key must not be blank" }
        val pageBlueprint = createStatePageBlueprint(define)
        val bindings = statePages.computeIfAbsent(stateKey) { mutableListOf() }
        bindings.removeIf { it.stateValue == stateValue }
        bindings += StGuiStatePageBinding(stateValue = stateValue, blueprint = pageBlueprint)
    }

    fun pageDefault(
        stateKey: String,
        define: StGuiDefinition,
    ) {
        require(stateKey.isNotBlank()) { "page state key must not be blank" }
        defaultStatePages[stateKey] = createStatePageBlueprint(define)
    }

    internal fun build(): StGuiBlueprint {
        assertPatternResolved()
        val pageGroups = buildStatePageGroups()
        return StGuiBlueprint(
            rows = rows,
            cancelAllClicks = cancelAllClicks,
            initialState = initialState.toMap(),
            staticSlots = staticSlots.toMap(),
            statePages = pageGroups,
            openHandlers = openHandlers.toList(),
            closeHandlers = closeHandlers.toList(),
            globalClickHandlers = globalClickHandlers.toList(),
        )
    }

    private fun buildStatePageGroups(): List<StGuiStatePageGroup> {
        if (statePages.isEmpty() && defaultStatePages.isEmpty()) {
            return emptyList()
        }

        val keys = linkedSetOf<String>()
        keys += statePages.keys
        keys += defaultStatePages.keys

        return keys.map { stateKey ->
            StGuiStatePageGroup(
                stateKey = stateKey,
                pages = statePages[stateKey]?.toList().orEmpty(),
                defaultPage = defaultStatePages[stateKey],
            )
        }
    }

    private fun createStatePageBlueprint(define: StGuiDefinition): StGuiBlueprint {
        val pageBuilder = StGuiBuilder(rows)
        with(define) { pageBuilder.define() }
        return pageBuilder.build()
    }

    private fun allSlots(): IntRange {
        return 0 until rows * 9
    }

    private fun validateSlot(slot: Int) {
        require(slot in allSlots()) { "slot index out of bounds: $slot" }
    }

    private fun validateRowColumn(
        row: Int,
        column: Int,
    ) {
        require(row in 0 until rows) { "row must be in range 0..${rows - 1}" }
        require(column in 0..8) { "column must be in range 0..8" }
    }

    private fun slotIndex(
        row: Int,
        column: Int,
    ): Int {
        return row * 9 + column
    }

    private fun validatePattern(lines: Array<out String>) {
        require(lines.isNotEmpty()) { "pattern lines must not be empty" }
        require(lines.size <= rows) { "pattern row count must be <= $rows" }
        lines.forEachIndexed { index, line ->
            require(line.length <= 9) { "pattern line length must be <= 9 at row $index" }
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
            "pattern symbols are not mapped: ${unresolvedSymbols.joinToString(", ")}" }
    }
}

internal data class StGuiBlueprint(
    val rows: Int,
    val cancelAllClicks: Boolean,
    val initialState: Map<String, Any>,
    val staticSlots: Map<Int, StGuiSlotBinding>,
    val statePages: List<StGuiStatePageGroup>,
    val openHandlers: List<StGuiOpenHandler>,
    val closeHandlers: List<StGuiCloseHandler>,
    val globalClickHandlers: List<StGuiClickHandler>,
)

internal data class StGuiStatePageGroup(
    val stateKey: String,
    val pages: List<StGuiStatePageBinding>,
    val defaultPage: StGuiBlueprint?,
)

internal data class StGuiStatePageBinding(
    val stateValue: Any,
    val blueprint: StGuiBlueprint,
)

internal data class StGuiSlotBinding(
    val itemProvider: StGuiItemProvider,
    val clickHandler: StGuiClickHandler,
)

class StGuiPatternScope internal constructor() {
    private val bindings = LinkedHashMap<Char, StGuiSlotBinding>()
    private val emptySymbols = linkedSetOf(' ')

    fun key(
        symbol: Char,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        key(symbol, StGuiItemProvider { item?.clone() }, onClick)
    }

    fun set(
        symbol: Char,
        item: ItemStack?,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        key(symbol, item, onClick)
    }

    fun key(
        symbol: Char,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        require(symbol !in emptySymbols) { "symbol '$symbol' is registered as empty" }
        bindings[symbol] = StGuiSlotBinding(itemProvider = item, clickHandler = onClick)
    }

    fun set(
        symbol: Char,
        item: StGuiItemProvider,
        onClick: StGuiClickHandler = NOOP_CLICK_HANDLER,
    ) {
        key(symbol, item, onClick)
    }

    fun empty(symbol: Char) {
        require(symbol !in bindings) { "symbol '$symbol' is already bound" }
        emptySymbols += symbol
    }

    internal fun bindings(): Map<Char, StGuiSlotBinding> {
        return bindings.toMap()
    }

    internal fun emptySymbols(): Set<Char> {
        return emptySymbols.toSet()
    }
}
