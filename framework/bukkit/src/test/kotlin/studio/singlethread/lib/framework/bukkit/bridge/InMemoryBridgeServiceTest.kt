package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import java.util.concurrent.TimeUnit

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

    @Test
    fun `request should return success when handler is registered`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "sum")

        service.respond(
            channel = channel,
            requestCodec = stringCodec(),
            responseCodec = stringCodec(),
        ) { request ->
            val tokens = request.payload.split(",")
            val left = tokens[0].toInt()
            val right = tokens[1].toInt()
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success((left + right).toString())
        }

        val response =
            service.request(
                channel = channel,
                payload = "2,3",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 500,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.SUCCESS, response.status)
        assertEquals("5", response.payload)
        assertNotNull(response.responderNode)
    }

    @Test
    fun `request should return no handler when channel has no responder`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "missing")

        val response =
            service.request(
                channel = channel,
                payload = "data",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 200,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.NO_HANDLER, response.status)
    }

    @Test
    fun `request should treat non-positive timeout as unlimited`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "slow")

        service.respond(
            channel = channel,
            requestCodec = stringCodec(),
            responseCodec = stringCodec(),
        ) { request ->
            Thread.sleep(50)
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success(request.payload)
        }

        val response =
            service.request(
                channel = channel,
                payload = "ok",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 0,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.SUCCESS, response.status)
        assertEquals("ok", response.payload)
    }

    private fun stringCodec(): BridgeCodec<String> = StringBridgeCodec
}
