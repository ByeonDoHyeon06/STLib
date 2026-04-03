package studio.singlethread.lib.ui

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import studio.singlethread.lib.dashboard.StlibDashboardEntry
import studio.singlethread.lib.dashboard.StlibDashboardHealthLevel
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus

class StlibDashboardMenuItemFactory(
    private val parse: (message: String, placeholders: Map<String, String>) -> Component,
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
) {
    fun pluginListItem(entry: StlibDashboardEntry): ItemStack {
        val item = ItemStack(statusMaterial(entry.status))
        val statusLabel = statusLabel(entry.status)
        val capabilitySummary = "${entry.capabilityEnabledCount}/${entry.capabilityDisabledCount}"

        item.editMeta { meta ->
            meta.displayName(
                parse(
                    translate("stlib.gui.plugin_name", mapOf("name" to entry.name)),
                    mapOf("name" to entry.name),
                ),
            )
            meta.lore(
                listOf(
                    parse(
                        translate("stlib.gui.lore.version", mapOf("version" to entry.version)),
                        mapOf("version" to entry.version),
                    ),
                    parse(
                        translate("stlib.gui.lore.runtime_status", mapOf("status" to statusLabel)),
                        mapOf("status" to statusLabel),
                    ),
                    parse(
                        translate("stlib.gui.lore.uptime", mapOf("uptime" to durationLabel(entry))),
                        mapOf("uptime" to durationLabel(entry)),
                    ),
                    parse(
                        translate("stlib.gui.lore.capability_summary", mapOf("summary" to capabilitySummary)),
                        mapOf("summary" to capabilitySummary),
                    ),
                    parse("<dark_gray>----------------</dark_gray>", emptyMap()),
                    parse(translate("stlib.gui.lore.detail_open", emptyMap()), emptyMap()),
                ),
            )
        }
        return item
    }

    fun detailIdentityItem(
        entry: StlibDashboardEntry,
        formatInstant: (java.time.Instant?) -> String,
    ): ItemStack {
        val item = ItemStack(Material.NAME_TAG)
        val mainClassPlaceholders = mapOf(
            "main_class" to entry.mainClass,
            "mainClass" to entry.mainClass,
        )
        item.editMeta { meta ->
            meta.displayName(parse(translate("stlib.gui.detail.identity_title", emptyMap()), emptyMap()))
            meta.lore(
                listOf(
                    parse(
                        translate("stlib.gui.lore.version", mapOf("version" to entry.version)),
                        mapOf("version" to entry.version),
                    ),
                    parse(
                        translate("stlib.gui.detail.main_class", mainClassPlaceholders),
                        mainClassPlaceholders,
                    ),
                    parse(
                        translate("stlib.gui.detail.loaded_at", mapOf("time" to formatInstant(entry.loadedAt))),
                        mapOf("time" to formatInstant(entry.loadedAt)),
                    ),
                ),
            )
        }
        return item
    }

    fun detailLifecycleItem(
        entry: StlibDashboardEntry,
        formatInstant: (java.time.Instant?) -> String,
    ): ItemStack {
        val item = ItemStack(Material.CLOCK)
        val statusLabel = statusLabel(entry.status)
        item.editMeta { meta ->
            meta.displayName(parse(translate("stlib.gui.detail.lifecycle_title", emptyMap()), emptyMap()))
            meta.lore(
                listOf(
                    parse(
                        translate("stlib.gui.lore.runtime_status", mapOf("status" to statusLabel)),
                        mapOf("status" to statusLabel),
                    ),
                    parse(
                        translate("stlib.gui.detail.enabled_at", mapOf("time" to formatInstant(entry.enabledAt))),
                        mapOf("time" to formatInstant(entry.enabledAt)),
                    ),
                    parse(
                        translate("stlib.gui.detail.disabled_at", mapOf("time" to formatInstant(entry.disabledAt))),
                        mapOf("time" to formatInstant(entry.disabledAt)),
                    ),
                    parse(
                        translate("stlib.gui.detail.total_enable_count", mapOf("count" to entry.totalEnableCount.toString())),
                        mapOf("count" to entry.totalEnableCount.toString()),
                    ),
                    parse(
                        translate("stlib.gui.detail.total_disable_count", mapOf("count" to entry.totalDisableCount.toString())),
                        mapOf("count" to entry.totalDisableCount.toString()),
                    ),
                ),
            )
        }
        return item
    }

    fun detailCapabilityItem(
        entry: StlibDashboardEntry,
        formatInstant: (java.time.Instant?) -> String,
    ): ItemStack {
        val item = ItemStack(Material.BOOK)
        val summary = "${entry.capabilityEnabledCount}/${entry.capabilityDisabledCount}"
        item.editMeta { meta ->
            meta.displayName(parse(translate("stlib.gui.detail.capability_title", emptyMap()), emptyMap()))
            meta.lore(
                listOf(
                    parse(
                        translate("stlib.gui.lore.capability_summary", mapOf("summary" to summary)),
                        mapOf("summary" to summary),
                    ),
                    parse(
                        translate("stlib.gui.detail.capability_updated_at", mapOf("time" to formatInstant(entry.capabilityUpdatedAt))),
                        mapOf("time" to formatInstant(entry.capabilityUpdatedAt)),
                    ),
                ),
            )
        }
        return item
    }

    fun detailHealthItem(
        entry: StlibDashboardEntry,
        formatInstant: (java.time.Instant?) -> String,
    ): ItemStack {
        val item = ItemStack(Material.COMPASS)
        val healthLevel =
            when (entry.healthLevel) {
                StlibDashboardHealthLevel.HEALTHY -> translate("stlib.gui.health.healthy", emptyMap())
                StlibDashboardHealthLevel.DEGRADED -> translate("stlib.gui.health.degraded", emptyMap())
            }
        item.editMeta { meta ->
            meta.displayName(parse(translate("stlib.gui.detail.health_title", emptyMap()), emptyMap()))
            meta.lore(
                listOf(
                    parse(
                        translate(
                            "stlib.gui.detail.health_level",
                            mapOf("value" to healthLevel),
                        ),
                        mapOf("value" to healthLevel),
                    ),
                    parse(
                        translate(
                            "stlib.gui.detail.health_issue_count",
                            mapOf("count" to entry.healthIssueCount.toString()),
                        ),
                        mapOf("count" to entry.healthIssueCount.toString()),
                    ),
                    parse(
                        translate(
                            "stlib.gui.detail.last_lifecycle_at",
                            mapOf("time" to formatInstant(entry.lastLifecycleAt)),
                        ),
                        mapOf("time" to formatInstant(entry.lastLifecycleAt)),
                    ),
                ),
            )
        }
        return item
    }

    fun previousPageItem(): ItemStack = controlItem(Material.ARROW, "stlib.gui.control.previous")

    fun nextPageItem(): ItemStack = controlItem(Material.ARROW, "stlib.gui.control.next")

    fun refreshItem(): ItemStack = controlItem(Material.SLIME_BALL, "stlib.gui.control.refresh")

    fun closeItem(): ItemStack = controlItem(Material.BARRIER, "stlib.gui.control.close")

    fun backItem(): ItemStack = controlItem(Material.ARROW, "stlib.gui.control.back")

    fun emptyStateItem(): ItemStack {
        val item = ItemStack(Material.PAPER)
        item.editMeta { meta ->
            meta.displayName(parse(translate("stlib.gui.empty.title", emptyMap()), emptyMap()))
            meta.lore(listOf(parse(translate("stlib.gui.empty.description", emptyMap()), emptyMap())))
        }
        return item
    }

    private fun statusMaterial(status: STPluginStatus): Material {
        return when (status) {
            STPluginStatus.ENABLED -> Material.LIME_DYE
            STPluginStatus.DISABLED -> Material.GRAY_DYE
            STPluginStatus.LOADED -> Material.YELLOW_DYE
        }
    }

    private fun statusLabel(status: STPluginStatus): String {
        val key =
            when (status) {
                STPluginStatus.ENABLED -> "stlib.gui.status.enabled"
                STPluginStatus.DISABLED -> "stlib.gui.status.disabled"
                STPluginStatus.LOADED -> "stlib.gui.status.loaded"
            }
        return translate(key, emptyMap())
    }

    private fun durationLabel(entry: StlibDashboardEntry): String {
        val duration = entry.uptime ?: return translate("stlib.gui.value.none", emptyMap())
        val totalSeconds = duration.seconds.coerceAtLeast(0L)
        val days = totalSeconds / 86_400
        val hours = (totalSeconds % 86_400) / 3_600
        val minutes = (totalSeconds % 3_600) / 60
        val seconds = totalSeconds % 60

        return buildString {
            if (days > 0) {
                append(days).append("d ")
            }
            if (hours > 0 || days > 0) {
                append(hours).append("h ")
            }
            if (minutes > 0 || hours > 0 || days > 0) {
                append(minutes).append("m ")
            }
            append(seconds).append("s")
        }.trim()
    }

    private fun controlItem(
        material: Material,
        key: String,
    ): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            val label = translate(key, emptyMap())
            meta.displayName(parse(label, emptyMap()))
            val hint = translate("stlib.gui.control.hint", emptyMap())
            meta.lore(listOf(parse(hint, emptyMap())))
        }
        return item
    }
}
