package studio.singlethread.lib.dashboard

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.scheduler.ScheduledTask
import java.util.concurrent.atomic.AtomicInteger

class StlibDashboardRuntimeControllerTest {
    @Test
    fun `initialize keeps dashboard available and disables persistence when storage capability is disabled`() {
        val service = RecordingDashboardRuntime()
        val controller =
            StlibDashboardRuntimeController(
                dashboardService = service,
                capabilityPolicy = StlibStorageCapabilityPolicy { false },
                schedulePeriodicFlush = { _, _, _ -> noOpTask() },
                async = { _ -> },
                logWarning = {},
            )

        controller.initialize()

        assertTrue(controller.isAvailable())
        assertEquals(1, service.bootstrapCount.get())
        val state = controller.state()
        assertFalse(state.storageAvailable)
        assertTrue(state.persistenceEnabled)
        assertFalse(state.persistenceActive)
    }

    @Test
    fun `initialize marks unavailable when dashboard is disabled by config`() {
        val service = RecordingDashboardRuntime()
        val controller =
            StlibDashboardRuntimeController(
                dashboardService = service,
                capabilityPolicy = StlibStorageCapabilityPolicy { true },
                schedulePeriodicFlush = { _, _, _ -> noOpTask() },
                async = { _ -> },
                logWarning = {},
                isDashboardEnabled = { false },
            )

        controller.initialize()

        assertFalse(controller.isAvailable())
        assertEquals(0, service.bootstrapCount.get())
    }

    @Test
    fun `start schedules periodic flush and stop flushes once`() {
        val service = RecordingDashboardRuntime()
        val scheduledCount = AtomicInteger(0)
        val asyncCount = AtomicInteger(0)

        val controller =
            StlibDashboardRuntimeController(
                dashboardService = service,
                capabilityPolicy = StlibStorageCapabilityPolicy { true },
                schedulePeriodicFlush = { _, _, task ->
                    scheduledCount.incrementAndGet()
                    task.run()
                    noOpTask()
                },
                async = { task ->
                    asyncCount.incrementAndGet()
                    task.run()
                },
                logWarning = {},
            )

        controller.initialize()
        controller.start()
        controller.stop()

        assertTrue(controller.isAvailable())
        assertEquals(1, service.bootstrapCount.get())
        assertEquals(2, service.flushCount.get())
        assertEquals(1, scheduledCount.get())
        assertEquals(1, asyncCount.get())
        val state = controller.state()
        assertTrue(state.storageAvailable)
        assertTrue(state.persistenceEnabled)
        assertTrue(state.persistenceActive)
    }

    @Test
    fun `when storage is unavailable, start and stop should not flush even if persistence is enabled`() {
        val service = RecordingDashboardRuntime()
        val scheduledCount = AtomicInteger(0)
        val controller =
            StlibDashboardRuntimeController(
                dashboardService = service,
                capabilityPolicy = StlibStorageCapabilityPolicy { false },
                schedulePeriodicFlush = { _, _, _ ->
                    scheduledCount.incrementAndGet()
                    noOpTask()
                },
                async = { _ -> },
                logWarning = {},
                isPersistenceEnabled = { true },
            )

        controller.initialize()
        controller.start()
        controller.stop()

        assertTrue(controller.isAvailable())
        assertEquals(1, service.bootstrapCount.get())
        assertEquals(0, scheduledCount.get())
        assertEquals(0, service.flushCount.get())
        val state = controller.state()
        assertFalse(state.storageAvailable)
        assertTrue(state.persistenceEnabled)
        assertFalse(state.persistenceActive)
    }

    @Test
    fun `when persistence is disabled, start and stop should not flush`() {
        val service = RecordingDashboardRuntime()
        val scheduledCount = AtomicInteger(0)
        val controller =
            StlibDashboardRuntimeController(
                dashboardService = service,
                capabilityPolicy = StlibStorageCapabilityPolicy { true },
                schedulePeriodicFlush = { _, _, _ ->
                    scheduledCount.incrementAndGet()
                    noOpTask()
                },
                async = { _ -> },
                logWarning = {},
                isPersistenceEnabled = { false },
            )

        controller.initialize()
        controller.start()
        controller.stop()

        assertTrue(controller.isAvailable())
        assertEquals(1, service.bootstrapCount.get())
        assertEquals(0, scheduledCount.get())
        assertEquals(0, service.flushCount.get())
        val state = controller.state()
        assertTrue(state.storageAvailable)
        assertFalse(state.persistenceEnabled)
        assertFalse(state.persistenceActive)
    }

    private fun noOpTask(): ScheduledTask {
        return ScheduledTask {}
    }
}

private class RecordingDashboardRuntime : StlibDashboardRuntime {
    val bootstrapCount = AtomicInteger(0)
    val flushCount = AtomicInteger(0)

    override fun bootstrap(loadPersisted: Boolean) {
        bootstrapCount.incrementAndGet()
    }

    override fun flush() {
        flushCount.incrementAndGet()
    }
}
