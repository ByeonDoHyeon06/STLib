package studio.singlethread.lib.framework.core.kernel

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

internal class ServiceContainer {
    private val services = ConcurrentHashMap<KClass<*>, Any>()

    fun <T : Any> register(type: KClass<T>, service: T) {
        services[type] = service
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> find(type: KClass<T>): T? {
        return services[type] as? T
    }
}
