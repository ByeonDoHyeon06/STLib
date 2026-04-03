package studio.singlethread.lib.storage.jdbc.internal.backend

import studio.singlethread.lib.storage.api.WriteAction

internal interface StorageBackend : AutoCloseable {
    fun initialize()

    fun upsert(
        namespace: String,
        collection: String,
        key: String,
        value: ByteArray,
        updatedAtEpochMs: Long,
    ): WriteAction

    fun get(namespace: String, collection: String, key: String): ByteArray?

    fun remove(namespace: String, collection: String, key: String): Boolean

    fun exists(namespace: String, collection: String, key: String): Boolean

    override fun close()
}
