package studio.singlethread.lib.framework.api.config

interface VersionedConfig {
    var version: Int
}

fun interface ConfigMigrationAction<T : VersionedConfig> {
    fun migrate(config: T)
}

fun interface ConfigMigrationPlanDefinition<T : VersionedConfig> {
    fun define(builder: ConfigMigrationPlanBuilder<T>)
}

data class ConfigMigrationStep<T : VersionedConfig>(
    val fromVersion: Int,
    val toVersion: Int,
    val migrate: ConfigMigrationAction<T>,
)

data class AppliedConfigMigrationStep(
    val fromVersion: Int,
    val toVersion: Int,
)

data class ConfigMigrationResult(
    val fromVersion: Int,
    val toVersion: Int,
    val appliedSteps: List<AppliedConfigMigrationStep>,
) {
    val migrated: Boolean
        get() = appliedSteps.isNotEmpty()
}

class ConfigMigrationPlan<T : VersionedConfig> internal constructor(
    val latestVersion: Int,
    private val stepsByFromVersion: Map<Int, ConfigMigrationStep<T>>,
) {
    init {
        require(latestVersion >= 1) { "latestVersion must be >= 1" }
    }

    fun apply(config: T): ConfigMigrationResult {
        val startVersion = config.version
        if (startVersion > latestVersion) {
            error(
                "Config version ${config.version} is newer than supported latest version $latestVersion",
            )
        }

        val applied = mutableListOf<AppliedConfigMigrationStep>()
        var currentVersion = startVersion
        while (currentVersion < latestVersion) {
            val step =
                stepsByFromVersion[currentVersion]
                    ?: error("Missing config migration step: $currentVersion -> ... (latest=$latestVersion)")

            step.migrate.migrate(config)
            config.version = step.toVersion
            applied += AppliedConfigMigrationStep(step.fromVersion, step.toVersion)
            currentVersion = config.version
        }

        return ConfigMigrationResult(
            fromVersion = startVersion,
            toVersion = config.version,
            appliedSteps = applied,
        )
    }
}

class ConfigMigrationPlanBuilder<T : VersionedConfig> internal constructor() {
    private val steps = linkedMapOf<Int, ConfigMigrationStep<T>>()

    fun step(
        fromVersion: Int,
        toVersion: Int,
        migrate: ConfigMigrationAction<T>,
    ) {
        require(fromVersion >= 1) { "fromVersion must be >= 1" }
        require(toVersion > fromVersion) { "toVersion must be greater than fromVersion" }
        require(fromVersion !in steps) { "Duplicate migration step for fromVersion=$fromVersion" }
        steps[fromVersion] = ConfigMigrationStep(fromVersion, toVersion, migrate)
    }

    internal fun build(latestVersion: Int): ConfigMigrationPlan<T> {
        return ConfigMigrationPlan(latestVersion = latestVersion, stepsByFromVersion = steps.toMap())
    }
}

fun <T : VersionedConfig> configMigrationPlan(
    latestVersion: Int,
    definition: ConfigMigrationPlanDefinition<T>,
): ConfigMigrationPlan<T> {
    val migrationBuilder = ConfigMigrationPlanBuilder<T>()
    definition.define(migrationBuilder)
    return migrationBuilder.build(latestVersion)
}
