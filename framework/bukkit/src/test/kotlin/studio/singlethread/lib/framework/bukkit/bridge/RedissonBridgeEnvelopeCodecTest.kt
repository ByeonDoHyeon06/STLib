package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus

class RedissonBridgeEnvelopeCodecTest {
    @Test
    fun `publish envelope decode should keep empty trailing payload`() {
        val encoded = PublishEnvelope(sourceNode = "node-a", payload = "").encode()
        val decoded = PublishEnvelope.decode(encoded)

        assertNotNull(decoded)
        assertEquals("node-a", decoded?.sourceNode)
        assertEquals("", decoded?.payload)
    }

    @Test
    fun `request envelope decode should keep empty target and payload`() {
        val encoded = RequestEnvelope(requestId = "req-1", sourceNode = "node-a", targetNode = null, payload = "").encode()
        val decoded = RequestEnvelope.decode(encoded)

        assertNotNull(decoded)
        assertEquals("req-1", decoded?.requestId)
        assertEquals("node-a", decoded?.sourceNode)
        assertEquals(null, decoded?.targetNode)
        assertEquals("", decoded?.payload)
    }

    @Test
    fun `response envelope decode should keep empty payload and responder`() {
        val encoded =
            ResponseEnvelope(
                requestId = "req-1",
                status = BridgeResponseStatus.NO_HANDLER,
                message = null,
                payload = null,
                responderNode = null,
            ).encode()
        val decoded = ResponseEnvelope.decode(encoded)

        assertNotNull(decoded)
        assertEquals("req-1", decoded?.requestId)
        assertEquals(BridgeResponseStatus.NO_HANDLER, decoded?.status)
        assertEquals(null, decoded?.payload)
        assertEquals(null, decoded?.responderNode)
    }
}
