package studio.singlethread.lib.storage.jdbc.internal

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.api.exception.StorageBackendException
import studio.singlethread.lib.storage.api.exception.StorageException
import studio.singlethread.lib.storage.api.exception.StorageMainThreadSyncException
import studio.singlethread.lib.storage.api.exception.StorageSerializationException
import studio.singlethread.lib.storage.api.exception.StorageTimeoutException
import studio.singlethread.lib.storage.api.extensions.unwrapCompletionException
import studio.singlethread.lib.storage.jdbc.internal.backend.StorageBackend
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal class StorageImpl(
    private val config: StorageConfig,
    private val backend: StorageBackend,
    private val primaryThreadChecker: () -> Boolean,
    private val onClose: () -> Unit = {},
) : Storage {

    private val closed: AtomicBoolean = AtomicBoolean(false)
    private val collectionCache = mutableMapOf<String, CollectionStorage>()
    private val executor: ExecutorService = Executors.newFixedThreadPool(
        config.executorThreads,
        NamedThreadFactory("stlib-storage-${config.namespace}"),
    )

    override fun collection(name: String): CollectionStorage {
        require(name.isNotBlank()) { "collection name must not be blank" }
        synchronized(collectionCache) {
            return collectionCache.getOrPut(name) { CollectionStorageImpl(this, name) }
        }
    }

    override fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                val startedAt = System.nanoTime()
                val payload = encode(codec, data)
                val now = Instant.now()
                val action = runBackend { backend.upsert(config.namespace, query.collection, query.key, payload, now.toEpochMilli()) }
                WriteResult(action = action, updatedAt = now, durationMs = elapsedMs(startedAt))
            },
            executor,
        )
    }

    override fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                val payload = runBackend { backend.get(config.namespace, query.collection, query.key) } ?: return@supplyAsync null
                decode(codec, payload)
            },
            executor,
        )
    }

    override fun remove(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                runBackend { backend.remove(config.namespace, query.collection, query.key) }
            },
            executor,
        )
    }

    override fun exists(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                runBackend { backend.exists(config.namespace, query.collection, query.key) }
            },
            executor,
        )
    }

    override fun <T> setSync(query: Query, data: T, codec: StorageCodec<T>): WriteResult {
        ensureSyncCallAllowed()
        return await(set(query, data, codec))
    }

    override fun <T> getSync(query: Query, codec: StorageCodec<T>): T? {
        ensureSyncCallAllowed()
        return await(get(query, codec))
    }

    override fun removeSync(query: Query): Boolean {
        ensureSyncCallAllowed()
        return await(remove(query))
    }

    override fun existsSync(query: Query): Boolean {
        ensureSyncCallAllowed()
        return await(exists(query))
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        onClose()

        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            executor.shutdownNow()
        }
    }

    private fun ensureOpen() {
        if (closed.get()) {
            throw StorageException("Storage instance for namespace '${config.namespace}' is already closed")
        }
    }

    private fun ensureSyncCallAllowed() {
        if (primaryThreadChecker()) {
            throw StorageMainThreadSyncException(
                "Synchronous storage operations are not allowed on the Bukkit main thread. Use async APIs.",
            )
        }
    }

    private fun <T> await(future: CompletableFuture<T>): T {
        return try {
            future.get(config.syncTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            throw StorageTimeoutException(
                "Storage operation timed out after ${config.syncTimeout.toMillis()}ms",
                timeout,
            )
        } catch (completion: CompletionException) {
            throw completion.unwrapCompletionException().toStorageException()
        } catch (throwable: Throwable) {
            throw throwable.toStorageException()
        }
    }

    private fun Throwable.toStorageException(): StorageException {
        val root = unwrapCompletionException()
        return when (root) {
            is StorageException -> root
            else -> StorageBackendException("Storage operation failed", root)
        }
    }

    private fun <T> runBackend(block: () -> T): T {
        return try {
            block()
        } catch (throwable: Throwable) {
            throw StorageBackendException("Storage backend operation failed", throwable)
        }
    }

    private fun <T> encode(codec: StorageCodec<T>, data: T): ByteArray {
        return try {
            codec.encode(data)
        } catch (throwable: Throwable) {
            throw StorageSerializationException("Failed to encode value for storage", throwable)
        }
    }

    private fun <T> decode(codec: StorageCodec<T>, payload: ByteArray): T {
        return try {
            codec.decode(payload)
        } catch (throwable: Throwable) {
            throw StorageSerializationException("Failed to decode value from storage", throwable)
        }
    }

    private fun elapsedMs(startedAt: Long): Long {
        return ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0)
    }

    private class NamedThreadFactory(
        private val prefix: String,
    ) : ThreadFactory {
        private val seq = AtomicInteger(0)

        override fun newThread(runnable: Runnable): Thread {
            return Thread(runnable, "$prefix-${seq.incrementAndGet()}").apply {
                isDaemon = true
            }
        }
    }
}
