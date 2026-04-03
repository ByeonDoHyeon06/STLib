package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestContext
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.bukkit.config.RedisBridgeSettings
import java.lang.reflect.Proxy
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RedissonBridgeService private constructor(
    private val localNodeId: BridgeNodeId,
    private val namespace: String,
    private val client: Any,
) : BridgeService {
    private val closed = AtomicBoolean(false)
    private val requestHandlers = ConcurrentHashMap<String, CopyOnWriteArrayList<RegisteredRequestHandler>>()
    private val requestListeners = ConcurrentHashMap<String, TopicListener>()
    private val timeoutExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun nodeId(): BridgeNodeId = localNodeId

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        if (closed.get()) {
            return
        }
        publishTopic(topicName(type = "pub", channel = channel), payload)
    }

    override fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        if (closed.get()) {
            return BridgeSubscription {}
        }

        val topic = topicName(type = "pub", channel = channel)
        val topicListener =
            addTopicListener(topic) { payload ->
                listener.onMessage(channel.asString(), payload)
            }

        return BridgeSubscription {
            removeTopicListener(topicListener)
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

        ensureRequestListener(channel)

        return BridgeSubscription {
            handlers -= registration
            if (handlers.isEmpty()) {
                requestHandlers.remove(key, handlers)
                requestListeners.remove(key)?.let(::removeTopicListener)
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
            return CompletableFuture.completedFuture(
                BridgeResponse(
                    status = BridgeResponseStatus.ERROR,
                    message = "bridge is closed",
                    responderNode = localNodeId,
                ),
            )
        }

        val requestId = BridgeService.nextRequestId()
        val future = CompletableFuture<BridgeResponse<Res>>()
        val responseTopic = topicName(type = "res", channel = channel)
        val requestTopic = topicName(type = "req", channel = channel)

        val responseListener =
            addTopicListener(responseTopic) { payloadMessage ->
                val envelope = ResponseEnvelope.decode(payloadMessage) ?: return@addTopicListener
                if (envelope.requestId != requestId) {
                    return@addTopicListener
                }

                if (future.isDone) {
                    return@addTopicListener
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
                future.complete(response)
            }

        val timeoutTask =
            timeoutExecutor.schedule(
                {
                    if (future.isDone) {
                        return@schedule
                    }
                    future.complete(
                        BridgeResponse(
                            status = BridgeResponseStatus.TIMEOUT,
                            message = "bridge request timed out after ${timeoutMillis}ms",
                            responderNode = null,
                        ),
                    )
                },
                timeoutMillis.coerceAtLeast(1L),
                TimeUnit.MILLISECONDS,
            )

        future.whenComplete { _, _ ->
            timeoutTask.cancel(false)
            removeTopicListener(responseListener)
        }

        val encodedPayload = requestCodec.encode(payload)
        val requestEnvelope =
            RequestEnvelope(
                requestId = requestId,
                sourceNode = localNodeId.value,
                targetNode = targetNode?.value,
                payload = encodedPayload,
            )
        publishTopic(requestTopic, requestEnvelope.encode())

        return future
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        requestListeners.values.forEach(::removeTopicListener)
        requestListeners.clear()
        requestHandlers.clear()
        timeoutExecutor.shutdownNow()

        runCatching {
            client.javaClass.getMethod("shutdown").invoke(client)
        }
    }

    private fun ensureRequestListener(channel: BridgeChannel) {
        val key = normalize(channel)
        requestListeners.computeIfAbsent(key) {
            val topic = topicName(type = "req", channel = channel)
            addTopicListener(topic) { message ->
                handleRequest(channel, message)
            }
        }
    }

    private fun handleRequest(
        channel: BridgeChannel,
        message: String,
    ) {
        val envelope = RequestEnvelope.decode(message) ?: return
        if (envelope.targetNode != null && envelope.targetNode != localNodeId.value) {
            return
        }

        val key = normalize(channel)
        val registration = requestHandlers[key]?.firstOrNull()
        if (registration == null) {
            publishTopic(
                topicName(type = "res", channel = channel),
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

        val responseEnvelope =
            runCatching {
                val decodedRequest = registration.requestCodec.decode(envelope.payload)
                val result =
                    registration.handler.handle(
                        BridgeRequestContext(
                            requestId = envelope.requestId,
                            channel = channel,
                            payload = decodedRequest,
                            sourceNode = BridgeNodeId(envelope.sourceNode),
                            targetNode = envelope.targetNode?.let(::BridgeNodeId),
                        ),
                    )

                if (result.status != BridgeResponseStatus.SUCCESS) {
                    return@runCatching ResponseEnvelope(
                        requestId = envelope.requestId,
                        status = result.status,
                        message = result.message,
                        payload = null,
                        responderNode = localNodeId.value,
                    )
                }

                val payload =
                    result.payload?.let(registration.responseCodec::encode)
                        ?: return@runCatching ResponseEnvelope(
                            requestId = envelope.requestId,
                            status = BridgeResponseStatus.ERROR,
                            message = "handler returned SUCCESS without payload",
                            payload = null,
                            responderNode = localNodeId.value,
                        )

                ResponseEnvelope(
                    requestId = envelope.requestId,
                    status = BridgeResponseStatus.SUCCESS,
                    message = null,
                    payload = payload,
                    responderNode = localNodeId.value,
                )
            }.getOrElse { error ->
                ResponseEnvelope(
                    requestId = envelope.requestId,
                    status = BridgeResponseStatus.ERROR,
                    message = error.message ?: "request execution failed",
                    payload = null,
                    responderNode = localNodeId.value,
                )
            }

        publishTopic(topicName(type = "res", channel = channel), responseEnvelope.encode())
    }

    private fun publishTopic(
        topicName: String,
        payload: String,
    ) {
        runCatching {
            val topic = topic(topicName)
            topic.javaClass.getMethod("publish", Any::class.java).invoke(topic, payload)
        }
    }

    private fun addTopicListener(
        topicName: String,
        consumer: (String) -> Unit,
    ): TopicListener {
        val topic = topic(topicName)
        val listenerType = Class.forName("org.redisson.api.listener.MessageListener")

        val proxy =
            Proxy.newProxyInstance(
                listenerType.classLoader,
                arrayOf(listenerType),
            ) { _, method, args ->
                if (method.name == "onMessage" && args != null && args.size >= 2) {
                    consumer(args[1]?.toString().orEmpty())
                }
                null
            }

        val addMethod = topic.javaClass.getMethod("addListener", Class::class.java, listenerType)
        val listenerId = (addMethod.invoke(topic, String::class.java, proxy) as Number).toInt()
        return TopicListener(topic = topic, listenerId = listenerId)
    }

    private fun removeTopicListener(listener: TopicListener) {
        runCatching {
            val removeMethod = listener.topic.javaClass.getMethod("removeListener", Int::class.javaPrimitiveType)
            removeMethod.invoke(listener.topic, listener.listenerId)
        }
    }

    private fun topic(name: String): Any {
        return client.javaClass.getMethod("getTopic", String::class.java).invoke(client, name)
    }

    private fun topicName(
        type: String,
        channel: BridgeChannel,
    ): String {
        return "stlib:bridge:$namespace:$type:${normalize(channel)}"
    }

    private fun normalize(channel: BridgeChannel): String {
        return channel.asString().lowercase()
    }

    private data class TopicListener(
        val topic: Any,
        val listenerId: Int,
    )

    private data class RegisteredRequestHandler(
        val requestCodec: BridgeCodec<Any>,
        val responseCodec: BridgeCodec<Any>,
        val handler: BridgeRequestHandler<Any, Any>,
    )

    private data class RequestEnvelope(
        val requestId: String,
        val sourceNode: String,
        val targetNode: String?,
        val payload: String,
    ) {
        fun encode(): String {
            return listOf(
                requestId,
                sourceNode,
                targetNode.orEmpty(),
                encodeBase64(payload),
            ).joinToString("|")
        }

        companion object {
            fun decode(raw: String): RequestEnvelope? {
                val parts = raw.split("|")
                if (parts.size != 4) {
                    return null
                }
                return RequestEnvelope(
                    requestId = parts[0],
                    sourceNode = parts[1],
                    targetNode = parts[2].ifBlank { null },
                    payload = decodeBase64(parts[3]),
                )
            }
        }
    }

    private data class ResponseEnvelope(
        val requestId: String,
        val status: BridgeResponseStatus,
        val message: String?,
        val payload: String?,
        val responderNode: String?,
    ) {
        fun encode(): String {
            return listOf(
                requestId,
                status.name,
                encodeBase64(message.orEmpty()),
                encodeBase64(payload.orEmpty()),
                responderNode.orEmpty(),
            ).joinToString("|")
        }

        companion object {
            fun decode(raw: String): ResponseEnvelope? {
                val parts = raw.split("|")
                if (parts.size != 5) {
                    return null
                }
                return ResponseEnvelope(
                    requestId = parts[0],
                    status =
                        runCatching { BridgeResponseStatus.valueOf(parts[1]) }
                            .getOrElse { return null },
                    message = decodeBase64(parts[2]).ifBlank { null },
                    payload = decodeBase64(parts[3]).ifBlank { null },
                    responderNode = parts[4].ifBlank { null },
                )
            }
        }
    }

    companion object {
        fun createOrNull(
            nodeId: BridgeNodeId,
            namespace: String,
            redis: RedisBridgeSettings,
            logWarning: (String) -> Unit,
        ): RedissonBridgeService? {
            return runCatching {
                val configClass = Class.forName("org.redisson.config.Config")
                val config = configClass.getConstructor().newInstance()
                val singleServer = configClass.getMethod("useSingleServer").invoke(config)

                singleServer.javaClass.getMethod("setAddress", String::class.java)
                    .invoke(singleServer, redis.address.trim())
                if (redis.username.isNotBlank()) {
                    runCatching {
                        singleServer.javaClass.getMethod("setUsername", String::class.java)
                            .invoke(singleServer, redis.username.trim())
                    }
                }
                if (redis.password.isNotBlank()) {
                    runCatching {
                        singleServer.javaClass.getMethod("setPassword", String::class.java)
                            .invoke(singleServer, redis.password)
                    }
                }
                runCatching {
                    singleServer.javaClass.getMethod("setDatabase", Int::class.javaPrimitiveType)
                        .invoke(singleServer, redis.database.coerceAtLeast(0))
                }
                runCatching {
                    singleServer.javaClass.getMethod("setConnectTimeout", Int::class.javaPrimitiveType)
                        .invoke(singleServer, redis.connectTimeoutMillis.coerceAtLeast(1L).toInt())
                }

                val redissonClass = Class.forName("org.redisson.Redisson")
                val client = redissonClass.getMethod("create", configClass).invoke(null, config)
                RedissonBridgeService(
                    localNodeId = nodeId,
                    namespace = namespace,
                    client = client,
                )
            }.onFailure { error ->
                logWarning("Redisson bridge initialization failed: ${error.message}")
            }.getOrNull()
        }
    }
}

private fun encodeBase64(value: String): String {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeBase64(value: String): String {
    if (value.isBlank()) {
        return ""
    }
    return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
