package studio.singlethread.lib.framework.bukkit.bootstrap.step

import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.platform.common.capability.CapabilityNames

internal object PlatformCapabilityStep {
    fun bootstrap(capabilities: CapabilityRegistry) {
        capabilities.enable(CapabilityNames.PLATFORM_BUKKIT)
        if (isFoliaServer()) {
            capabilities.enable(CapabilityNames.PLATFORM_FOLIA)
            return
        }
        capabilities.disable(CapabilityNames.PLATFORM_FOLIA, "Folia runtime not detected")
    }

    private fun isFoliaServer(): Boolean {
        return runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }
}
