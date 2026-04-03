package studio.singlethread.lib.framework.bukkit.storage

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.util.concurrent.CompletableFuture

/**
 * Defers default storage creation until a plugin actually performs a storage operation.
 */
class LazyStorage(
    private val createDelegate: () -> Storage,
) : Storage {
    @Volatile
    private var delegate: Storage? = null

    override fun collection(name: String): CollectionStorage {
        return delegate().collection(name)
    }

    override fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return delegate().set(query, data, codec)
    }

    override fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?> {
        return delegate().get(query, codec)
    }

    override fun remove(query: Query): CompletableFuture<Boolean> {
        return delegate().remove(query)
    }

    override fun exists(query: Query): CompletableFuture<Boolean> {
        return delegate().exists(query)
    }

    override fun <T> setSync(query: Query, data: T, codec: StorageCodec<T>): WriteResult {
        return delegate().setSync(query, data, codec)
    }

    override fun <T> getSync(query: Query, codec: StorageCodec<T>): T? {
        return delegate().getSync(query, codec)
    }

    override fun removeSync(query: Query): Boolean {
        return delegate().removeSync(query)
    }

    override fun existsSync(query: Query): Boolean {
        return delegate().existsSync(query)
    }

    override fun close() {
        synchronized(this) {
            delegate?.close()
            delegate = null
        }
    }

    private fun delegate(): Storage {
        delegate?.let { return it }
        synchronized(this) {
            delegate?.let { return it }
            val created = createDelegate()
            delegate = created
            return created
        }
    }
}
