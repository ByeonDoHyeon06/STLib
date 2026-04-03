package studio.singlethread.lib.storage.json

import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageFactory
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.json.internal.JsonStorage

class JsonStorageFactory(
    private val primaryThreadChecker: () -> Boolean,
) : StorageFactory {
    private val storages = mutableSetOf<JsonStorage>()

    override fun supports(databaseConfig: DatabaseConfig): Boolean {
        return databaseConfig is DatabaseConfig.Json
    }

    override fun create(config: StorageConfig): Storage {
        val dbConfig = config.databaseConfig as? DatabaseConfig.Json
            ?: error("JsonStorageFactory only supports DatabaseConfig.Json")

        val storage = JsonStorage(config = config, filePath = dbConfig.filePath, primaryThreadChecker = primaryThreadChecker)
        synchronized(storages) {
            storages.add(storage)
        }

        return storage
    }

    override fun close() {
        synchronized(storages) {
            storages.forEach { runCatching { it.close() } }
            storages.clear()
        }
    }
}
