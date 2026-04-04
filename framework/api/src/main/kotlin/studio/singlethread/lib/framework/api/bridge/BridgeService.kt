package studio.singlethread.lib.framework.api.bridge

import java.util.UUID
import java.util.concurrent.CompletableFuture

fun interface BridgeSubscription {
    fun unsubscribe()
}

fun interface BridgeListener {
    fun onMessage(channel: String, payload: String)
}

fun interface BridgePayloadListener<T : Any> {
    fun onMessage(payload: T)
}

fun interface BridgeTypedListener<T : Any> {
    fun onMessage(message: BridgeIncomingMessage<T>)
}

interface BridgeCodec<T : Any> {
    fun encode(value: T): String

    fun decode(payload: String): T
}

@JvmInline
value class BridgeNodeId(
    val value: String,
) {
    init {
        require(value.trim().isNotEmpty()) { "bridge node id must not be blank" }
    }

    override fun toString(): String = value
}

data class BridgeChannel(
    val namespace: String,
    val key: String,
) {
    init {
        require(namespace.trim().isNotEmpty()) { "bridge channel namespace must not be blank" }
        require(key.trim().isNotEmpty()) { "bridge channel key must not be blank" }
    }

    fun asString(): String {
        return "${normalizeToken(namespace)}:${normalizeToken(key)}"
    }

    companion object {
        const val DEFAULT_NAMESPACE: String = "global"

        fun of(
            namespace: String,
            key: String,
        ): BridgeChannel {
            return BridgeChannel(namespace = namespace, key = key)
        }

        fun parse(channel: String): BridgeChannel {
            val normalized = channel.trim()
            require(normalized.isNotEmpty()) { "bridge channel must not be blank" }
            val index = normalized.indexOf(':')
            if (index < 0) {
                return BridgeChannel(DEFAULT_NAMESPACE, normalized)
            }
            val namespace = normalized.substring(0, index)
            val key = normalized.substring(index + 1)
            return BridgeChannel(namespace = namespace, key = key)
        }
    }
}

enum class BridgeResponseStatus {
    SUCCESS,
    TIMEOUT,
    NO_HANDLER,
    ERROR,
}

data class BridgeIncomingMessage<T : Any>(
    val channel: BridgeChannel,
    val payload: T,
    val sourceNode: BridgeNodeId,
)

data class BridgeRequestContext<T : Any>(
    val requestId: String,
    val channel: BridgeChannel,
    val payload: T,
    val sourceNode: BridgeNodeId,
    val targetNode: BridgeNodeId?,
)

data class BridgeRequestResult<T : Any>(
    val status: BridgeResponseStatus,
    val payload: T? = null,
    val message: String? = null,
) {
    companion object {
        fun <T : Any> success(payload: T): BridgeRequestResult<T> {
            return BridgeRequestResult(status = BridgeResponseStatus.SUCCESS, payload = payload)
        }

        fun <T : Any> error(message: String): BridgeRequestResult<T> {
            return BridgeRequestResult(status = BridgeResponseStatus.ERROR, message = message)
        }
    }
}

fun interface BridgeRequestHandler<Req : Any, Res : Any> {
    fun handle(request: BridgeRequestContext<Req>): BridgeRequestResult<Res>
}

data class BridgeResponse<T : Any>(
    val status: BridgeResponseStatus,
    val payload: T? = null,
    val message: String? = null,
    val responderNode: BridgeNodeId? = null,
)

interface BridgeService : AutoCloseable {
    fun nodeId(): BridgeNodeId

    fun publish(channel: String, payload: String) {
        publish(channel = BridgeChannel.parse(channel), payload = payload)
    }

    fun subscribe(
        channel: String,
        listener: BridgeListener,
    ): BridgeSubscription {
        return subscribe(channel = BridgeChannel.parse(channel), listener = listener)
    }

    fun publish(channel: BridgeChannel, payload: String)

    fun subscribe(
        channel: BridgeChannel,
        listener: BridgeListener,
    ): BridgeSubscription {
        return subscribeWithSource(channel) { message ->
            listener.onMessage(message.channel.asString(), message.payload)
        }
    }

    fun subscribeWithSource(
        channel: BridgeChannel,
        listener: BridgeTypedListener<String>,
    ): BridgeSubscription

    fun <T : Any> publish(
        channel: BridgeChannel,
        payload: T,
        codec: BridgeCodec<T>,
    ) {
        publish(channel = channel, payload = codec.encode(payload))
    }

    fun <T : Any> subscribe(
        channel: BridgeChannel,
        codec: BridgeCodec<T>,
        listener: BridgeTypedListener<T>,
    ): BridgeSubscription {
        return subscribeWithSource(channel = channel) { incoming ->
            val decoded = codec.decode(incoming.payload)
            listener.onMessage(
                BridgeIncomingMessage(
                    channel = incoming.channel,
                    payload = decoded,
                    sourceNode = incoming.sourceNode,
                ),
            )
        }
    }

    fun <Req : Any, Res : Any> respond(
        channel: BridgeChannel,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        handler: BridgeRequestHandler<Req, Res>,
    ): BridgeSubscription

    fun <Req : Any, Res : Any> request(
        channel: BridgeChannel,
        payload: Req,
        requestCodec: BridgeCodec<Req>,
        responseCodec: BridgeCodec<Res>,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
        targetNode: BridgeNodeId? = null,
    ): CompletableFuture<BridgeResponse<Res>>

    override fun close() = Unit

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 3_000L

        fun nextRequestId(): String {
            return UUID.randomUUID().toString()
        }
    }
}

private fun normalizeToken(value: String): String {
    return value.trim().lowercase()
}
