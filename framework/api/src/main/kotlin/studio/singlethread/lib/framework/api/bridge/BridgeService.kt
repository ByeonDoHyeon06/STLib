package studio.singlethread.lib.framework.api.bridge

fun interface BridgeSubscription {
    fun unsubscribe()
}

fun interface BridgeListener {
    fun onMessage(channel: String, payload: String)
}

interface BridgeService : AutoCloseable {
    fun publish(channel: String, payload: String)

    fun subscribe(channel: String, listener: BridgeListener): BridgeSubscription

    override fun close() = Unit
}
