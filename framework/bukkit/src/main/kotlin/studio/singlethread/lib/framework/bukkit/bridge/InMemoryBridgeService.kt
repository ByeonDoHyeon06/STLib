package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeIncomingMessage
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestContext
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.bridge.BridgeTypedListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

class InMemoryBridgeService(
    private val localNodeId: BridgeNodeId = BridgeNodeId("local"),
) : BridgeService {
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<BridgeTypedListener<String>>>()
    private val requestHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredRequestHandler>>()
    private val closed = AtomicBoolean(false)
    private val requestExecutor = Executors.newCachedThreadPool()
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()

    override fun nodeId(): BridgeNodeId {
        return localNodeId
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        if (closed.get()) {
            return
        }

        val key = normalize(channel)
        subscribers[key]
            ?.forEach { listener ->
                runCatching {
                    listener.onMessage(
                        BridgeIncomingMessage(
                            channel = channel,
                            payload = payload,
                            sourceNode = localNodeId,
                        ),
                    )
                }
            }
    }

    override fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        return subscribeWithSource(channel) { message ->
            listener.onMessage(message.channel.asString(), message.payload)
        }
    }

    override fun subscribeWithSource(
        channel: BridgeChannel,
        listener: BridgeTypedListener<String>,
    ): BridgeSubscription {
        if (closed.get()) {
            return BridgeSubscription {}
        }

        val key = normalize(channel)
        val listeners = subscribers.computeIfAbsent(key) { CopyOnWriteArrayList() }
        listeners += listener

        return BridgeSubscription {
            listeners -= listener
            if (listeners.isEmpty()) {
                subscribers.remove(key, listeners)
            }
        }
    }

    override fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        if (closed.get()) {
            return BridgeSubscription {}
        }

        val key = normalize(channel)
        val handlers = requestHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
        val registration =
            RegisteredRequestHandler(
                requestCodec = requestCodec as BridgeCodec<Any>,
                responseCodec = responseCodec as BridgeCodec<Any>,
                handler = handler as BridgeRequestHandler<Any, Any>,
            )
        handlers += registration

        return BridgeSubscription {
            handlers -= registration
            if (handlers.isEmpty()) {
                requestHandlers.remove(key, handlers)
            }
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
        if (closed.get()) {
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = "bridge is closed",
                    responderNode = localNodeId,
                ),
            )
        }

        if (targetNode != null && targetNode != localNodeId) {
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.NO_HANDLER,
                    message = "target node '${targetNode.value}' is unavailable in local bridge",
                    responderNode = null,
                ),
            )
        }

        val key = normalize(channel)
        val registration = requestHandlers[key]?.firstOrNull()
        if (registration == null) {
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.NO_HANDLER,
                    message = "no request handler registered for channel ${channel.asString()}",
                    responderNode = null,
                ),
            )
        }

        val responseFuture = CompletableFuture<BridgeResponse<Res>>()
        val timeoutTask = scheduleTimeout(responseFuture, timeoutMillis)

        CompletableFuture
            .supplyAsync(
                {
                    invokeHandler(
                        channel = channel,
                        payload = payload,
                        requestCodec = requestCodec,
                        responseCodec = responseCodec,
                        registration = registration,
                        targetNode = targetNode,
                    )
                },
                requestExecutor,
            ).whenComplete { response, error ->
                timeoutTask?.cancel(false)
                if (error != null) {
                    responseFuture.complete(
                        BridgeResponse(
                            status = BridgeResponseStatus.ERROR,
                            message = error.message ?: "request execution failed",
                            responderNode = localNodeId,
                        ),
                    )
                    return@whenComplete
                }

                responseFuture.complete(response)
            }

        return responseFuture
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        subscribers.clear()
        requestHandlers.clear()
        requestExecutor.shutdownNow()
        timeoutScheduler.shutdownNow()
    }

    private fun <Req : Any, Res : Any> invokeHandler(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        registration: RegisteredRequestHandler,
        targetNode: BridgeNodeId?,
    ): BridgeResponse<Res> {
        val requestId = BridgeService.nextRequestId()

        return runCatching {
            val encodedRequest = requestCodec.encode(payload)
            val decodedRequest = registration.requestCodec.decode(encodedRequest)

            val handlerResult =
                registration.handler.handle(
                    BridgeRequestContext(
                        requestId = requestId,
                        channel = channel,
                        payload = decodedRequest,
                        sourceNode = localNodeId,
                        targetNode = targetNode,
                    ),
                )

            if (handlerResult.status != BridgeResponseStatus.SUCCESS) {
                return BridgeResponse(
                    status = handlerResult.status,
                    message = handlerResult.message,
                    responderNode = localNodeId,
                )
            }

            val rawResponsePayload =
                handlerResult.payload
                    ?: return BridgeResponse(
                        status = BridgeResponseStatus.ERROR,
                        message = "handler returned SUCCESS without payload",
                        responderNode = localNodeId,
                    )

            val encodedResponse = registration.responseCodec.encode(rawResponsePayload)
            val decodedResponse = responseCodec.decode(encodedResponse)

            BridgeResponse(
                status = BridgeResponseStatus.SUCCESS,
                payload = decodedResponse,
                responderNode = localNodeId,
            )
        }.getOrElse { error ->
            BridgeResponse(
                status = BridgeResponseStatus.ERROR,
                message = error.message ?: "request execution failed",
                responderNode = localNodeId,
            )
        }
    }

    private fun <Res : Any> scheduleTimeout(
        future: CompletableFuture<BridgeResponse<Res>>,
        timeoutMillis: Long,
    ): ScheduledFuture<*>? {
        if (timeoutMillis <= 0L) {
            return null
        }

        return timeoutScheduler.schedule(
            {
                if (future.isDone) {
                    return@schedule
                }
                future.complete(
                    BridgeResponse(
                        status = BridgeResponseStatus.TIMEOUT,
                        message = TimeoutException("bridge request timed out after ${timeoutMillis}ms").message,
                        responderNode = null,
                    ),
                )
            },
            timeoutMillis,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun normalize(channel: BridgeChannel): String {
        return channel.asString().trim().lowercase()
    }

    private fun <T : Any> completedResponse(response: BridgeResponse<T>): CompletableFuture<BridgeResponse<T>> {
        return CompletableFuture.completedFuture(response)
    }

    private data class RegisteredRequestHandler(
        val requestCodec: BridgeCodec<Any>,
        val responseCodec: BridgeCodec<Any>,
        val handler: BridgeRequestHandler<Any, Any>,
    )
}
