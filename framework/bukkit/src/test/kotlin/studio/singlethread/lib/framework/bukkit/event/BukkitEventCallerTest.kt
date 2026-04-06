package studio.singlethread.lib.framework.bukkit.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class BukkitEventCallerTest {
    @Test
    fun `fire should pass event to bukkit path and return same event instance`() {
        val called = mutableListOf<Event>()
        val caller = BukkitEventCaller(called::add)
        val event = TestEvent()

        val fired = caller.fire(event)

        assertSame(event, fired)
        assertEquals(listOf(event), called)
    }

    private class TestEvent : STEvent() {
        override fun getHandlers(): HandlerList = HANDLERS

        companion object {
            @JvmStatic
            private val HANDLERS = HandlerList()

            @JvmStatic
            fun getHandlerList(): HandlerList = HANDLERS
        }
    }
}

