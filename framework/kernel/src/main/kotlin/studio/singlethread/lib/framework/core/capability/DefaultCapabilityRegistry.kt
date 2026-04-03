package studio.singlethread.lib.framework.core.capability

import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.capability.CapabilityState
import java.util.concurrent.ConcurrentHashMap

class DefaultCapabilityRegistry : CapabilityRegistry {
    private val states = ConcurrentHashMap<String, CapabilityState>()

    override fun enable(capability: String) {
        states[capability] = CapabilityState(enabled = true)
    }

    override fun disable(capability: String, reason: String) {
        states[capability] = CapabilityState(enabled = false, reason = reason)
    }

    override fun isEnabled(capability: String): Boolean {
        return states[capability]?.enabled ?: false
    }

    override fun reason(capability: String): String? {
        return states[capability]?.reason
    }

    override fun snapshot(): Map<String, CapabilityState> {
        return states.toMap()
    }
}
