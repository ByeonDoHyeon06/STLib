package studio.singlethread.lib.framework.api.capability

interface CapabilityRegistry {
    fun enable(capability: String)

    fun disable(capability: String, reason: String)

    fun isEnabled(capability: String): Boolean

    fun reason(capability: String): String?

    fun snapshot(): Map<String, CapabilityState>
}

data class CapabilityState(
    val enabled: Boolean,
    val reason: String? = null,
)
