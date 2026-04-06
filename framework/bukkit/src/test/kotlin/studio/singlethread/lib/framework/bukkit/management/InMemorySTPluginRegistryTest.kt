package studio.singlethread.lib.framework.bukkit.management

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.capability.CapabilityState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class InMemorySTPluginRegistryTest {
    @Test
    fun `registry should track stplugin lifecycle with version and status`() {
        val clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneOffset.UTC)
        val registry = InMemorySTPluginRegistry(clock)
        val descriptor = STPluginDescriptor("example-plugin", "1.2.3", "sample.ExamplePlugin")

        registry.register(descriptor)
        registry.markEnabled(descriptor.name)
        registry.markDisabled(descriptor.name)

        val snapshot = registry.find(descriptor.name)
        assertNotNull(snapshot)
        assertEquals("1.2.3", snapshot?.version)
        assertEquals(STPluginStatus.DISABLED, snapshot?.status)
        assertEquals("sample.ExamplePlugin", snapshot?.mainClass)
        assertEquals(1, snapshot?.enableCount)
        assertEquals(1, snapshot?.disableCount)
    }

    @Test
    fun `registry snapshot should be sorted by plugin name`() {
        val clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneOffset.UTC)
        val registry = InMemorySTPluginRegistry(clock)

        registry.register(STPluginDescriptor("zeta", "1.0.0", "zeta.Main"))
        registry.register(STPluginDescriptor("alpha", "1.0.0", "alpha.Main"))

        val names = registry.snapshot().map { it.name }
        assertEquals(listOf("alpha", "zeta"), names)
    }

    @Test
    fun `registry should track command registration and execution stats`() {
        val clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneOffset.UTC)
        val registry = InMemorySTPluginRegistry(clock)
        val descriptor = STPluginDescriptor("example-plugin", "1.0.0", "sample.ExamplePlugin")

        registry.register(descriptor)
        registry.configureCommandMetrics(true)
        registry.markCommandRegistered(descriptor.name)
        registry.markCommandRegistered(descriptor.name)
        val executedAt = Instant.parse("2026-04-02T00:01:00Z")
        registry.markCommandExecuted(descriptor.name, at = executedAt)

        val snapshot = registry.find(descriptor.name)
        assertNotNull(snapshot)
        assertEquals(2, snapshot?.registeredCommandCount)
        assertEquals(1, snapshot?.executedCommandCount)
        assertEquals(executedAt, snapshot?.lastCommandAt)
    }

    @Test
    fun `registry should skip command metrics when command metrics are disabled`() {
        val clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneOffset.UTC)
        val registry = InMemorySTPluginRegistry(clock)
        val descriptor = STPluginDescriptor("example-plugin", "1.0.0", "sample.ExamplePlugin")

        registry.register(descriptor)
        registry.markCommandRegistered(descriptor.name)
        registry.markCommandExecuted(descriptor.name, at = Instant.parse("2026-04-02T00:01:00Z"))

        val snapshot = registry.find(descriptor.name)
        assertNotNull(snapshot)
        assertEquals(0, snapshot?.registeredCommandCount)
        assertEquals(0, snapshot?.executedCommandCount)
    }

    @Test
    fun `registry should sync capability summary counts`() {
        val clock = Clock.fixed(Instant.parse("2026-04-02T00:00:00Z"), ZoneOffset.UTC)
        val registry = InMemorySTPluginRegistry(clock)
        val descriptor = STPluginDescriptor("example-plugin", "1.0.0", "sample.ExamplePlugin")
        registry.register(descriptor)

        val updatedAt = Instant.parse("2026-04-02T00:02:00Z")
        registry.syncCapabilitySummary(
            pluginName = descriptor.name,
            capabilitySnapshot = mapOf(
                "storage:json" to CapabilityState(enabled = true),
                "storage:jdbc" to CapabilityState(enabled = false, reason = "missing driver"),
                "text:translation" to CapabilityState(enabled = true),
            ),
            at = updatedAt,
        )

        val snapshot = registry.find(descriptor.name)
        assertNotNull(snapshot)
        assertEquals(2, snapshot?.capabilityEnabledCount)
        assertEquals(1, snapshot?.capabilityDisabledCount)
        assertEquals(updatedAt, snapshot?.capabilityUpdatedAt)
    }
}
