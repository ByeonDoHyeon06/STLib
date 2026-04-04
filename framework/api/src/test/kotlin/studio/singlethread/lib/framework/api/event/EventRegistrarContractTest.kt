package studio.singlethread.lib.framework.api.event

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventRegistrarContractTest {
    @Test
    fun `event registrar contract should support listen unlisten lifecycle`() {
        val registrar = InMemoryEventRegistrar()
        val listener = TestListener

        registrar.listen(listener)
        registrar.listen(listener)
        registrar.unlisten(listener)
        registrar.listen(listener)
        registrar.unlistenAll()

        assertEquals(3, registrar.listenCalls)
        assertEquals(1, registrar.unlistenCalls)
        assertEquals(1, registrar.unlistenAllCalls)
    }

    private object TestListener

    private class InMemoryEventRegistrar : EventRegistrar<TestListener> {
        var listenCalls = 0
            private set

        var unlistenCalls = 0
            private set

        var unlistenAllCalls = 0
            private set

        override fun listen(listener: TestListener) {
            listenCalls++
        }

        override fun unlisten(listener: TestListener) {
            unlistenCalls++
        }

        override fun unlistenAll() {
            unlistenAllCalls++
        }
    }

}
