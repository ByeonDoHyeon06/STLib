package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeListener
import studio.singlethread.lib.framework.api.bridge.BridgeService
import studio.singlethread.lib.framework.api.bridge.BridgeSubscription
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class InMemoryBridgeService : BridgeService {
    private val subscribers = ConcurrentHashMap<String, CopyOnWriteArrayList<BridgeListener>>()
    private val closed = AtomicBoolean(false)

    override fun publish(channel: String, payload: String) {
        if (closed.get()) {
            return
        }

        subscribers[normalize(channel)]
            ?.forEach { listener ->
                runCatching {
                    listener.onMessage(channel, payload)
                }
            }
    }

    override fun subscribe(channel: String, listener: BridgeListener): BridgeSubscription {
        if (closed.get()) {
            return BridgeSubscription {}
        }

        val normalized = normalize(channel)
        val listeners = subscribers.computeIfAbsent(normalized) { CopyOnWriteArrayList() }
        listeners += listener

        return BridgeSubscription {
            listeners -= listener
            if (listeners.isEmpty()) {
                subscribers.remove(normalized, listeners)
            }
        }
    }

    override fun close() {
        closed.set(true)
        subscribers.clear()
    }

    private fun normalize(channel: String): String {
        val normalized = channel.trim().lowercase()
        require(normalized.isNotBlank()) { "bridge channel must not be blank" }
        return normalized
    }
}
