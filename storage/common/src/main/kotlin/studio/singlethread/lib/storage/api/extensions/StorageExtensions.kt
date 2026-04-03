package studio.singlethread.lib.storage.api.extensions

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.WriteResult
import studio.singlethread.lib.storage.api.codec.DefaultCodecRegistry
import studio.singlethread.lib.storage.api.codec.StorageCodec
import java.util.concurrent.CompletableFuture

inline fun <reified T> Storage.set(
    query: Query,
    data: T,
    codec: StorageCodec<T>? = null,
): CompletableFuture<WriteResult> {
    return set(query, data, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> Storage.get(
    query: Query,
    codec: StorageCodec<T>? = null,
): CompletableFuture<T?> {
    return get(query, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> Storage.setSync(
    query: Query,
    data: T,
    codec: StorageCodec<T>? = null,
): WriteResult {
    return setSync(query, data, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> Storage.getSync(
    query: Query,
    codec: StorageCodec<T>? = null,
): T? {
    return getSync(query, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> CollectionStorage.set(
    key: String,
    data: T,
    codec: StorageCodec<T>? = null,
): CompletableFuture<WriteResult> {
    return set(key, data, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> CollectionStorage.get(
    key: String,
    codec: StorageCodec<T>? = null,
): CompletableFuture<T?> {
    return get(key, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> CollectionStorage.setSync(
    key: String,
    data: T,
    codec: StorageCodec<T>? = null,
): WriteResult {
    return setSync(key, data, DefaultCodecRegistry.instance.resolve(codec))
}

inline fun <reified T> CollectionStorage.getSync(
    key: String,
    codec: StorageCodec<T>? = null,
): T? {
    return getSync(key, DefaultCodecRegistry.instance.resolve(codec))
}
