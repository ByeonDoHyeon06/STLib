package studio.singlethread.lib.storage.api

import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.util.concurrent.CompletableFuture

interface Storage : AutoCloseable {
    fun collection(name: String): CollectionStorage

    fun <T> set(query: Query, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult>
    fun <T> get(query: Query, codec: StorageCodec<T>): CompletableFuture<T?>
    fun remove(query: Query): CompletableFuture<Boolean>
    fun exists(query: Query): CompletableFuture<Boolean>

    fun <T> setSync(query: Query, data: T, codec: StorageCodec<T>): WriteResult
    fun <T> getSync(query: Query, codec: StorageCodec<T>): T?
    fun removeSync(query: Query): Boolean
    fun existsSync(query: Query): Boolean

    override fun close()
}
