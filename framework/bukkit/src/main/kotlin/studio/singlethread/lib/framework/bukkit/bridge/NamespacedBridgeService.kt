package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.bridge.BridgeTypedListener
import java.util.concurrent.CompletableFuture

class NamespacedBridgeService(
    private val delegate: BridgeService,
    private val defaultNamespace: String,
) : BridgeService {
    override fun nodeId(): BridgeNodeId {
        return delegate.nodeId()
    }

    override fun publish(
        channel: String,
        payload: String,
    ) {
        delegate.publish(resolveChannel(channel), payload)
    }

    override fun subscribe(
        channel: String,
        listener: BridgeListener,
    ): BridgeSubscription {
        return delegate.subscribe(resolveChannel(channel), listener)
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        delegate.publish(channel, payload)
    }

    override fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        return delegate.subscribe(channel, listener)
    }

    override fun subscribeWithSource(
        channel: BridgeChannel,
        listener: BridgeTypedListener<String>,
    ): BridgeSubscription {
        return delegate.subscribeWithSource(channel, listener)
    }

    override fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        return delegate.respond(channel, requestCodec, responseCodec, handler)
    }

    override fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long,
        targetNode: BridgeNodeId?,
    ): CompletableFuture<BridgeResponse<Res>> {
        return delegate.request(channel, payload, requestCodec, responseCodec, timeoutMillis, targetNode)
    }

    override fun close() {
        delegate.close()
    }

    private fun resolveChannel(channel: String): BridgeChannel {
        val raw = channel.trim()
        require(raw.isNotEmpty()) { "bridge channel must not be blank" }
        if (raw.contains(':')) {
            return BridgeChannel.parse(raw)
        }
        return BridgeChannel.of(defaultNamespace, raw)
    }
}
