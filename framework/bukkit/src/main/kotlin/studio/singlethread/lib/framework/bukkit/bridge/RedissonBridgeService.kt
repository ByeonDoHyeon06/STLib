package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeIncomingMessage
import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeRequestHandler
import studio.singlethread.lib.framework.api.bridge.BridgeResponse
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import studio.singlethread.lib.framework.api.bridge.BridgeTypedListener
import studio.singlethread.lib.framework.bukkit.config.RedisBridgeSettings
import studio.singlethread.lib.framework.bukkit.support.STCallbackFailureLogger
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class RedissonBridgeService private constructor(
    private val localNodeId: BridgeNodeId,
    private val namespace: String,
    private val client: Any,
    maxPendingRequests: Int,
    private val logWarning: (String) -> Unit,
    private val logger: Logger,
    private val debugLoggingEnabled: () -> Boolean,
) : BridgeService {
    private val closed = AtomicBoolean(false)
    private val timeoutExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val transport =
        RedissonTopicTransport(
            client = client,
            namespace = namespace,
            logWarning = logWarning,
            callbackFailureReporter = { phase, error ->
                STCallbackFailureLogger.log(
                    logger = logger,
                    subsystem = "Bridge",
                    phase = phase,
                    error = error,
                    debugEnabled = debugLoggingEnabled,
                )
            },
        )
    private val metrics = BridgeMetricsRecorder()
    private val rpcDispatcher =
        RedissonRpcDispatcher(
            localNodeId = localNodeId,
            transport = transport,
            timeoutExecutor = timeoutExecutor,
            maxPendingRequests = maxPendingRequests,
            metrics = metrics,
        )

    override fun nodeId(): BridgeNodeId = localNodeId

    override fun metrics(): BridgeMetricsSnapshot {
        return metrics.snapshot(pendingRequestsOverride = rpcDispatcher.pendingRequestCount())
    }

    override fun publish(
        channel: BridgeChannel,
        payload: String,
    ) {
        if (closed.get()) {
            return
        }
        metrics.markPublished()
        transport.publish(
            type = "pub",
            channel = channel,
            payload =
                PublishEnvelope(
                    sourceNode = localNodeId.value,
                    payload = payload,
                ).encode(),
        )
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

        val token =
            transport.addListener(type = "pub", channel = channel) { payload ->
                val envelope = PublishEnvelope.decode(payload)
                val sourceNode = envelope?.sourceNode?.ifBlank { null }
                listener.onMessage(
                    BridgeIncomingMessage(
                        channel = channel,
                        payload = envelope?.payload ?: payload,
                        sourceNode = BridgeNodeId(sourceNode ?: UNKNOWN_SOURCE_NODE),
                    ),
                )
            }

        return BridgeSubscription {
            transport.removeListener(token)
        }
    }

    override fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: studio.singlethread.lib.framework.api.bridge.BridgeCodec<Req>,
        responseCodec: studio.singlethread.lib.framework.api.bridge.BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription {
        if (closed.get()) {
            return BridgeSubscription {}
        }
        return rpcDispatcher.registerResponder(channel, requestCodec, responseCodec, handler)
    }

    override fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: studio.singlethread.lib.framework.api.bridge.BridgeCodec<Req>,
        responseCodec: studio.singlethread.lib.framework.api.bridge.BridgeCodec<Res>,
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
        return rpcDispatcher.request(
            channel = channel,
            payload = payload,
            requestCodec = requestCodec,
            responseCodec = responseCodec,
            timeoutMillis = timeoutMillis,
            targetNode = targetNode,
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        rpcDispatcher.close()
        timeoutExecutor.shutdownNow()

        runCatching {
            client.javaClass.getMethod("shutdown").invoke(client)
        }
    }

    companion object {
        private const val UNKNOWN_SOURCE_NODE = "unknown"

        fun createOrNull(
            nodeId: BridgeNodeId,
            namespace: String,
            redis: RedisBridgeSettings,
            maxPendingRequests: Int,
            logWarning: (String) -> Unit,
            logger: Logger,
            debugLoggingEnabled: () -> Boolean,
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
                    maxPendingRequests = maxPendingRequests,
                    logWarning = logWarning,
                    logger = logger,
                    debugLoggingEnabled = debugLoggingEnabled,
                )
            }.onFailure { error ->
                logWarning("Redisson bridge initialization failed: ${error.describeBridgeFailure()}")
            }.getOrNull()
        }

        private fun Throwable.describeBridgeFailure(): String {
            val root = rootCause()
            val rootMessage = root.message?.takeIf { it.isNotBlank() } ?: "<no-message>"
            return if (root === this) {
                "${root.javaClass.simpleName}: $rootMessage"
            } else {
                "${this.javaClass.simpleName} -> ${root.javaClass.simpleName}: $rootMessage"
            }
        }

        private fun Throwable.rootCause(): Throwable {
            var current: Throwable = this
            while (current.cause != null && current.cause !== current) {
                current = current.cause!!
            }
            return current
        }
    }
}
