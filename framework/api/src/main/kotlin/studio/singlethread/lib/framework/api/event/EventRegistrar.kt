package studio.singlethread.lib.framework.api.event

interface EventRegistrar {
    fun listen(listener: Any)

    fun unlisten(listener: Any)

    fun unlistenAll()
}
