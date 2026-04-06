package studio.singlethread.lib.command

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.bukkit.management.STPluginSnapshot
import studio.singlethread.lib.framework.bukkit.management.STPluginStatus
import studio.singlethread.lib.operations.STLibStatusPlugin
import studio.singlethread.lib.operations.STLibStatusSnapshotAssembler
import java.time.Instant

class STLibStatusSnapshotAssemblerTest {
    @Test
    fun `when snapshots are provided, assembles command runtime snapshot`() {
        val assembler =
            STLibStatusSnapshotAssembler(
                storageBackend = { "json" },
                plugins = {
                    listOf(
                        snapshot(name = "Alpha", version = "1.0.0", status = STPluginStatus.ENABLED),
                        snapshot(name = "Beta", version = "2.3.4", status = STPluginStatus.DISABLED),
                    )
                },
            )

        val runtime = assembler.snapshot()

        assertEquals("json", runtime.storageBackend)
        assertEquals(2, runtime.plugins.size)
        assertEquals(STLibStatusPlugin(name = "Alpha", version = "1.0.0", status = "enabled"), runtime.plugins[0])
        assertEquals(STLibStatusPlugin(name = "Beta", version = "2.3.4", status = "disabled"), runtime.plugins[1])
    }

    private fun snapshot(
        name: String,
        version: String,
        status: STPluginStatus,
    ): STPluginSnapshot {
        return STPluginSnapshot(
            name = name,
            version = version,
            mainClass = "sample.$name",
            status = status,
            loadedAt = Instant.parse("2026-04-02T10:00:00Z"),
            enabledAt = null,
            disabledAt = null,
            enableCount = 0,
            disableCount = 0,
            registeredCommandCount = 0,
            executedCommandCount = 0,
            lastCommandAt = null,
            capabilityEnabledCount = 0,
            capabilityDisabledCount = 0,
            capabilityUpdatedAt = null,
        )
    }
}
