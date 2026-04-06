package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeIncomingMessage
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class InMemoryBridgeService(
    private val localNodeId: BridgeNodeId = BridgeNodeId("local"),
    private val maxPendingRequests: Int = DEFAULT_MAX_PENDING_REQUESTS,
    private val callbackFailureReporter: (phase: String, error: Throwable) -> Unit = { _, _ -> },
) : BridgeService {
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<BridgeTypedListener<String>>>()
    private val requestHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredRequestHandler>>()
    private val closed = AtomicBoolean(false)
    private val requestExecutor =
        Executors.newFixedThreadPool(
            REQUEST_EXECUTOR_THREADS,
            NamedBridgeThreadFactory("stlib-bridge-local-worker"),
        )
    private val timeoutScheduler = Executors.newSingleThreadScheduledExecutor()
    private val inFlightRequests = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<String, PendingRequest>()
    private val metrics = BridgeMetricsRecorder(pendingRequests = { inFlightRequests.get() })

    init {
        require(maxPendingRequests > 0) { "maxPendingRequests must be > 0" }
    }

    override fun nodeId(): BridgeNodeId {
        return localNodeId
    }

    override fun metrics(): BridgeMetricsSnapshot {
        return metrics.snapshot()
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        if (closed.get()) {
            return
        }
        metrics.published()

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
                }.onFailure { error ->
                    callbackFailureReporter("publish:${channel.asString()}", error)
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
                tryHandle = { invocation ->
                    val decodedRequest =
                        runCatching { requestCodec.decode(invocation.encodedPayload) }
                            .getOrNull()
                            ?: return@RegisteredRequestHandler null

                    runCatching {
                        val result =
                            handler.handle(
                                BridgeRequestContext(
                                    requestId = invocation.requestId,
                                    channel = invocation.channel,
                                    payload = decodedRequest,
                                    sourceNode = invocation.sourceNode,
                                    targetNode = invocation.targetNode,
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
                            encodedResponsePayload = responseCodec.encode(rawPayload),
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
        metrics.requestSubmitted()
        if (inFlightRequests.incrementAndGet() > maxPendingRequests) {
            inFlightRequests.decrementAndGet()
            metrics.requestRejectedBackpressure()
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = "bridge backpressure: pending request limit exceeded (max=$maxPendingRequests)",
                    responderNode = localNodeId,
                ),
            )
        }

        if (closed.get()) {
            inFlightRequests.decrementAndGet()
            metrics.requestCompleted(BridgeResponseStatus.ERROR)
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = "bridge is closed",
                    responderNode = localNodeId,
                ),
            )
        }

        if (targetNode != null && targetNode != localNodeId) {
            inFlightRequests.decrementAndGet()
            metrics.requestCompleted(BridgeResponseStatus.NO_HANDLER)
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.NO_HANDLER,
                    message = "target node '${targetNode.value}' is unavailable in local bridge",
                    responderNode = null,
                ),
            )
        }

        val key = normalize(channel)
        val handlers = requestHandlers[key]
        if (handlers.isNullOrEmpty()) {
            inFlightRequests.decrementAndGet()
            metrics.requestCompleted(BridgeResponseStatus.NO_HANDLER)
            return completedResponse(
                BridgeResponse(
                    status = BridgeResponseStatus.NO_HANDLER,
                    message = "no request handler registered for channel ${channel.asString()}",
                    responderNode = null,
                ),
            )
        }

        val encodedRequestPayload =
            runCatching { requestCodec.encode(payload) }
                .getOrElse { error ->
                    inFlightRequests.decrementAndGet()
                    metrics.decodeFailure()
                    metrics.requestCompleted(BridgeResponseStatus.ERROR)
                    return completedResponse(
                        BridgeResponse(
                            status = BridgeResponseStatus.ERROR,
                            message = error.message ?: "failed to encode bridge request payload",
                            responderNode = localNodeId,
                        ),
                    )
                }
        val requestId = BridgeService.nextRequestId()
        val responseFuture = CompletableFuture<BridgeResponse<Res>>()
        @Suppress("UNCHECKED_CAST")
        val pending =
            PendingRequest(
                future = responseFuture as CompletableFuture<BridgeResponse<*>>,
            )
        pendingRequests[requestId] = pending
        if (timeoutMillis > 0L) {
            pending.timeoutTask.set(
                timeoutScheduler.schedule(
                    {
                        completePending(
                            requestId = requestId,
                            response =
                                BridgeResponse<Any>(
                                    status = BridgeResponseStatus.TIMEOUT,
                                    message = TimeoutException("bridge request timed out after ${timeoutMillis}ms").message,
                                    responderNode = null,
                                ),
                        )
                    },
                    timeoutMillis,
                    TimeUnit.MILLISECONDS,
                ),
            )
        }
        responseFuture.whenComplete { _, _ ->
            cleanupPendingRequest(requestId)
        }

        CompletableFuture
            .supplyAsync(
                {
                    invokeHandler(
                        requestId = requestId,
                        channel = channel,
                        responseCodec = responseCodec,
                        handlers = handlers,
                        encodedRequestPayload = encodedRequestPayload,
                        targetNode = targetNode,
                    )
                },
                requestExecutor,
            ).whenComplete { response, error ->
                if (error != null) {
                    completePending(
                        requestId = requestId,
                        response =
                        BridgeResponse<Any>(
                            status = BridgeResponseStatus.ERROR,
                            message = error.message ?: "request execution failed",
                            responderNode = localNodeId,
                        ),
                    )
                    return@whenComplete
                }

                completePending(requestId = requestId, response = response)
            }
        return responseFuture
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        subscribers.clear()
        requestHandlers.clear()
        failAllPendingRequests()
        requestExecutor.shutdownNow()
        timeoutScheduler.shutdownNow()
    }

    private fun completePending(
        requestId: String,
        response: BridgeResponse<*>,
    ): Boolean {
        val pending = pendingRequests.remove(requestId) ?: return false
        if (!pending.completed.compareAndSet(false, true)) {
            return false
        }

        pending.timeoutTask.get()?.cancel(false)
        pending.future.complete(response)
        inFlightRequests.decrementAndGet()
        metrics.requestCompleted(response.status)
        return true
    }

    private fun cleanupPendingRequest(requestId: String) {
        val pending = pendingRequests.remove(requestId) ?: return
        if (!pending.completed.compareAndSet(false, true)) {
            return
        }

        pending.timeoutTask.get()?.cancel(false)
        inFlightRequests.decrementAndGet()
        metrics.requestCompleted(BridgeResponseStatus.ERROR)
    }

    private fun failAllPendingRequests() {
        val requestIds = pendingRequests.keys.toList()
        requestIds.forEach { requestId ->
            completePending(
                requestId = requestId,
                response =
                    BridgeResponse<Any>(
                        status = BridgeResponseStatus.ERROR,
                        message = "bridge is closed",
                        responderNode = localNodeId,
                    ),
            )
        }
    }

    private fun <Res : Any> invokeHandler(
        requestId: String,
        channel: BridgeChannel,
        responseCodec: BridgeCodec<Res>,
        handlers: List<RegisteredRequestHandler>,
        encodedRequestPayload: String,
        targetNode: BridgeNodeId?,
    ): BridgeResponse<Res> {
        val invocation =
            BridgeRequestInvocation(
                requestId = requestId,
                channel = channel,
                encodedPayload = encodedRequestPayload,
                sourceNode = localNodeId,
                targetNode = targetNode,
            )

        handlers.forEach { registration ->
            val outcome = registration.tryHandle(invocation) ?: return@forEach
            if (outcome.status != BridgeResponseStatus.SUCCESS) {
                return BridgeResponse(
                    status = outcome.status,
                    message = outcome.message,
                    responderNode = localNodeId,
                )
            }

            val encodedResponsePayload =
                outcome.encodedResponsePayload
                    ?: return BridgeResponse(
                        status = BridgeResponseStatus.ERROR,
                        message = "handler returned SUCCESS without payload",
                        responderNode = localNodeId,
                    )
            return runCatching {
                BridgeResponse(
                    status = BridgeResponseStatus.SUCCESS,
                    payload = responseCodec.decode(encodedResponsePayload),
                    responderNode = localNodeId,
                )
            }.getOrElse { error ->
                metrics.decodeFailure()
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = error.message ?: "failed to decode bridge response payload",
                    responderNode = localNodeId,
                )
            }
        }

        return BridgeResponse(
            status = BridgeResponseStatus.NO_HANDLER,
            message = "no compatible request handler registered for channel ${channel.asString()}",
            responderNode = null,
        )
    }

    private data class BridgeRequestInvocation(
        val requestId: String,
        val channel: BridgeChannel,
        val encodedPayload: String,
        val sourceNode: BridgeNodeId,
        val targetNode: BridgeNodeId?,
    )

    private data class HandlerInvocationOutcome(
        val status: BridgeResponseStatus,
        val message: String? = null,
        val encodedResponsePayload: String? = null,
    )

    private data class RegisteredRequestHandler(
        val tryHandle: (BridgeRequestInvocation) -> HandlerInvocationOutcome?,
    )

    private data class PendingRequest(
        val future: CompletableFuture<BridgeResponse<*>>,
        val timeoutTask: AtomicReference<ScheduledFuture<*>?> = AtomicReference(null),
        val completed: AtomicBoolean = AtomicBoolean(false),
    )

    private companion object {
        private const val DEFAULT_MAX_PENDING_REQUESTS = 2_048
        private val REQUEST_EXECUTOR_THREADS = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
    }

    private class NamedBridgeThreadFactory(
        private val prefix: String,
    ) : java.util.concurrent.ThreadFactory {
        private val delegate = Executors.defaultThreadFactory()
        private val sequence = AtomicLong(0)

        override fun newThread(runnable: Runnable): Thread {
            val thread = delegate.newThread(runnable)
            thread.name = "$prefix-${sequence.incrementAndGet()}"
            thread.isDaemon = true
            return thread
        }
    }

    private fun normalize(channel: BridgeChannel): String {
        return channel.asString().trim().lowercase()
    }

    private fun <T : Any> completedResponse(response: BridgeResponse<T>): CompletableFuture<BridgeResponse<T>> {
        return CompletableFuture.completedFuture(response)
    }

}
