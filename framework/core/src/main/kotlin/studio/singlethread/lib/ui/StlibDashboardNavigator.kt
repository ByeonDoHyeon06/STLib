package studio.singlethread.lib.ui

import org.bukkit.entity.Player
import studio.singlethread.lib.dashboard.StlibDashboardService
import studio.singlethread.lib.framework.bukkit.gui.StGui
import studio.singlethread.lib.framework.bukkit.gui.StGuiDefinition
import java.time.Instant

class StlibDashboardNavigator(
    private val createGui: (Int, String, Map<String, String>, StGuiDefinition) -> StGui,
    private val openGui: (Player, StGui) -> Unit,
    private val dashboardService: StlibDashboardService,
    private val menuItemFactory: StlibDashboardMenuItemFactory,
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val formatInstant: (Instant?) -> String,
    private val notifyPlayer: (player: Player, message: String) -> Unit,
) {
    fun openList(
        player: Player,
        requestedPage: Int = 0,
    ) {
        val entries = dashboardService.entries()
        val pages = if (entries.isEmpty()) listOf(emptyList()) else entries.chunked(45)
        val totalPages = pages.size
        val currentPage = requestedPage.coerceIn(0, totalPages - 1)
        val pageEntries = pages[currentPage]

        val title =
            translate(
                "stlib.gui.title.list",
                mapOf(
                    "page" to (currentPage + 1).toString(),
                    "total" to totalPages.toString(),
                ),
            )
        val menu =
            createGui(
                6,
                title,
                emptyMap(),
                StGuiDefinition {
                    pageEntries.forEachIndexed { index, entry ->
                        slot(index, menuItemFactory.pluginListItem(entry)) { click ->
                            val viewer = click.event.whoClicked as? Player ?: return@slot
                            openDetail(
                                player = viewer,
                                pluginName = entry.name,
                                returnPage = currentPage,
                            )
                        }
                    }

                    if (pageEntries.isEmpty()) {
                        slot(22, menuItemFactory.emptyStateItem())
                    }

                    if (currentPage > 0) {
                        slot(45, menuItemFactory.previousPageItem()) { click ->
                            val viewer = click.event.whoClicked as? Player ?: return@slot
                            openList(viewer, currentPage - 1)
                        }
                    }

                    slot(49, menuItemFactory.refreshItem()) { click ->
                        val viewer = click.event.whoClicked as? Player ?: return@slot
                        openList(viewer, currentPage)
                    }

                    slot(50, menuItemFactory.closeItem()) { click ->
                        val viewer = click.event.whoClicked as? Player ?: return@slot
                        viewer.closeInventory()
                    }

                    if (currentPage < totalPages - 1) {
                        slot(53, menuItemFactory.nextPageItem()) { click ->
                            val viewer = click.event.whoClicked as? Player ?: return@slot
                            openList(viewer, currentPage + 1)
                        }
                    }
                },
            )
        openGui(player, menu)
    }

    private fun openDetail(
        player: Player,
        pluginName: String,
        returnPage: Int,
    ) {
        val entry = dashboardService.entry(pluginName)
        if (entry == null) {
            notifyPlayer(
                player,
                translate("stlib.gui.feedback.not_found", mapOf("plugin" to pluginName)),
            )
            openList(player, returnPage)
            return
        }

        val menu =
            createGui(
                6,
                translate("stlib.gui.title.detail", mapOf("name" to entry.name)),
                emptyMap(),
                StGuiDefinition {
                    slot(10, menuItemFactory.detailIdentityItem(entry, formatInstant))
                    slot(12, menuItemFactory.detailLifecycleItem(entry, formatInstant))
                    slot(14, menuItemFactory.detailCapabilityItem(entry, formatInstant))
                    slot(16, menuItemFactory.detailHealthItem(entry, formatInstant))

                    slot(45, menuItemFactory.backItem()) { click ->
                        val viewer = click.event.whoClicked as? Player ?: return@slot
                        openList(viewer, returnPage)
                    }
                    slot(49, menuItemFactory.refreshItem()) { click ->
                        val viewer = click.event.whoClicked as? Player ?: return@slot
                        openDetail(viewer, entry.name, returnPage)
                    }
                    slot(50, menuItemFactory.closeItem()) { click ->
                        val viewer = click.event.whoClicked as? Player ?: return@slot
                        viewer.closeInventory()
                    }
                },
            )

        openGui(player, menu)
    }
}
