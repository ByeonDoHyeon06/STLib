package studio.singlethread.lib.storage.api

import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig

interface StorageFactory : AutoCloseable {
    fun supports(databaseConfig: DatabaseConfig): Boolean

    fun create(config: StorageConfig): Storage

    override fun close() {
        // no-op default
    }
}
