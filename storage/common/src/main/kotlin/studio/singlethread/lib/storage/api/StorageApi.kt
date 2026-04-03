package studio.singlethread.lib.storage.api

import studio.singlethread.lib.storage.api.config.StorageConfig

interface StorageApi : AutoCloseable {
    fun create(config: StorageConfig): Storage

    override fun close()
}
