package studio.singlethread.lib.operations

import org.bukkit.entity.Player
import studio.singlethread.lib.framework.api.command.CommandContext

class STLibOpenGuiExecutor(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
    private val logWarning: (String) -> Unit,
    private val isDashboardAvailable: () -> Boolean,
    private val dashboardUnavailableMessage: () -> String,
    private val openGui: (Player) -> Unit,
) {
    fun execute(
        context: CommandContext,
        player: Player?,
    ) {
        logInfo(
            translate(
                "stlib.log.gui_invoked",
                mapOf("sender" to context.senderName),
            ),
        )

        if (player == null) {
            context.reply(translate("stlib.command.player_only", emptyMap()))
            return
        }

        if (!isDashboardAvailable()) {
            context.reply(dashboardUnavailableMessage())
            return
        }

        runCatching {
            openGui(player)
        }.onFailure { error ->
            logWarning("Failed to open STPlugin dashboard: ${error.message}")
            context.reply(translate("stlib.gui.feedback.open_failed", emptyMap()))
        }
    }
}
