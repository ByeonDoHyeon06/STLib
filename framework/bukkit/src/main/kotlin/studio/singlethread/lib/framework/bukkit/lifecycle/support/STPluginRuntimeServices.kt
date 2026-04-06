package studio.singlethread.lib.framework.bukkit.lifecycle.support

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.command.CommandRegistrar
import studio.singlethread.lib.framework.api.event.EventRegistrar
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.text.TextService
import studio.singlethread.lib.framework.bukkit.command.BukkitCommandRegistrar
import studio.singlethread.lib.framework.bukkit.event.BukkitEventCaller
import studio.singlethread.lib.framework.bukkit.event.BukkitEventRegistrar
import studio.singlethread.lib.framework.bukkit.gui.BukkitGuiService
import studio.singlethread.lib.framework.bukkit.gui.STGuiService
import studio.singlethread.lib.framework.bukkit.resource.BukkitResourceIntegrationRuntime
import studio.singlethread.lib.framework.core.text.MiniMessageTextService
import studio.singlethread.lib.platform.common.capability.CapabilityNames
import java.util.logging.Logger

internal class STPluginRuntimeServices(
    private val plugin: JavaPlugin,
    private val kernel: STKernel,
    private val capabilityRegistry: CapabilityRegistry,
    private val logger: Logger,
) {
    fun registerDefaultServices(debugLoggingEnabled: () -> Boolean): BukkitGuiService {
        kernel.registerService(TextService::class, MiniMessageTextService())
        kernel.registerService(
            CommandRegistrar::class,
            BukkitCommandRegistrar(plugin) { debugLoggingEnabled() },
        )
        val bukkitEventRegistrar = BukkitEventRegistrar(plugin)
        val bukkitEventCaller = BukkitEventCaller(plugin)
        kernel.registerService(EventRegistrar::class, bukkitEventRegistrar)
        kernel.registerService(BukkitEventRegistrar::class, bukkitEventRegistrar)
        kernel.registerService(BukkitEventCaller::class, bukkitEventCaller)
        val guiService = BukkitGuiService(plugin, debugLoggingEnabled = debugLoggingEnabled)
        kernel.registerService(STGuiService::class, guiService)
        kernel.registerService(BukkitGuiService::class, guiService)
        capabilityRegistry.disable(CapabilityNames.UI_INVENTORY, "Waiting for plugin enable")
        return guiService
    }

    fun activateResourceIntegrations() {
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.activate() }
            .onFailure { error ->
                logger.warning("Resource integration runtime activation failed: ${error.message}")
            }
    }

    fun activateGui(
        guiService: BukkitGuiService?,
        syncCapabilitySummary: () -> Unit,
    ) {
        val service = guiService ?: return
        runCatching {
            service.activate()
        }.onSuccess {
            capabilityRegistry.enable(CapabilityNames.UI_INVENTORY)
            syncCapabilitySummary()
        }.onFailure { error ->
            capabilityRegistry.disable(
                CapabilityNames.UI_INVENTORY,
                "GUI activation failed: ${error.message ?: "unknown"}",
            )
            logger.warning("GUI service activation failed: ${error.message}")
            syncCapabilitySummary()
        }
    }

    fun cleanupRuntimeResources(guiService: STGuiService?) {
        runCatching { kernel.service(BukkitResourceIntegrationRuntime::class)?.shutdown() }
            .onFailure { error ->
                logger.warning("Resource integration runtime shutdown failed: ${error.message}")
            }
        runCatching { guiService?.close() }
            .onFailure { error ->
                logger.warning("GUI runtime shutdown failed: ${error.message}")
            }
    }
}
