package studio.singlethread.lib.framework.api.kernel

import studio.singlethread.lib.framework.api.capability.CapabilityRegistry
import kotlin.reflect.KClass

interface STKernel {
    val capabilityRegistry: CapabilityRegistry

    fun <T : Any> registerService(type: KClass<T>, service: T)

    fun <T : Any> service(type: KClass<T>): T?
}

inline fun <reified T : Any> STKernel.service(): T? = service(T::class)

inline fun <reified T : Any> STKernel.requireService(): T {
    return service(T::class)
        ?: error("Service not found: ${T::class.qualifiedName}")
}
