package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import java.util.concurrent.CompletableFuture

class CompositeBridgeService(
    private val local: BridgeService,
    private val distributed: BridgeService?,
) : BridgeService {
    override fun nodeId(): BridgeNodeId {
        return local.nodeId()
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        local.publish(channel, payload)
        distributed?.publish(channel, payload)
    }

    override fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        val localSub = local.subscribe(channel, listener)
        val distributedSub = distributed?.subscribe(channel, listener)

        return BridgeSubscription {
            localSub.unsubscribe()
            distributedSub?.unsubscribe()
        }
    }

    override fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        val localSub = local.respond(channel, requestCodec, responseCodec, handler)
        val distributedSub = distributed?.respond(channel, requestCodec, responseCodec, handler)

        return BridgeSubscription {
            localSub.unsubscribe()
            distributedSub?.unsubscribe()
        }
    }

    override fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long,
        targetNode: BridgeNodeId?,
    ): CompletableFuture<BridgeResponse<Res>> {
        val distributedService = distributed
        if (distributedService == null) {
            return local.request(channel, payload, requestCodec, responseCodec, timeoutMillis, targetNode)
        }

        val distributedFuture =
            distributedService.request(
                channel = channel,
                payload = payload,
                requestCodec = requestCodec,
                responseCodec = responseCodec,
                timeoutMillis = timeoutMillis,
                targetNode = targetNode,
            )

        return distributedFuture.thenCompose { response ->
            val shouldFallbackLocal =
                response.status == BridgeResponseStatus.NO_HANDLER &&
                    (targetNode == null || targetNode == local.nodeId())
            if (!shouldFallbackLocal) {
                return@thenCompose CompletableFuture.completedFuture(response)
            }
            local.request(channel, payload, requestCodec, responseCodec, timeoutMillis, targetNode)
        }
    }

    override fun close() {
        runCatching { local.close() }
        runCatching { distributed?.close() }
    }
}
