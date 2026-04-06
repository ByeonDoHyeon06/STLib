package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeRequestResult
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.bridge.BridgeTypedListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger

class CompositeBridgeServiceTest {
    @Test
    fun `composite subscribeWithSource should avoid duplicate loopback delivery in composite mode`() {
        val nodeId = BridgeNodeId("node-a")
        val local = InMemoryBridgeService(localNodeId = nodeId)
        val distributed = InMemoryBridgeService(localNodeId = nodeId)
        val composite = CompositeBridgeService(local = local, distributed = distributed)
        val received = mutableListOf<String>()

        composite.subscribeWithSource(BridgeChannel.of("test", "topic")) { message ->
            received += "${message.sourceNode.value}:${message.payload}"
        }

        composite.publish(BridgeChannel.of("test", "topic"), "payload")

        assertEquals(
            listOf("node-a:payload"),
            received,
            "composite mode should not deliver duplicate loopback messages from distributed bridge",
        )
    }

    @Test
    fun `publish should fan out to local and distributed when distributed bridge exists`() {
        val local = CountingBridgeService(id = BridgeNodeId("local"))
        val distributed = CountingBridgeService(id = BridgeNodeId("remote"))
        val composite = CompositeBridgeService(local = local, distributed = distributed)

        composite.publish(BridgeChannel.of("test", "topic"), "payload")

        assertEquals(1, local.publishCount.get(), "local publish should run in composite mode")
        assertEquals(1, distributed.publishCount.get(), "distributed publish should run in composite mode")
    }

    @Test
    fun `publish should fallback to local path when distributed bridge is absent`() {
        val local = CountingBridgeService(id = BridgeNodeId("local"))
        val composite = CompositeBridgeService(local = local, distributed = null)

        composite.publish(BridgeChannel.of("test", "topic"), "payload")

        assertEquals(1, local.publishCount.get())
    }

    @Test
    fun `subscribe should register local and distributed listeners when distributed bridge exists`() {
        val local = CountingBridgeService(id = BridgeNodeId("local"))
        val distributed = CountingBridgeService(id = BridgeNodeId("remote"))
        val composite = CompositeBridgeService(local = local, distributed = distributed)

        val sub = composite.subscribe(BridgeChannel.of("test", "topic"), BridgeListener { _, _ -> })
        assertNotNull(sub)

        assertEquals(1, local.subscribeCount.get(), "local subscribe should run in composite mode")
        assertEquals(1, distributed.subscribeCount.get(), "distributed subscribe should run in composite mode")
    }

    @Test
    fun `request should fallback to local when distributed has no handler`() {
        val local = CountingBridgeService(id = BridgeNodeId("local"))
        local.nextRequestResponse =
            BridgeResponse(
                status = BridgeResponseStatus.SUCCESS,
                payload = "from-local",
                responderNode = BridgeNodeId("local"),
            )
        val distributed = CountingBridgeService(id = BridgeNodeId("remote"))
        distributed.nextRequestResponse =
            BridgeResponse(
                status = BridgeResponseStatus.NO_HANDLER,
                message = "missing",
                responderNode = null,
            )
        val composite = CompositeBridgeService(local = local, distributed = distributed)

        val response =
            composite.request(
                channel = BridgeChannel.of("test", "rpc"),
                payload = "input",
                requestCodec = StringBridgeCodec,
                responseCodec = StringBridgeCodec,
                timeoutMillis = 1_000,
            ).join()

        assertEquals(1, distributed.requestCount.get(), "distributed request should be attempted first")
        assertEquals(1, local.requestCount.get(), "local request should be used as fallback")
        assertEquals(BridgeResponseStatus.SUCCESS, response.status)
        assertEquals("from-local", response.payload)
    }

    @Test
    fun `metrics should merge local and distributed snapshots`() {
        val local = CountingBridgeService(id = BridgeNodeId("local"))
        local.metricsSnapshot =
            BridgeMetricsSnapshot(
                pendingRequests = 1,
                requestSubmitted = 2,
                requestTimedOut = 1,
            )
        val distributed = CountingBridgeService(id = BridgeNodeId("remote"))
        distributed.metricsSnapshot =
            BridgeMetricsSnapshot(
                pendingRequests = 3,
                requestSubmitted = 4,
                requestRejectedBackpressure = 2,
            )
        val composite = CompositeBridgeService(local = local, distributed = distributed)

        val metrics = composite.metrics()
        assertEquals(4, metrics.pendingRequests)
        assertEquals(6L, metrics.requestSubmitted)
        assertEquals(1L, metrics.requestTimedOut)
        assertEquals(2L, metrics.requestRejectedBackpressure)
    }
}

private class CountingBridgeService(
    private val id: BridgeNodeId = BridgeNodeId("test-node"),
    var nextRequestResponse: BridgeResponse<String> = BridgeResponse(status = BridgeResponseStatus.NO_HANDLER),
) : BridgeService {
    val publishCount = AtomicInteger(0)
    val subscribeCount = AtomicInteger(0)
    val requestCount = AtomicInteger(0)
    var metricsSnapshot: BridgeMetricsSnapshot = BridgeMetricsSnapshot.EMPTY

    override fun nodeId(): BridgeNodeId = id

    override fun metrics(): BridgeMetricsSnapshot {
        return metricsSnapshot
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        publishCount.incrementAndGet()
    }

    override fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        subscribeCount.incrementAndGet()
        return BridgeSubscription {}
    }

    override fun subscribeWithSource(
        channel: BridgeChannel,
        listener: BridgeTypedListener<String>,
    ): BridgeSubscription {
        subscribeCount.incrementAndGet()
        return BridgeSubscription {}
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
        requestCount.incrementAndGet()
        @Suppress("UNCHECKED_CAST")
        return CompletableFuture.completedFuture(nextRequestResponse as BridgeResponse<Res>)
    }
}
