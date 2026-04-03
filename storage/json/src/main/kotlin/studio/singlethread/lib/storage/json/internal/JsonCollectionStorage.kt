package studio.singlethread.lib.storage.json.internal

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.util.concurrent.CompletableFuture

internal class JsonCollectionStorage(
    private val storage: Storage,
    override val name: String,
) : CollectionStorage {
    override fun <T> set(key: String, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return storage.set(Query(name, key), data, codec)
    }

    override fun <T> get(key: String, codec: StorageCodec<T>): CompletableFuture<T?> {
        return storage.get(Query(name, key), codec)
    }

    override fun remove(key: String): CompletableFuture<Boolean> {
        return storage.remove(Query(name, key))
    }

    override fun exists(key: String): CompletableFuture<Boolean> {
        return storage.exists(Query(name, key))
    }

    override fun <T> setSync(key: String, data: T, codec: StorageCodec<T>): WriteResult {
        return storage.setSync(Query(name, key), data, codec)
    }

    override fun <T> getSync(key: String, codec: StorageCodec<T>): T? {
        return storage.getSync(Query(name, key), codec)
    }

    override fun removeSync(key: String): Boolean {
        return storage.removeSync(Query(name, key))
    }

    override fun existsSync(key: String): Boolean {
        return storage.existsSync(Query(name, key))
    }
}
