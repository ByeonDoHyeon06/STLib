package studio.singlethread.lib.framework.bukkit.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteAction
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class LazyStorageTest {
    @Test
    fun `delegate should be created lazily and only once`() {
        val createdCount = AtomicInteger(0)
        val delegate = TestStorage()
        val lazyStorage =
            LazyStorage {
                createdCount.incrementAndGet()
                delegate
            }

        assertEquals(0, createdCount.get())
        val collection = lazyStorage.collection("sample")
        assertEquals(1, createdCount.get())
        assertEquals("sample", collection.name)

        lazyStorage.exists(Query("sample", "a")).join()
        lazyStorage.remove(Query("sample", "a")).join()
        assertEquals(1, createdCount.get())
    }

    @Test
    fun `close should not create delegate when never used`() {
        val createdCount = AtomicInteger(0)
        val delegate = TestStorage()
        val lazyStorage =
            LazyStorage {
                createdCount.incrementAndGet()
                delegate
            }

        lazyStorage.close()
        assertEquals(0, createdCount.get())
        assertFalse(delegate.closed.get())
    }

    @Test
    fun `close should close created delegate`() {
        val delegate = TestStorage()
        val lazyStorage = LazyStorage { delegate }

        val firstCollection = lazyStorage.collection("alpha")
        val secondCollection = lazyStorage.collection("alpha")
        assertSame(firstCollection, secondCollection)

        lazyStorage.close()
        assertTrue(delegate.closed.get())
    }
}

private class TestStorage : Storage {
    private val collections = linkedMapOf<String, CollectionStorage>()
    val closed = AtomicBoolean(false)

    override fun collection(name: String): CollectionStorage {
        return collections.getOrPut(name) { TestCollectionStorage(name) }
    }

    override fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return CompletableFuture.completedFuture(
            WriteResult(
                action = WriteAction.INSERT,
                updatedAt = Instant.now(),
                durationMs = 0L,
            ),
        )
    }

    override fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?> {
        return CompletableFuture.completedFuture(null)
    }

    override fun remove(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(true)
    }

    override fun exists(query: Query): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(false)
    }

    override fun <T> setSync(query: Query, data: T, codec: StorageCodec<T>): WriteResult {
        return set(query, data, codec).join()
    }

    override fun <T> getSync(query: Query, codec: StorageCodec<T>): T? {
        return get(query, codec).join()
    }

    override fun removeSync(query: Query): Boolean {
        return remove(query).join()
    }

    override fun existsSync(query: Query): Boolean {
        return exists(query).join()
    }

    override fun close() {
        closed.set(true)
    }
}

private class TestCollectionStorage(
    override val name: String,
) : CollectionStorage {
    override fun <T> set(key: String, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult> {
        return CompletableFuture.completedFuture(
            WriteResult(
                action = WriteAction.INSERT,
                updatedAt = Instant.now(),
                durationMs = 0L,
            ),
        )
    }

    override fun <T> get(key: String, codec: StorageCodec<T>): CompletableFuture<T?> {
        return CompletableFuture.completedFuture(null)
    }

    override fun remove(key: String): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(true)
    }

    override fun exists(key: String): CompletableFuture<Boolean> {
        return CompletableFuture.completedFuture(false)
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
