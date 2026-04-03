package studio.singlethread.lib.config

import studio.singlethread.lib.framework.api.config.ConfigMigrationPlan
import studio.singlethread.lib.framework.api.config.configMigrationPlan

object StlibRuntimeConfigMigration {
    const val LATEST_VERSION: Int = 3

    val plan: ConfigMigrationPlan<StlibRuntimeConfig> =
        configMigrationPlan(latestVersion = LATEST_VERSION) {
            step(fromVersion = 1, toVersion = 2) { config ->
                if (config.dashboard.flushIntervalSeconds <= 0) {
                    config.dashboard.flushIntervalSeconds = 30
                }
            }
            step(fromVersion = 2, toVersion = 3) { config ->
                if (config.dashboard.profile.isBlank()) {
                    config.dashboard.profile = "core_ops"
                }
            }
        }
}
