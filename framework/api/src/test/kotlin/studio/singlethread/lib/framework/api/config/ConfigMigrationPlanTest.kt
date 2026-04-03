package studio.singlethread.lib.framework.api.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigMigrationPlanTest {
    @Test
    fun `plan should migrate sequential steps to latest version`() {
        val plan =
            configMigrationPlan<ExampleVersionedConfig>(latestVersion = 3) {
                step(fromVersion = 1, toVersion = 2) { config ->
                    config.message = "v2"
                }
                step(fromVersion = 2, toVersion = 3) { config ->
                    config.enabled = false
                }
            }

        val config = ExampleVersionedConfig()
        val result = plan.apply(config)

        assertTrue(result.migrated)
        assertEquals(1, result.fromVersion)
        assertEquals(3, result.toVersion)
        assertEquals(3, config.version)
        assertEquals("v2", config.message)
        assertEquals(false, config.enabled)
        assertEquals(listOf(1 to 2, 2 to 3), result.appliedSteps.map { it.fromVersion to it.toVersion })
    }

    @Test
    fun `plan should fail when a required step is missing`() {
        val plan =
            configMigrationPlan<ExampleVersionedConfig>(latestVersion = 3) {
                step(fromVersion = 1, toVersion = 2) {}
            }

        val config = ExampleVersionedConfig()
        val exception = assertFailsWith<IllegalStateException> { plan.apply(config) }
        assertTrue(exception.message.orEmpty().contains("Missing config migration step"))
    }

    @Test
    fun `plan should fail when config version is newer than latest`() {
        val plan = configMigrationPlan<ExampleVersionedConfig>(latestVersion = 2) {}

        val config = ExampleVersionedConfig().also { it.version = 3 }
        val exception = assertFailsWith<IllegalStateException> { plan.apply(config) }
        assertTrue(exception.message.orEmpty().contains("newer than supported latest version"))
    }

    class ExampleVersionedConfig : VersionedConfig {
        override var version: Int = 1
        var message: String = "v1"
        var enabled: Boolean = true
    }
}
