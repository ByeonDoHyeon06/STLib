package studio.singlethread.lib.registry.common.provider

import java.lang.reflect.Method

object ReflectiveResourceAccess {
    fun classExists(className: String): Boolean {
        return runCatching { Class.forName(className) }.isSuccess
    }

    fun invokeStatic(
        classNames: Collection<String>,
        methodNames: Collection<String>,
        vararg args: Any?,
    ): Any? {
        return classNames.asSequence()
            .mapNotNull { className -> invokeStatic(className, methodNames, *args) }
            .firstOrNull()
    }

    fun invokeStatic(
        className: String,
        methodNames: Collection<String>,
        vararg args: Any?,
    ): Any? {
        return runCatching {
            val type = Class.forName(className)
            val method = findMethod(type.methods.asList(), methodNames, args) ?: return null
            method.invoke(null, *args)
        }.getOrNull()
    }

    fun invoke(
        target: Any,
        methodNames: Collection<String>,
        vararg args: Any?,
    ): Any? {
        return runCatching {
            val method = findMethod(target.javaClass.methods.asList(), methodNames, args) ?: return null
            method.invoke(target, *args)
        }.getOrNull()
    }

    fun asStringCollection(value: Any?): List<String> {
        return when (value) {
            is Array<*> -> value.mapNotNull { item -> asNonBlankString(item) }
            is Collection<*> -> value.mapNotNull { item -> asNonBlankString(item) }
            is Map<*, *> -> value.keys.mapNotNull { key -> asNonBlankString(key) }
            else -> emptyList()
        }
    }

    fun asNonBlankString(value: Any?): String? {
        val stringValue = value?.toString()?.trim() ?: return null
        if (stringValue.isBlank()) {
            return null
        }
        return stringValue
    }

    private fun findMethod(
        methods: List<Method>,
        methodNames: Collection<String>,
        args: Array<out Any?>,
    ): Method? {
        return methods.firstOrNull { method ->
            methodNames.any { it.equals(method.name, ignoreCase = true) } &&
                method.parameterCount == args.size &&
                parametersMatch(method.parameterTypes, args)
        }
    }

    private fun parametersMatch(
        parameterTypes: Array<Class<*>>,
        args: Array<out Any?>,
    ): Boolean {
        return parameterTypes.indices.all { index ->
            val parameterType = wrapPrimitive(parameterTypes[index])
            val argument = args[index] ?: return@all true
            parameterType.isAssignableFrom(argument.javaClass)
        }
    }

    private fun wrapPrimitive(type: Class<*>): Class<*> {
        if (!type.isPrimitive) {
            return type
        }

        return when (type) {
            java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
            java.lang.Byte.TYPE -> Byte::class.javaObjectType
            java.lang.Short.TYPE -> Short::class.javaObjectType
            java.lang.Integer.TYPE -> Int::class.javaObjectType
            java.lang.Long.TYPE -> Long::class.javaObjectType
            java.lang.Float.TYPE -> Float::class.javaObjectType
            java.lang.Double.TYPE -> Double::class.javaObjectType
            java.lang.Character.TYPE -> Char::class.javaObjectType
            else -> type
        }
    }
}
