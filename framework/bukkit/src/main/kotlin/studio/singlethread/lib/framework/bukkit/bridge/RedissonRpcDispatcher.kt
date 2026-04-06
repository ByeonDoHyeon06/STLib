package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestContext
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

internal class RedissonRpcDispatcher(
    private val localNodeId: BridgeNodeId,
    private val transport: RedissonTopicTransport,
    private val timeoutExecutor: ScheduledExecutorService,
    private val maxPendingRequests: Int,
    private val metrics: BridgeMetricsRecorder,
) {
    private val requestHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredRequestHandler>>()
    private val requestListeners = ConcurrentHashMap<String, RedissonTopicTransport.ListenerToken>()
    private val responseListeners = ConcurrentHashMap<String, RedissonTopicTransport.ListenerToken>()
    private val responseListenerUsage = ConcurrentHashMap<String, AtomicInteger>()
    private val pendingResponses = ConcurrentHashMap<String, PendingResponse>()
    private val inFlightRequests = AtomicInteger(0)

    fun <Req : Any, Res : Any> registerResponder(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        val key = normalize(channel)
        val handlers = requestHandlers.computeIfAbsent(key) { CopyOnWriteArrayList() }
        val registration =
            RegisteredRequestHandler(
                tryHandle = { invocation ->
                    val decodedRequest =
                        runCatching { requestCodec.decode(invocation.payload) }
                            .getOrNull()
                            ?: return@RegisteredRequestHandler null

                    runCatching {
                        val result =
                            handler.handle(
                                BridgeRequestContext(
                                    requestId = invocation.requestId,
                                    channel = invocation.channel,
                                    payload = decodedRequest,
                                    sourceNode = BridgeNodeId(invocation.sourceNode),
                                    targetNode = invocation.targetNode?.let(::BridgeNodeId),
                                ),
                            )

                        if (result.status != BridgeResponseStatus.SUCCESS) {
                            return@runCatching HandlerInvocationOutcome(
                                status = result.status,
                                message = result.message,
                            )
                        }

                        val rawPayload =
                            result.payload
                                ?: return@runCatching HandlerInvocationOutcome(
                                    status = BridgeResponseStatus.ERROR,
                                    message = "handler returned SUCCESS without payload",
                                )

                        HandlerInvocationOutcome(
                            status = BridgeResponseStatus.SUCCESS,
                            payload = responseCodec.encode(rawPayload),
                        )
                    }.getOrElse { error ->
                        HandlerInvocationOutcome(
                            status = BridgeResponseStatus.ERROR,
                            message = error.message ?: "request execution failed",
                        )
                    }
                },
            )
        handlers += registration

        ensureRequestListener(channel)

        return BridgeSubscription {
            handlers -= registration
            if (handlers.isEmpty()) {
                requestHandlers.remove(key, handlers)
                requestListeners.remove(key)?.let(transport::removeListener)
            }
        }
    }

    fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long,
        targetNode: BridgeNodeId?,
    ): CompletableFuture<BridgeResponse<Res>> {
        metrics.requestSubmitted()
        if (!tryAcquireInFlightSlot()) {
            metrics.requestRejectedBackpressure()
            return CompletableFuture.completedFuture(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = "bridge backpressure: pending request limit exceeded (max=$maxPendingRequests)",
                    responderNode = localNodeId,
                ),
            )
        }

        val requestId = BridgeService.nextRequestId()
        val future = CompletableFuture<BridgeResponse<Res>>()
        val requestTopic = transport.topicName(type = "req", channel = channel)
        val inFlightReleased = AtomicBoolean(false)
        val cleanupExecuted = AtomicBoolean(false)
        var timeoutTask: ScheduledFuture<*>? = null
        var responseListenerKey: String? = null
        fun releaseInFlight() {
            if (inFlightReleased.compareAndSet(false, true)) {
                inFlightRequests.decrementAndGet()
            }
        }
        fun cleanupRequestState() {
            if (!cleanupExecuted.compareAndSet(false, true)) {
                return
            }
            releaseInFlight()
            pendingResponses.remove(requestId)
            timeoutTask?.cancel(false)
            responseListenerKey?.let(::releaseResponseListener)
        }

        future.whenComplete { _, _ ->
            cleanupRequestState()
        }

        runCatching {
            responseListenerKey = acquireResponseListener(channel)

            pendingResponses[requestId] =
                PendingResponse(
                    expectedResponderNode = targetNode?.value,
                ) { envelope ->
                    if (future.isDone) {
                        return@PendingResponse
                    }
                    val response =
                        when (envelope.status) {
                            BridgeResponseStatus.SUCCESS -> {
                                val rawPayload = envelope.payload ?: ""
                                runCatching {
                                    BridgeResponse(
                                        status = BridgeResponseStatus.SUCCESS,
                                        payload = responseCodec.decode(rawPayload),
                                        responderNode = envelope.responderNode?.let(::BridgeNodeId),
                                    )
                                }.getOrElse { error ->
                                    metrics.decodeFailure()
                                    BridgeResponse(
                                        status = BridgeResponseStatus.ERROR,
                                        message = error.message ?: "failed to decode bridge response",
                                        responderNode = envelope.responderNode?.let(::BridgeNodeId),
                                    )
                                }
                            }

                            BridgeResponseStatus.NO_HANDLER ->
                                BridgeResponse(
                                    status = BridgeResponseStatus.NO_HANDLER,
                                    message = envelope.message,
                                    responderNode = envelope.responderNode?.let(::BridgeNodeId),
                                )

                            BridgeResponseStatus.TIMEOUT ->
                                BridgeResponse(
                                    status = BridgeResponseStatus.TIMEOUT,
                                    message = envelope.message,
                                    responderNode = envelope.responderNode?.let(::BridgeNodeId),
                                )

                            BridgeResponseStatus.ERROR ->
                                BridgeResponse(
                                    status = BridgeResponseStatus.ERROR,
                                    message = envelope.message,
                                    responderNode = envelope.responderNode?.let(::BridgeNodeId),
                                )
                            }
                    metrics.requestCompleted(response.status)
                    future.complete(response)
                }

            timeoutTask =
                if (timeoutMillis <= 0L) {
                    null
                } else {
                    timeoutExecutor.schedule(
                        {
                            val pending = pendingResponses.remove(requestId)
                            if (future.isDone || pending == null) {
                                return@schedule
                            }
                            val status =
                                if (pending.expectedResponderNode != null) {
                                    BridgeResponseStatus.NO_HANDLER
                                } else {
                                    BridgeResponseStatus.TIMEOUT
                                }
                            when (status) {
                                BridgeResponseStatus.NO_HANDLER -> metrics.requestCompleted(BridgeResponseStatus.NO_HANDLER)
                                BridgeResponseStatus.TIMEOUT -> metrics.requestCompleted(BridgeResponseStatus.TIMEOUT)
                                else -> Unit
                            }
                            val message =
                                if (status == BridgeResponseStatus.NO_HANDLER) {
                                    "target node '${pending.expectedResponderNode}' is unavailable or has no handler"
                                } else {
                                    "bridge request timed out after ${timeoutMillis}ms"
                                }
                            future.complete(
                                BridgeResponse(
                                    status = status,
                                    message = message,
                                    responderNode = null,
                                ),
                            )
                        },
                        timeoutMillis,
                        TimeUnit.MILLISECONDS,
                    )
                }

            val encodedPayload =
                runCatching { requestCodec.encode(payload) }
                    .getOrElse { error ->
                        metrics.decodeFailure()
                        throw IllegalStateException(
                            error.message ?: "failed to encode bridge request payload",
                            error,
                        )
                    }
            val requestEnvelope =
                RequestEnvelope(
                    requestId = requestId,
                    sourceNode = localNodeId.value,
                    targetNode = targetNode?.value,
                    payload = encodedPayload,
                )
            transport.publishRaw(requestTopic, requestEnvelope.encode())
        }.onFailure { error ->
            metrics.requestCompleted(BridgeResponseStatus.ERROR)
            val message =
                error.message
                    ?.ifBlank { null }
                    ?: "failed to initialize bridge request pipeline"
            future.complete(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = message,
                    responderNode = localNodeId,
                ),
            )
        }

        return future
    }

    fun close() {
        requestListeners.values.forEach(transport::removeListener)
        responseListeners.values.forEach(transport::removeListener)
        requestListeners.clear()
        responseListeners.clear()
        responseListenerUsage.clear()
        requestHandlers.clear()
        pendingResponses.values.forEach { pending -> pending.complete(timeoutEnvelope()) }
        pendingResponses.clear()
    }

    fun pendingRequestCount(): Int {
        return inFlightRequests.get()
    }

    private fun ensureRequestListener(channel: BridgeChannel) {
        val key = normalize(channel)
        requestListeners.computeIfAbsent(key) {
            transport.addListener(type = "req", channel = channel) { message ->
                handleRequest(channel, message)
            }
        }
    }

    private fun handleRequest(
        channel: BridgeChannel,
        message: String,
    ) {
        val envelope =
            RequestEnvelope.decode(message) ?: run {
                metrics.decodeFailure()
                return
            }
        if (envelope.targetNode != null && envelope.targetNode != localNodeId.value) {
            return
        }

        val key = normalize(channel)
        val handlers = requestHandlers[key]
        if (handlers.isNullOrEmpty()) {
            transport.publish(
                type = "res",
                channel = channel,
                payload =
                    ResponseEnvelope(
                        requestId = envelope.requestId,
                        status = BridgeResponseStatus.NO_HANDLER,
                        message = "no handler registered",
                        payload = null,
                        responderNode = localNodeId.value,
                    ).encode(),
            )
            return
        }

        val responseEnvelope = resolveRequestResponse(channel, envelope, handlers)

        transport.publish(type = "res", channel = channel, payload = responseEnvelope.encode())
    }

    private fun acquireResponseListener(channel: BridgeChannel): String {
        val key = normalize(channel)
        val usage = responseListenerUsage.computeIfAbsent(key) { AtomicInteger(0) }
        usage.incrementAndGet()
        runCatching {
            responseListeners.computeIfAbsent(key) {
                transport.addListener(type = "res", channel = channel) { message ->
                    val envelope =
                        ResponseEnvelope.decode(message) ?: run {
                            metrics.decodeFailure()
                            return@addListener
                        }
                    val pending = pendingResponses[envelope.requestId]
                    if (pending == null) {
                        metrics.responseLate()
                        return@addListener
                    }
                    val expectedResponder = pending.expectedResponderNode
                    if (expectedResponder != null && envelope.responderNode != expectedResponder) {
                        metrics.responseTargetMismatched()
                        return@addListener
                    }
                    if (pendingResponses.remove(envelope.requestId, pending)) {
                        metrics.responseMatched()
                        pending.complete(envelope)
                    }
                }
            }
        }.onFailure {
            rollbackResponseListenerUsage(key, usage)
        }.getOrThrow()
        return key
    }

    private fun rollbackResponseListenerUsage(
        key: String,
        usage: AtomicInteger,
    ) {
        val remaining = usage.decrementAndGet()
        if (remaining <= 0) {
            responseListenerUsage.remove(key, usage)
        }
    }

    private fun releaseResponseListener(key: String) {
        val usage = responseListenerUsage[key] ?: return
        val remaining = usage.decrementAndGet()
        if (remaining > 0) {
            return
        }
        if (!responseListenerUsage.remove(key, usage)) {
            return
        }
        responseListeners.remove(key)?.let(transport::removeListener)
    }

    private fun resolveRequestResponse(
        channel: BridgeChannel,
        envelope: RequestEnvelope,
        handlers: List<RegisteredRequestHandler>,
    ): ResponseEnvelope {
        val invocation =
            RequestInvocation(
                requestId = envelope.requestId,
                channel = channel,
                sourceNode = envelope.sourceNode,
                targetNode = envelope.targetNode,
                payload = envelope.payload,
            )

        handlers.forEach { registration ->
            val outcome = registration.tryHandle(invocation) ?: return@forEach
            if (outcome.status != BridgeResponseStatus.SUCCESS) {
                return ResponseEnvelope(
                    requestId = envelope.requestId,
                    status = outcome.status,
                    message = outcome.message,
                    payload = null,
                    responderNode = localNodeId.value,
                )
            }

            val payload =
                outcome.payload
                    ?: return ResponseEnvelope(
                        requestId = envelope.requestId,
                        status = BridgeResponseStatus.ERROR,
                        message = "handler returned SUCCESS without payload",
                        payload = null,
                        responderNode = localNodeId.value,
                    )
            return ResponseEnvelope(
                requestId = envelope.requestId,
                status = BridgeResponseStatus.SUCCESS,
                message = null,
                payload = payload,
                responderNode = localNodeId.value,
            )
        }

        return ResponseEnvelope(
            requestId = envelope.requestId,
            status = BridgeResponseStatus.NO_HANDLER,
            message = "no compatible handler registered",
            payload = null,
            responderNode = localNodeId.value,
        )
    }

    private fun timeoutEnvelope(): ResponseEnvelope {
        return ResponseEnvelope(
            requestId = BridgeService.nextRequestId(),
            status = BridgeResponseStatus.TIMEOUT,
            message = "bridge dispatcher closed before response completed",
            payload = null,
            responderNode = localNodeId.value,
        )
    }

    private fun normalize(channel: BridgeChannel): String {
        return channel.asString().lowercase()
    }

    private fun tryAcquireInFlightSlot(): Boolean {
        while (true) {
            val current = inFlightRequests.get()
            if (current >= maxPendingRequests) {
                return false
            }
            if (inFlightRequests.compareAndSet(current, current + 1)) {
                return true
            }
        }
    }

    private data class RequestInvocation(
        val requestId: String,
        val channel: BridgeChannel,
        val sourceNode: String,
        val targetNode: String?,
        val payload: String,
    )

    private data class HandlerInvocationOutcome(
        val status: BridgeResponseStatus,
        val message: String? = null,
        val payload: String? = null,
    )

    private data class RegisteredRequestHandler(
        val tryHandle: (RequestInvocation) -> HandlerInvocationOutcome?,
    )

    private data class PendingResponse(
        val expectedResponderNode: String?,
        val complete: (ResponseEnvelope) -> Unit,
    )
}
