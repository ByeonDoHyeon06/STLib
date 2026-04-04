package studio.singlethread.lib.framework.api.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

class BridgeServiceDefaultSubscribeTest {
    @Test
    fun `typed subscribe should keep source node from subscribeWithSource`() {
        val service = FakeBridgeService()
        val receivedSource = AtomicReference<BridgeNodeId?>()
        val receivedPayload = AtomicReference<String?>()

        service.subscribe(
            channel = BridgeChannel.of("test", "topic"),
            codec = passthroughCodec(),
            listener =
                BridgeTypedListener { incoming ->
                    receivedSource.set(incoming.sourceNode)
                    receivedPayload.set(incoming.payload)
                },
        )

        service.emit(
            BridgeIncomingMessage(
                channel = BridgeChannel.of("test", "topic"),
                payload = "payload",
                sourceNode = BridgeNodeId("remote-node"),
            ),
        )

        assertEquals("remote-node", receivedSource.get()?.value)
        assertEquals("payload", receivedPayload.get())
    }

    @Test
    fun `raw subscribe should receive payload from subscribeWithSource`() {
        val service = FakeBridgeService()
        val received = AtomicReference<String?>()

        service.subscribe(BridgeChannel.of("test", "topic"), BridgeListener { _, payload ->
            received.set(payload)
        })

        service.emit(
            BridgeIncomingMessage(
                channel = BridgeChannel.of("test", "topic"),
                payload = "hello",
                sourceNode = BridgeNodeId("another-node"),
            ),
        )

        assertEquals("hello", received.get())
    }

    private fun passthroughCodec(): BridgeCodec<String> {
        return object : BridgeCodec<String> {
            override fun encode(value: String): String = value

            override fun decode(payload: String): String = payload
        }
    }
}

private class FakeBridgeService : BridgeService {
    private var listener: BridgeTypedListener<String>? = null

    override fun nodeId(): BridgeNodeId = BridgeNodeId("local-node")

    override fun publish(channel: BridgeChannel, payload: String) = Unit

    override fun subscribeWithSource(
        channel: BridgeChannel,
        listener: BridgeTypedListener<String>,
    ): BridgeSubscription {
        this.listener = listener
        return BridgeSubscription { this.listener = null }
    }

    override fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        return BridgeSubscription {}
    }

    override fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long,
        targetNode: BridgeNodeId?,
    ): CompletableFuture<BridgeResponse<Res>> {
        return CompletableFuture.completedFuture(
            BridgeResponse(
                status = BridgeResponseStatus.NO_HANDLER,
                responderNode = null,
            ),
        )
    }

    fun emit(message: BridgeIncomingMessage<String>) {
        val current = listener
        assertNotNull(current)
        current!!.onMessage(message)
    }
}
