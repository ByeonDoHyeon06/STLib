package studio.singlethread.lib.framework.bukkit.di

import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STInject
import studio.singlethread.lib.framework.api.di.STScope
import studio.singlethread.lib.framework.api.kernel.STKernel
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

internal class ReflectiveComponentResolver(
    private val owner: Any,
    private val kernel: STKernel,
) {
    private val singletonInstances = ConcurrentHashMap<Class<*>, Any>()

    fun <T : Any> resolve(type: Class<T>): T {
        return resolve(type = type, stack = ArrayDeque())
    }

    private fun <T : Any> resolve(
        type: Class<T>,
        stack: ArrayDeque<Class<*>>,
    ): T {
        resolveOwner(type)?.let { return it }
        resolveKernelService(type)?.let { return it }
        resolveSingleton(type)?.let { return it }
        return create(type, stack)
    }

    private fun <T : Any> resolveOwner(type: Class<T>): T? {
        if (!type.isInstance(owner)) {
            return null
        }
        return type.cast(owner)
    }

    private fun <T : Any> resolveKernelService(type: Class<T>): T? {
        return kernel.service(type.kotlin)
    }

    private fun <T : Any> resolveSingleton(type: Class<T>): T? {
        return singletonInstances[type]?.let(type::cast)
    }

    private fun <T : Any> create(
        type: Class<T>,
        stack: ArrayDeque<Class<*>>,
    ): T {
        if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
            throw IllegalArgumentException(
                "Cannot instantiate abstract type ${type.name}. Register it as a kernel service or provide a concrete component.",
            )
        }
        detectCircularDependency(type, stack)
        try {
            val constructor = selectConstructor(type)
            val args = constructor.parameterTypes.map { dependencyType ->
                resolveDependency(dependencyType, stack)
            }.toTypedArray()
            if (!constructor.canAccess(null)) {
                constructor.isAccessible = true
            }

            val created = type.cast(constructor.newInstance(*args))
            injectFields(created, stack)

            return when (scopeOf(type)) {
                STScope.SINGLETON -> {
                    val existing = singletonInstances.putIfAbsent(type, created)
                    type.cast(existing ?: created)
                }

                STScope.PROTOTYPE -> created
            }
        } finally {
            stack.removeLast()
        }
    }

    private fun detectCircularDependency(
        type: Class<*>,
        stack: ArrayDeque<Class<*>>,
    ) {
        if (stack.contains(type)) {
            val chain = (stack + type).joinToString(" -> ") { it.simpleName }
            throw IllegalStateException("Circular dependency detected: $chain")
        }
        stack.addLast(type)
    }

    private fun <T : Any> selectConstructor(type: Class<T>): Constructor<*> {
        val constructors = type.declaredConstructors.toList()
        val injected = constructors.filter { it.isAnnotationPresent(STInject::class.java) }

        if (injected.size > 1) {
            throw IllegalArgumentException(
                "Multiple @STInject constructors found in ${type.name}. Keep only one injectable constructor.",
            )
        }
        if (injected.size == 1) {
            return injected.first()
        }
        if (constructors.size == 1) {
            return constructors.first()
        }

        return constructors.firstOrNull { it.parameterCount == 0 }
            ?: throw IllegalArgumentException(
                "Cannot choose constructor for ${type.name}. " +
                    "Add @STInject to the constructor to use or provide a no-arg constructor.",
            )
    }

    private fun resolveDependency(
        dependencyType: Class<*>,
        stack: ArrayDeque<Class<*>>,
    ): Any {
        if (dependencyType.isInstance(owner)) {
            return owner
        }
        if (dependencyType == STKernel::class.java) {
            return kernel
        }
        kernel.service(dependencyType.kotlin)?.let { return it }

        @Suppress("UNCHECKED_CAST")
        return resolve(dependencyType as Class<Any>, stack)
    }

    private fun injectFields(
        instance: Any,
        stack: ArrayDeque<Class<*>>,
    ) {
        fieldsNeedingInjection(instance.javaClass).forEach { field ->
            if (Modifier.isFinal(field.modifiers)) {
                throw IllegalArgumentException(
                    "Field injection target must be mutable in ${instance.javaClass.name}: ${field.name}",
                )
            }
            if (!field.canAccess(instance)) {
                field.isAccessible = true
            }
            field.set(instance, resolveDependency(field.type, stack))
        }
    }

    private fun fieldsNeedingInjection(type: Class<*>): List<Field> {
        return type.declaredFields.filter { field -> field.isAnnotationPresent(STInject::class.java) }
    }

    private fun scopeOf(type: Class<*>): STScope {
        return type.getAnnotation(STComponent::class.java)?.scope ?: STScope.PROTOTYPE
    }
}
