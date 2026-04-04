package studio.singlethread.lib.framework.bukkit.di

import studio.singlethread.lib.framework.api.di.ComponentContainer
import studio.singlethread.lib.framework.api.di.ComponentScanSummary
import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STInject
import studio.singlethread.lib.framework.api.di.STScope
import studio.singlethread.lib.framework.api.kernel.STKernel
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

internal class ReflectiveComponentResolver(
    private val owner: Any,
    private val kernel: STKernel,
) : ComponentContainer {
    private val singletonInstances = ConcurrentHashMap<Class<*>, Any>()

    override fun <T : Any> resolve(type: Class<T>): T {
        return resolve(type = type, stack = ArrayDeque())
    }

    override fun scan(packageRoot: String): ComponentScanSummary {
        val normalizedRoot = packageRoot.trim().removeSuffix(".")
        require(normalizedRoot.isNotBlank()) { "packageRoot must not be blank" }

        val discovered = discoverComponentTypes(normalizedRoot)
        if (discovered.isEmpty()) {
            return ComponentScanSummary(
                packageRoot = normalizedRoot,
                discovered = 0,
                validated = 0,
                singletonComponents = 0,
                prototypeComponents = 0,
            )
        }

        val singletonCount = discovered.count { scopeOf(it) == STScope.SINGLETON }
        val prototypeCount = discovered.size - singletonCount

        val failures = mutableListOf<String>()
        var validated = 0

        discovered.forEach { componentType ->
            @Suppress("UNCHECKED_CAST")
            val typed = componentType as Class<Any>
            runCatching {
                if (scopeOf(componentType) == STScope.SINGLETON) {
                    resolve(typed)
                } else {
                    validateGraph(componentType, ArrayDeque())
                }
            }.onSuccess {
                validated += 1
            }.onFailure { error ->
                failures += "${componentType.name}: ${error.message ?: "unknown"}"
            }
        }

        if (failures.isNotEmpty()) {
            throw IllegalStateException(
                "DI component graph validation failed for '$normalizedRoot': ${failures.joinToString(" | ")}",
            )
        }

        return ComponentScanSummary(
            packageRoot = normalizedRoot,
            discovered = discovered.size,
            validated = validated,
            singletonComponents = singletonCount,
            prototypeComponents = prototypeCount,
        )
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

    private fun validateGraph(
        type: Class<*>,
        stack: ArrayDeque<Class<*>>,
    ) {
        if (type.isInstance(owner) || type == STKernel::class.java) {
            return
        }
        if (kernel.service(type.kotlin) != null || singletonInstances.containsKey(type)) {
            return
        }

        if (type.isInterface || Modifier.isAbstract(type.modifiers)) {
            throw IllegalArgumentException(
                "Cannot instantiate abstract type ${type.name}. Register it as a kernel service or provide a concrete component.",
            )
        }

        detectCircularDependency(type, stack)
        try {
            val constructor = selectConstructor(type)
            constructor.parameterTypes.forEach { dependencyType ->
                validateDependency(dependencyType, stack)
            }
            fieldsNeedingInjection(type).forEach { field ->
                if (Modifier.isFinal(field.modifiers)) {
                    throw IllegalArgumentException(
                        "Field injection target must be mutable in ${type.name}: ${field.name}",
                    )
                }
                validateDependency(field.type, stack)
            }
        } finally {
            stack.removeLast()
        }
    }

    private fun validateDependency(
        dependencyType: Class<*>,
        stack: ArrayDeque<Class<*>>,
    ) {
        if (dependencyType.isInstance(owner) || dependencyType == STKernel::class.java) {
            return
        }
        if (kernel.service(dependencyType.kotlin) != null || singletonInstances.containsKey(dependencyType)) {
            return
        }
        validateGraph(dependencyType, stack)
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

    private fun discoverComponentTypes(packageRoot: String): List<Class<*>> {
        val classLoader = owner.javaClass.classLoader
        val packagePath = packageRoot.replace('.', '/')

        val codeSourceUri = owner.javaClass.protectionDomain?.codeSource?.location?.toURI() ?: return emptyList()
        val codeSourcePath = runCatching { java.nio.file.Paths.get(codeSourceUri) }.getOrNull() ?: return emptyList()

        val classNames =
            when {
                Files.isDirectory(codeSourcePath) -> discoverFromDirectory(codeSourcePath, packageRoot, packagePath)
                Files.isRegularFile(codeSourcePath) -> discoverFromJar(codeSourcePath, packagePath)
                else -> emptyList()
            }

        return classNames
            .mapNotNull { className ->
                runCatching {
                    Class.forName(className, false, classLoader)
                }.getOrNull()
            }.filter { type ->
                !type.isInterface &&
                    !type.isAnnotation &&
                    !type.isEnum &&
                    !type.isSynthetic &&
                    type.isAnnotationPresent(STComponent::class.java)
            }.distinctBy(Class<*>::getName)
            .sortedBy(Class<*>::getName)
    }

    private fun discoverFromDirectory(
        root: java.nio.file.Path,
        packageRoot: String,
        packagePath: String,
    ): List<String> {
        val targetRoot = root.resolve(packagePath)
        if (!Files.exists(targetRoot)) {
            return emptyList()
        }

        Files.walk(targetRoot).use { stream ->
            return stream
                .filter(Files::isRegularFile)
                .map { path ->
                    targetRoot.relativize(path).toString()
                }.filter { relative ->
                    relative.endsWith(".class") && !relative.contains("module-info")
                }.map { relative ->
                    relative.removeSuffix(".class").replace('/', '.').replace('\\', '.')
                }.map { suffix ->
                    "$packageRoot.$suffix"
                }.toList()
        }
    }

    private fun discoverFromJar(
        jarPath: java.nio.file.Path,
        packagePath: String,
    ): List<String> {
        if (!jarPath.fileName.toString().endsWith(".jar")) {
            return emptyList()
        }

        JarFile(jarPath.toFile()).use { jar ->
            return jar.entries().asSequence()
                .filter { entry -> !entry.isDirectory }
                .map { entry -> entry.name }
                .filter { name -> name.startsWith(packagePath) }
                .filter { name -> name.endsWith(".class") }
                .filterNot { name -> name.contains("module-info") }
                .map { name -> name.removeSuffix(".class").replace('/', '.') }
                .toList()
        }
    }
}
