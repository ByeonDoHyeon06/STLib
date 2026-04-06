package studio.singlethread.lib.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class STLibRuntimeConfigMigrationTest {
    @Test
    fun `stlib runtime config migration should upgrade to latest version`() {
        val config =
            STLibRuntimeConfig().also {
                it.version = 1
                it.dashboard.flushIntervalSeconds = 0
                it.dashboard.profile = ""
            }

        val result = STLibRuntimeConfigMigration.plan.apply(config)

        assertTrue(result.migrated)
        assertEquals(1, result.fromVersion)
        assertEquals(STLibRuntimeConfigMigration.LATEST_VERSION, result.toVersion)
        assertEquals(STLibRuntimeConfigMigration.LATEST_VERSION, config.version)
        assertEquals(30, config.dashboard.flushIntervalSeconds)
        assertEquals("core_ops", config.dashboard.profile)
        assertEquals(false, config.dashboard.persistStats)
        assertEquals(false, config.metrics.command.enabled)
    }
}
