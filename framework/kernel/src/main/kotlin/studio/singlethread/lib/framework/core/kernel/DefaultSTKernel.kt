package studio.singlethread.lib.framework.core.kernel

import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.core.capability.DefaultCapabilityRegistry
import kotlin.reflect.KClass

class DefaultSTKernel(
    override val capabilityRegistry: CapabilityRegistry = DefaultCapabilityRegistry(),
) : STKernel {
    private val serviceContainer = ServiceContainer()

    override fun <T : Any> registerService(type: KClass<T>, service: T) {
        serviceContainer.register(type, service)
    }

    override fun <T : Any> service(type: KClass<T>): T? {
        return serviceContainer.find(type)
    }
}
