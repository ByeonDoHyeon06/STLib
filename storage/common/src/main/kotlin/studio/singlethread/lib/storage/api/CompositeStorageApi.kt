package studio.singlethread.lib.storage.api

import studio.singlethread.lib.storage.api.config.StorageConfig
import java.util.concurrent.CopyOnWriteArrayList

class CompositeStorageApi(
    private val factories: List<StorageFactory>,
) : StorageApi {
    private val openedStorages = CopyOnWriteArrayList<Storage>()

    override fun create(config: StorageConfig): Storage {
        val factory = factories.firstOrNull { it.supports(config.databaseConfig) }
            ?: error("No storage factory found for ${config.databaseConfig::class.qualifiedName}")

        return factory.create(config).also { openedStorages.add(it) }
    }

    override fun close() {
        openedStorages.forEach { runCatching { it.close() } }
        openedStorages.clear()

        factories.forEach { runCatching { it.close() } }
    }
}
