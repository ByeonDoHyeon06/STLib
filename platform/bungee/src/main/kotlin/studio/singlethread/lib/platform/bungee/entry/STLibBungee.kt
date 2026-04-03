package studio.singlethread.lib.platform.bungee.entry

import net.md_5.bungee.api.plugin.Plugin
import studio.singlethread.lib.platform.common.capability.CapabilityNames

class STLibBungee : Plugin() {
    override fun onEnable() {
        logger.info("STLib Bungee bootstrap completed (${CapabilityNames.PLATFORM_BUNGEE})")
    }
}
