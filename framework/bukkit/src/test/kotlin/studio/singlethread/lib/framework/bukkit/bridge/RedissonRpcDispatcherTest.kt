package studio.singlethread.lib.framework.bukkit.bridge

import org.redisson.api.listener.MessageListener
import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import studio.singlethread.lib.framework.api.bridge.BridgeCodec
import studio.singlethread.lib.framework.api.bridge.BridgeNodeId
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class RedissonRpcDispatcherTest {
    @Test
    fun `request should release in-flight slot when response listener acquisition fails`() {
        val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        val metrics = BridgeMetricsRecorder()
        val dispatcher =
            RedissonRpcDispatcher(
                localNodeId = BridgeNodeId("node-a"),
                transport =
                    RedissonTopicTransport(
                        client = FailingListenerClient(),
                        namespace = "stlib",
                        logWarning = {},
                    ),
                timeoutExecutor = timeoutExecutor,
                maxPendingRequests = 8,
                metrics = metrics,
            )

        try {
            val response =
                dispatcher.request(
                    channel = BridgeChannel.of("test", "listener-failure"),
                    payload = "hello",
                    requestCodec = STRING_CODEC,
                    responseCodec = STRING_CODEC,
                    timeoutMillis = 200,
                    targetNode = null,
                ).get(1, TimeUnit.SECONDS)

            assertEquals(BridgeResponseStatus.ERROR, response.status)
            assertEquals(0, dispatcher.pendingRequestCount())
            assertEquals(1, metrics.snapshot().requestErrored)
            assertEquals(0, responseListenerUsageSize(dispatcher))
        } finally {
            dispatcher.close()
            timeoutExecutor.shutdownNow()
        }
    }

    @Test
    fun `targeted request timeout should resolve as no handler for mode consistency`() {
        val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()
        val metrics = BridgeMetricsRecorder()
        val dispatcher =
            RedissonRpcDispatcher(
                localNodeId = BridgeNodeId("node-a"),
                transport =
                    RedissonTopicTransport(
                        client = InMemoryRedissonClient(),
                        namespace = "stlib",
                        logWarning = {},
                    ),
                timeoutExecutor = timeoutExecutor,
                maxPendingRequests = 8,
                metrics = metrics,
            )

        try {
            val response =
                dispatcher.request(
                    channel = BridgeChannel.of("test", "target-timeout"),
                    payload = "hello",
                    requestCodec = STRING_CODEC,
                    responseCodec = STRING_CODEC,
                    timeoutMillis = 60,
                    targetNode = BridgeNodeId("node-b"),
                ).get(2, TimeUnit.SECONDS)

            assertEquals(BridgeResponseStatus.NO_HANDLER, response.status)
            assertEquals(0, dispatcher.pendingRequestCount())
            assertEquals(1, metrics.snapshot().requestNoHandler)
        } finally {
            dispatcher.close()
            timeoutExecutor.shutdownNow()
        }
    }

    private class FailingListenerClient {
        fun getTopic(@Suppress("UNUSED_PARAMETER") topicName: String): FailingListenerTopic {
            return FailingListenerTopic()
        }
    }

    private class FailingListenerTopic {
        fun addListener(
            @Suppress("UNUSED_PARAMETER") type: Class<*>,
            @Suppress("UNUSED_PARAMETER") listener: MessageListener<String>,
        ): Int {
            throw IllegalStateException("listener registration failed")
        }

        fun removeListener(@Suppress("UNUSED_PARAMETER") listenerId: Int) = Unit

        fun publish(@Suppress("UNUSED_PARAMETER") payload: Any) = Unit
    }

    private class InMemoryRedissonClient {
        private val topics = linkedMapOf<String, InMemoryRedissonTopic>()

        fun getTopic(topicName: String): InMemoryRedissonTopic {
            return synchronized(topics) {
                topics.getOrPut(topicName) { InMemoryRedissonTopic(topicName) }
            }
        }
    }

    private class InMemoryRedissonTopic(
        private val topicName: String,
    ) {
        private val listeners = linkedMapOf<Int, MessageListener<String>>()
        private var sequence: Int = 0

        @Synchronized
        fun addListener(
            @Suppress("UNUSED_PARAMETER") type: Class<*>,
            listener: MessageListener<String>,
        ): Int {
            sequence += 1
            listeners[sequence] = listener
            return sequence
        }

        @Synchronized
        fun removeListener(listenerId: Int) {
            listeners.remove(listenerId)
        }

        fun publish(payload: Any) {
            val message = payload.toString()
            val snapshot =
                synchronized(this) {
                    listeners.values.toList()
                }
            snapshot.forEach { listener ->
                listener.onMessage(topicName, message)
            }
        }
    }

    private companion object {
        private val STRING_CODEC =
            object : BridgeCodec<String> {
                override fun encode(value: String): String {
                    return value
                }

                override fun decode(payload: String): String {
                    return payload
                }
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun responseListenerUsageSize(dispatcher: RedissonRpcDispatcher): Int {
        val field = dispatcher.javaClass.getDeclaredField("responseListenerUsage")
        field.isAccessible = true
        val usage = field.get(dispatcher) as Map<String, *>
        return usage.size
    }
}
