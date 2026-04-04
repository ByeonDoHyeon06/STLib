package studio.singlethread.lib.framework.bukkit.gui

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

class StGui internal constructor(
    val inventory: Inventory,
    private val blueprint: StGuiBlueprint,
    private val renderRequest: (StGui, Player?) -> Unit,
    private val reportError: (phase: String, error: Throwable?) -> Unit,
) {
    private val state = ConcurrentHashMap<String, Any>(blueprint.initialState)
    private val slotHandlers = ConcurrentHashMap<Int, StGuiClickHandler>()

    internal fun cancelAllClicks(): Boolean {
        return blueprint.cancelAllClicks
    }

    internal fun render(viewer: Player?) {
        for (slot in 0 until inventory.size) {
            inventory.setItem(slot, null)
        }
        slotHandlers.clear()

        val renderContext = StGuiRenderContext(this)
        renderBlueprint(
            target = blueprint,
            renderContext = renderContext,
            traversalPath = identitySet(),
        )

        viewer?.updateInventory()
    }

    internal fun onOpen(player: Player) {
        val context = StGuiOpenContext(player = player, gui = this)
        forEachActiveBlueprint { active ->
            active.openHandlers.forEach { handler ->
                runCatching { handler.onOpen(context) }
                    .onFailure { error -> reportError("open handler", error) }
            }
        }
    }

    internal fun onClose(player: Player) {
        val context = StGuiCloseContext(player = player, gui = this)
        forEachActiveBlueprint { active ->
            active.closeHandlers.forEach { handler ->
                runCatching { handler.onClose(context) }
                    .onFailure { error -> reportError("close handler", error) }
            }
        }
    }

    internal fun handleGlobalClick(context: StGuiClickContext) {
        forEachActiveBlueprint { active ->
            active.globalClickHandlers.forEach { handler ->
                runCatching { handler.onClick(context) }
                    .onFailure { error -> reportError("global click handler", error) }
            }
        }
    }

    internal fun handleSlotClick(
        slot: Int,
        context: StGuiClickContext,
    ) {
        slotHandlers[slot]?.let { handler ->
            runCatching { handler.onClick(context) }
                .onFailure { error -> reportError("slot click handler", error) }
        }
    }

    internal fun stateValue(key: String): Any? {
        return state[key]
    }

    internal fun updateState(
        key: String,
        value: Any?,
    ) {
        if (value == null) {
            state.remove(key)
            return
        }
        state[key] = value
    }

    internal fun refresh(player: Player?) {
        renderRequest(this, player)
    }

    internal fun reopen(player: Player) {
        renderRequest(this, player)
        player.openInventory(inventory)
    }

    private fun renderBlueprint(
        target: StGuiBlueprint,
        renderContext: StGuiRenderContext,
        traversalPath: MutableSet<StGuiBlueprint>,
    ) {
        if (!traversalPath.add(target)) {
            reportError("page traversal cycle detected", null)
            return
        }
        try {
            target.staticSlots.forEach { (slot, binding) ->
                inventory.setItem(slot, binding.itemProvider.render(renderContext)?.clone())
                slotHandlers[slot] = binding.clickHandler
            }

            target.statePages.forEach { group ->
                val selected =
                    group.pages
                        .firstOrNull { binding -> binding.stateValue == state[group.stateKey] }
                        ?.blueprint
                        ?: group.defaultPage
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

    private fun forEachActiveBlueprint(action: (StGuiBlueprint) -> Unit) {
        traverseActiveBlueprint(
            target = blueprint,
            action = action,
            traversalPath = identitySet(),
        )
    }

    private fun traverseActiveBlueprint(
        target: StGuiBlueprint,
        action: (StGuiBlueprint) -> Unit,
        traversalPath: MutableSet<StGuiBlueprint>,
    ) {
        if (!traversalPath.add(target)) {
            reportError("active blueprint traversal cycle detected", null)
            return
        }
        try {
            action(target)
            target.statePages.forEach { group ->
                val selected =
                    group.pages
                        .firstOrNull { binding -> binding.stateValue == state[group.stateKey] }
                        ?.blueprint
                        ?: group.defaultPage
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

    private fun identitySet(): MutableSet<StGuiBlueprint> {
        return Collections.newSetFromMap(IdentityHashMap())
    }
}
