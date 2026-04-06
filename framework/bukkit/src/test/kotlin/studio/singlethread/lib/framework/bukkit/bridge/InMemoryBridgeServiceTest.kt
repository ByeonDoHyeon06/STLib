package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
        val metrics = service.metrics()
        assertEquals(1L, metrics.requestSubmitted)
        assertEquals(1L, metrics.requestNoHandler)
    }

    @Test
    fun `request should choose first compatible handler in registration order`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "compat")

        service.respond(
            channel = channel,
            requestCodec = intCodec(),
            responseCodec = stringCodec(),
        ) {
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success("int")
        }
        service.respond(
            channel = channel,
            requestCodec = stringCodec(),
            responseCodec = stringCodec(),
        ) { request ->
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success("string:${request.payload}")
        }

        val response =
            service.request(
                channel = channel,
                payload = "hello",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 500,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.SUCCESS, response.status)
        assertEquals("string:hello", response.payload)
    }

    @Test
    fun `request should return no handler when responders exist but none are compatible`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "incompatible")

        service.respond(
            channel = channel,
            requestCodec = intCodec(),
            responseCodec = stringCodec(),
        ) {
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success("int")
        }

        val response =
            service.request(
                channel = channel,
                payload = "not-an-int",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 500,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.NO_HANDLER, response.status)
        val metrics = service.metrics()
        assertEquals(1L, metrics.requestSubmitted)
        assertEquals(1L, metrics.requestNoHandler)
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

    @Test
    fun `request should reject when backpressure limit is exceeded`() {
        val service = InMemoryBridgeService(maxPendingRequests = 1)
        val channel = BridgeChannel.of("test", "busy")

        service.respond(
            channel = channel,
            requestCodec = stringCodec(),
            responseCodec = stringCodec(),
        ) { request ->
            Thread.sleep(200)
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success(request.payload)
        }

        val first =
            service.request(
                channel = channel,
                payload = "first",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 1_000,
            )

        Thread.sleep(20)

        val second =
            service.request(
                channel = channel,
                payload = "second",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 1_000,
            ).get(2, TimeUnit.SECONDS)

        assertEquals(BridgeResponseStatus.ERROR, second.status)
        assertTrue(second.message?.contains("backpressure", ignoreCase = true) == true)

        val firstResponse = first.get(2, TimeUnit.SECONDS)
        assertEquals(BridgeResponseStatus.SUCCESS, firstResponse.status)

        val metrics = service.metrics()
        assertEquals(2L, metrics.requestSubmitted)
        assertEquals(1L, metrics.requestRejectedBackpressure)
    }

    @Test
    fun `close should complete pending requests with error`() {
        val service = InMemoryBridgeService()
        val channel = BridgeChannel.of("test", "close-pending")

        service.respond(
            channel = channel,
            requestCodec = stringCodec(),
            responseCodec = stringCodec(),
        ) { request ->
            Thread.sleep(500)
            studio.singlethread.lib.framework.api.bridge.BridgeRequestResult.success(request.payload)
        }

        val future =
            service.request(
                channel = channel,
                payload = "pending",
                requestCodec = stringCodec(),
                responseCodec = stringCodec(),
                timeoutMillis = 2_000,
            )

        Thread.sleep(30)
        service.close()

        val response = future.get(2, TimeUnit.SECONDS)
        assertEquals(BridgeResponseStatus.ERROR, response.status)
        assertTrue(response.message?.contains("closed", ignoreCase = true) == true)
        assertEquals(0, service.metrics().pendingRequests)
        assertEquals(1L, service.metrics().requestErrored)
    }

    @Test
    fun `publish should increment bridge metrics`() {
        val service = InMemoryBridgeService()

        service.publish("metrics:test", "hello")

        assertEquals(1L, service.metrics().publishedMessages)
    }

    @Test
    fun `publish subscriber failure should be reported without aborting other subscribers`() {
        val failures = mutableListOf<String>()
        val service =
            InMemoryBridgeService(
                callbackFailureReporter = { phase, error ->
                    failures += "$phase:${error.message}"
                },
            )
        val received = mutableListOf<String>()

        service.subscribe("metrics:test") { _, _ ->
            error("boom")
        }
        service.subscribe("metrics:test") { _, payload ->
            received += payload
        }

        service.publish("metrics:test", "hello")

        assertEquals(listOf("hello"), received)
        assertTrue(failures.any { it.contains("publish:global:metrics:test") || it.contains("publish:metrics:test") })
    }

    private fun stringCodec(): BridgeCodec<String> = StringBridgeCodec

    private fun intCodec(): BridgeCodec<Int> =
        object : BridgeCodec<Int> {
            override fun encode(value: Int): String = value.toString()

            override fun decode(payload: String): Int = payload.toInt()
        }
}
