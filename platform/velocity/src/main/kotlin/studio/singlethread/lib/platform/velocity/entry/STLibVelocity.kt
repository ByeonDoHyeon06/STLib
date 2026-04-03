package studio.singlethread.lib.platform.velocity.entry

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.plugin.Plugin
import org.slf4j.LoggerFactory
import studio.singlethread.lib.platform.common.capability.CapabilityNames

@Plugin(
    id = "stlib",
    name = "STLib",
    version = "1.0.0-SNAPSHOT",
)
class STLibVelocity {
    private val logger = LoggerFactory.getLogger(STLibVelocity::class.java)

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("STLib Velocity bootstrap completed ({})", CapabilityNames.PLATFORM_VELOCITY)
    }
}
