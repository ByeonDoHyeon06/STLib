package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeChannel
import java.lang.reflect.Proxy

internal class RedissonTopicTransport(
    private val client: Any,
    private val namespace: String,
    private val logWarning: (String) -> Unit,
    private val callbackFailureReporter: (phase: String, error: Throwable) -> Unit = { _, _ -> },
) {
    fun publish(
        type: String,
        channel: BridgeChannel,
        payload: String,
    ) {
        publishRaw(topicName(type, channel), payload)
    }

    fun publishRaw(
        topicName: String,
        payload: String,
    ) {
        runCatching {
            val topic = topic(topicName)
            topic.javaClass.getMethod("publish", Any::class.java).invoke(topic, payload)
        }.onFailure { error ->
            logWarning("Redisson publish failed for topic '$topicName': ${error.message ?: "unknown"}")
        }
    }

    fun addListener(
        type: String,
        channel: BridgeChannel,
        consumer: (String) -> Unit,
    ): ListenerToken {
        return addRawListener(topicName(type, channel), consumer)
    }

    fun addRawListener(
        topicName: String,
        consumer: (String) -> Unit,
    ): ListenerToken {
        val topic = topic(topicName)
        val listenerType = Class.forName("org.redisson.api.listener.MessageListener")

        val proxy =
            Proxy.newProxyInstance(
                listenerType.classLoader,
                arrayOf(listenerType),
            ) { _, method, args ->
                if (method.name == "onMessage" && args != null && args.size >= 2) {
                    runCatching {
                        consumer(args[1]?.toString().orEmpty())
                    }.onFailure { error ->
                        callbackFailureReporter("listener:$topicName", error)
                    }
                }
                null
            }

        val addMethod = topic.javaClass.getMethod("addListener", Class::class.java, listenerType)
        val listenerId = (addMethod.invoke(topic, String::class.java, proxy) as Number).toInt()
        return ListenerToken(topic = topic, listenerId = listenerId)
    }

    fun removeListener(token: ListenerToken) {
        runCatching {
            val removeMethod = token.topic.javaClass.getMethod("removeListener", Int::class.javaPrimitiveType)
            removeMethod.invoke(token.topic, token.listenerId)
        }.onFailure { error ->
            logWarning("Redisson listener removal failed for listener=${token.listenerId}: ${error.message ?: "unknown"}")
        }
    }

    fun topicName(
        type: String,
        channel: BridgeChannel,
    ): String {
        return "stlib:bridge:$namespace:$type:${normalize(channel)}"
    }

    private fun topic(name: String): Any {
        return client.javaClass.getMethod("getTopic", String::class.java).invoke(client, name)
    }

    private fun normalize(channel: BridgeChannel): String {
        return channel.asString().lowercase()
    }

    data class ListenerToken(
        val topic: Any,
        val listenerId: Int,
    )
}
