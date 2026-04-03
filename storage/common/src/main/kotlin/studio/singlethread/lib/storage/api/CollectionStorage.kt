package studio.singlethread.lib.storage.api

import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.util.concurrent.CompletableFuture

interface CollectionStorage {
    val name: String

    fun <T> set(key: String, data: T, codec: StorageCodec<T>): CompletableFuture<WriteResult>
    fun <T> get(key: String, codec: StorageCodec<T>): CompletableFuture<T?>
    fun remove(key: String): CompletableFuture<Boolean>
    fun exists(key: String): CompletableFuture<Boolean>

    fun <T> setSync(key: String, data: T, codec: StorageCodec<T>): WriteResult
    fun <T> getSync(key: String, codec: StorageCodec<T>): T?
    fun removeSync(key: String): Boolean
    fun existsSync(key: String): Boolean
}
