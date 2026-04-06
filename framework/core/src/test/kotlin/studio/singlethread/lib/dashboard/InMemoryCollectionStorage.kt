package studio.singlethread.lib.dashboard

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.WriteAction
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class InMemoryCollectionStorage(
    override val name: String = "test",
) : CollectionStorage {
    private val payloads = ConcurrentHashMap<String, ByteArray>()

    override fun <T> set(key: String, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        val encoded = codec.encode(data)
        val action = if (payloads.put(key, encoded) == null) WriteAction.INSERT else WriteAction.UPDATE
        return CompletableFuture.completedFuture(
            WriteResult(
                action = action,
                updatedAt = Instant.now(),
                durationMs = 0L,
            ),
        )
    }

    override fun <T> get(key: String, codec: StorageCodec<T>): CompletableFuture<T?> {
        val encoded = payloads[key] ?: return CompletableFuture.completedFuture(null)
        return CompletableFuture.completedFuture(codec.decode(encoded))
    }

    override fun remove(key: String): CompletableFuture<Boolean> {
        val removed = payloads.remove(key) != null
        return CompletableFuture.completedFuture(removed)
    }

    override fun exists(key: String): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(payloads.containsKey(key))
    }

    override fun <T> setSync(key: String, data: T, codec: StorageCodec<T>): WriteResult {
        return set(key, data, codec).join()
    }

    override fun <T> getSync(key: String, codec: StorageCodec<T>): T? {
        return get(key, codec).join()
    }

    override fun removeSync(key: String): Boolean {
        return remove(key).join()
    }

    override fun existsSync(key: String): Boolean {
        return exists(key).join()
    }
}
