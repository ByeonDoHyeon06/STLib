package studio.singlethread.lib.framework.bukkit.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

class STGui internal constructor(
    internal val title: Component,
    internal val size: Int,
    internal val type: InventoryType,
    private val blueprint: STGuiBlueprint,
    private val openRequest: (Player, STGui) -> InventoryView?,
    private val reportError: (phase: String, error: Throwable?) -> Unit,
) {
    fun open(player: Player): InventoryView? {
        return openRequest(player, this)
    }

    internal fun createSession(
        viewer: Player,
        inventory: Inventory,
    ): STGuiSession {
        return STGuiSession(
            viewer = viewer,
            inventory = inventory,
            blueprint = blueprint,
            reportError = reportError,
        )
    }
}

internal class STGuiSession(
    val viewer: Player,
    val inventory: Inventory,
    private val blueprint: STGuiBlueprint,
    private val reportError: (phase: String, error: Throwable?) -> Unit,
) {
    private val state = ConcurrentHashMap<String, STGuiStateValue>(blueprint.initialState)
    private val slotHandlers = ConcurrentHashMap<Int, STGuiClickHandler>()

    fun cancelAllClicks(): Boolean {
        return blueprint.cancelAllClicks
    }

    fun render() {
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, null)
        }
        slotHandlers.clear()

        val renderContext = STGuiRenderContext(viewer = viewer, session = this)
        renderBlueprint(
            target = blueprint,
            renderContext = renderContext,
            traversalPath = identitySet(),
        )

        viewer.updateInventory()
    }

    fun onOpen() {
        val context = STGuiOpenContext(player = viewer, session = this)
        forEachActiveBlueprint { active ->
            active.openHandlers.forEach { handler ->
                runCatching { handler.onOpen(context) }
                    .onFailure { error -> reportError("open handler", error) }
            }
        }
    }

    fun onClose() {
        val context = STGuiCloseContext(player = viewer, session = this)
        forEachActiveBlueprint { active ->
            active.closeHandlers.forEach { handler ->
                runCatching { handler.onClose(context) }
                    .onFailure { error -> reportError("close handler", error) }
            }
        }
    }

    fun handleGlobalClick(context: STGuiClickContext) {
        forEachActiveBlueprint { active ->
            active.globalClickHandlers.forEach { handler ->
                runCatching { handler.onClick(context) }
                    .onFailure { error -> reportError("global click handler", error) }
            }
        }
    }

    fun handleSlotClick(
        slot: Int,
        context: STGuiClickContext,
    ) {
        slotHandlers[slot]?.let { handler ->
            runCatching { handler.onClick(context) }
                .onFailure { error -> reportError("slot click handler", error) }
        }
    }

    fun stateValue(key: String): Any? {
        return state[key]?.value
    }

    fun <T : Any> stateValue(key: STGuiStateKey<T>): T? {
        return state[key.name]?.cast(key.type)
    }

    fun updateState(
        key: String,
        value: Any?,
    ) {
        if (value == null) {
            state.remove(key)
            return
        }
        state[key] = STGuiStateValue.from(value)
    }

    fun <T : Any> updateState(
        key: STGuiStateKey<T>,
        value: T?,
    ) {
        if (value == null) {
            state.remove(key.name)
            return
        }
        state[key.name] = STGuiStateValue.from(value, key)
    }

    fun refresh() {
        render()
    }

    fun reopen() {
        render()
        viewer.openInventory(inventory)
    }

    private fun renderBlueprint(
        target: STGuiBlueprint,
        renderContext: STGuiRenderContext,
        traversalPath: MutableSet<STGuiBlueprint>,
    ) {
        if (!traversalPath.add(target)) {
            reportError("view traversal cycle detected", null)
            return
        }
        try {
            target.staticSlots.forEach { (slot, binding) ->
                inventory.setItem(slot, binding.itemProvider.render(renderContext)?.clone())
                slotHandlers[slot] = binding.clickHandler
            }

            target.stateViews.forEach { group ->
                val selected =
                    group.views
                        .firstOrNull { binding -> binding.stateValue.matches(state[group.stateKey]) }
                        ?.blueprint
                if (selected != null) {
                    renderBlueprint(
                        target = selected,
                        renderContext = renderContext,
                        traversalPath = traversalPath,
                    )
                }
            }
        } finally {
            traversalPath.remove(target)
        }
    }

    private fun forEachActiveBlueprint(action: (STGuiBlueprint) -> Unit) {
        traverseActiveBlueprint(
            target = blueprint,
            action = action,
            traversalPath = identitySet(),
        )
    }

    private fun traverseActiveBlueprint(
        target: STGuiBlueprint,
        action: (STGuiBlueprint) -> Unit,
        traversalPath: MutableSet<STGuiBlueprint>,
    ) {
        if (!traversalPath.add(target)) {
            reportError("active blueprint traversal cycle detected", null)
            return
        }
        try {
            action(target)
            target.stateViews.forEach { group ->
                val selected =
                    group.views
                        .firstOrNull { binding -> binding.stateValue.matches(state[group.stateKey]) }
                        ?.blueprint
                if (selected != null) {
                    traverseActiveBlueprint(
                        target = selected,
                        action = action,
                        traversalPath = traversalPath,
                    )
                }
            }
        } finally {
            traversalPath.remove(target)
        }
    }

    private fun identitySet(): MutableSet<STGuiBlueprint> {
        return Collections.newSetFromMap(IdentityHashMap())
    }
}
