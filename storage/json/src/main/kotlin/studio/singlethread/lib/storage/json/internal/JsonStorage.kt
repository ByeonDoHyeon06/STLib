package studio.singlethread.lib.storage.json.internal

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteAction
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.api.exception.StorageMainThreadSyncException
import studio.singlethread.lib.storage.api.exception.StorageSerializationException
import studio.singlethread.lib.storage.api.exception.StorageTimeoutException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

internal class JsonStorage(
    private val config: StorageConfig,
    filePath: String,
    private val primaryThreadChecker: () -> Boolean,
) : Storage {
    private val closed = AtomicBoolean(false)
    private val storageFile = Path.of(filePath)
    private val json = Json { prettyPrint = true }
    private val lock = Any()
    private val collectionCache = mutableMapOf<String, CollectionStorage>()

    private val executor: ExecutorService = Executors.newFixedThreadPool(config.executorThreads)
    private var state: FileState = loadState()

    override fun collection(name: String): CollectionStorage {
        require(name.isNotBlank()) { "collection name must not be blank" }
        synchronized(collectionCache) {
            return collectionCache.getOrPut(name) { JsonCollectionStorage(this, name) }
        }
    }

    override fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                val startedAt = System.nanoTime()
                val payload = runCatching { codec.encode(data) }
                    .getOrElse { throw StorageSerializationException("Failed to encode value", it) }
                val encoded = Base64.getEncoder().encodeToString(payload)
                val now = Instant.now().toEpochMilli()

                val action = synchronized(lock) {
                    val collection = state.collections.getOrPut(query.collection) { mutableMapOf() }
                    val previous = collection.put(query.key, FileEntry(encoded, now))
                    persistState()
                    if (previous == null) WriteAction.INSERT else WriteAction.UPDATE
                }

                WriteResult(
                    action = action,
                    updatedAt = Instant.ofEpochMilli(now),
                    durationMs = ((System.nanoTime() - startedAt) / 1_000_000).coerceAtLeast(0),
                )
            },
            executor,
        )
    }

    override fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                val encoded = synchronized(lock) { state.collections[query.collection]?.get(query.key)?.valueBase64 } ?: return@supplyAsync null
                val bytes = Base64.getDecoder().decode(encoded)
                runCatching { codec.decode(bytes) }
                    .getOrElse { throw StorageSerializationException("Failed to decode value", it) }
            },
            executor,
        )
    }

    override fun remove(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                synchronized(lock) {
                    val removed = state.collections[query.collection]?.remove(query.key) != null
                    if (removed) {
                        persistState()
                    }
                    removed
                }
            },
            executor,
        )
    }

    override fun exists(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync(
            {
                ensureOpen()
                synchronized(lock) {
                    state.collections[query.collection]?.containsKey(query.key) == true
                }
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
        check(!closed.get()) { "Storage instance for namespace '${config.namespace}' is already closed" }
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
            throw StorageTimeoutException("Storage operation timed out after ${config.syncTimeout.toMillis()}ms", timeout)
        } catch (completion: CompletionException) {
            throw completion.cause ?: completion
        }
    }

    private fun loadState(): FileState {
        if (!Files.exists(storageFile)) {
            Files.createDirectories(storageFile.parent ?: storageFile.toAbsolutePath().parent)
            persistState(FileState())
            return FileState()
        }

        val content = Files.readString(storageFile)
        if (content.isBlank()) {
            return FileState()
        }

        return runCatching {
            json.decodeFromString<FileState>(content)
        }.getOrElse { error ->
            throw StorageSerializationException(
                "Failed to parse JSON storage file '$storageFile'. " +
                    "Refusing to continue with empty state to avoid silent data loss.",
                error,
            )
        }
    }

    private fun persistState() {
        persistState(state)
    }

    private fun persistState(state: FileState) {
        val serialized = json.encodeToString(state)
        Files.writeString(storageFile, serialized)
    }

    @Serializable
    private data class FileState(
        val collections: MutableMap<String, MutableMap<String, FileEntry>> = mutableMapOf(),
    )

    @Serializable
    private data class FileEntry(
        val valueBase64: String,
        val updatedAtEpochMs: Long,
    )
}
