package studio.singlethread.lib.framework.bukkit.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.lifecycle.support.CachedServicePropertyDelegate
import java.util.concurrent.atomic.AtomicInteger

class CachedServicePropertyDelegateTest {
    @Test
    fun `delegate should resolve only once for repeated access`() {
        val resolves = AtomicInteger(0)
        val holder =
            object {
                val value by CachedServicePropertyDelegate {
                    resolves.incrementAndGet()
                    "resolved"
                }
            }

        val first = holder.value
        val second = holder.value
        val third = holder.value

        assertEquals("resolved", first)
        assertEquals("resolved", second)
        assertEquals("resolved", third)
        assertEquals(1, resolves.get())
    }

    @Test
    fun `delegate should cache nullable values`() {
        val resolves = AtomicInteger(0)
        val holder =
            object {
                val value: String? by CachedServicePropertyDelegate {
                    resolves.incrementAndGet()
                    null
                }
            }

        assertNull(holder.value)
        assertNull(holder.value)
        assertEquals(1, resolves.get())
    }
}
