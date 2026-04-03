package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryBridgeServiceTest {
    @Test
    fun `publish should deliver message to subscribers in registration order`() {
        val service = InMemoryBridgeService()
        val received = mutableListOf<String>()

        service.subscribe("channel:alpha") { _, payload -> received += "first:$payload" }
        service.subscribe("channel:alpha") { _, payload -> received += "second:$payload" }

        service.publish("channel:alpha", "hello")

        assertEquals(listOf("first:hello", "second:hello"), received)
    }

    @Test
    fun `unsubscribe should stop receiving further messages`() {
        val service = InMemoryBridgeService()
        val received = mutableListOf<String>()

        val subscription = service.subscribe("channel:alpha") { _, payload -> received += payload }
        service.publish("channel:alpha", "before")
        subscription.unsubscribe()
        service.publish("channel:alpha", "after")

        assertEquals(listOf("before"), received)
    }

    @Test
    fun `close should clear all channel subscribers`() {
        val service = InMemoryBridgeService()
        val received = mutableListOf<String>()

        service.subscribe("channel:alpha") { _, payload -> received += payload }
        service.close()
        service.publish("channel:alpha", "ignored")

        assertEquals(emptyList<String>(), received)
    }
}
