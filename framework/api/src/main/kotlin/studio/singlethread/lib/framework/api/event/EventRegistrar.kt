package studio.singlethread.lib.framework.api.event

interface EventRegistrar<in L : Any> {
    fun listen(listener: L)

    fun unlisten(listener: L)

    fun unlistenAll()
}
