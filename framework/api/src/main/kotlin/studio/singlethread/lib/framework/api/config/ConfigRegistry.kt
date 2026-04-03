package studio.singlethread.lib.framework.api.config

interface ConfigRegistry {
    fun <T : Any> register(fileName: String, type: Class<T>): T

    fun <T> register(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig

    fun <T : Any> current(fileName: String, type: Class<T>): T?

    fun <T : Any> reload(fileName: String, type: Class<T>): T

    fun <T> reload(
        fileName: String,
        type: Class<T>,
        migrationPlan: ConfigMigrationPlan<T>,
    ): T where T : Any, T : VersionedConfig

    fun <T : Any> save(fileName: String, value: T, type: Class<T>)

    fun reloadAll(): Map<String, Any>
}

inline fun <reified T : Any> ConfigRegistry.register(fileName: String): T {
    return register(fileName, T::class.java)
}

inline fun <reified T> ConfigRegistry.register(
    fileName: String,
    migrationPlan: ConfigMigrationPlan<T>,
): T where T : Any, T : VersionedConfig {
    return register(fileName, T::class.java, migrationPlan)
}

inline fun <reified T : Any> ConfigRegistry.current(fileName: String): T? {
    return current(fileName, T::class.java)
}

inline fun <reified T : Any> ConfigRegistry.reload(fileName: String): T {
    return reload(fileName, T::class.java)
}

inline fun <reified T> ConfigRegistry.reload(
    fileName: String,
    migrationPlan: ConfigMigrationPlan<T>,
): T where T : Any, T : VersionedConfig {
    return reload(fileName, T::class.java, migrationPlan)
}

inline fun <reified T : Any> ConfigRegistry.save(fileName: String, value: T) {
    save(fileName, value, T::class.java)
}
