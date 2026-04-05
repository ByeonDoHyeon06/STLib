package studio.singlethread.lib.framework.bukkit.lifecycle.support

import kotlin.reflect.KProperty

internal class CachedServicePropertyDelegate<T>(
    private val resolve: () -> T,
) {
    @Volatile
    private var cached: Any? = UNINITIALIZED

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T {
        val current = cached
        if (current !== UNINITIALIZED) {
            @Suppress("UNCHECKED_CAST")
            return current as T
        }

        return synchronized(this) {
            val synchronizedCurrent = cached
            if (synchronizedCurrent !== UNINITIALIZED) {
                @Suppress("UNCHECKED_CAST")
                return@synchronized synchronizedCurrent as T
            }

            val resolved = resolve()
            cached = resolved
            resolved
        }
    }

    private companion object {
        private val UNINITIALIZED = Any()
    }
}
