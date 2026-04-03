package studio.singlethread.lib.storage.api.config

import java.time.Duration

data class StorageConfig(
    val namespace: String,
    val databaseConfig: DatabaseConfig,
    val syncTimeout: Duration = Duration.ofSeconds(5),
    val executorThreads: Int = 4,
) {
    init {
        require(namespace.isNotBlank()) { "namespace must not be blank" }
        require(!namespace.contains(':')) { "namespace must not contain ':'" }
        require(syncTimeout.toMillis() > 0) { "syncTimeout must be positive" }
        require(executorThreads > 0) { "executorThreads must be positive" }
    }
}
