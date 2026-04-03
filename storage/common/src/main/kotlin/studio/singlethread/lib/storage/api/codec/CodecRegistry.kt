package studio.singlethread.lib.storage.api.codec

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType
import kotlin.reflect.typeOf

class CodecRegistry(
    @PublishedApi internal val json: Json = JsonCodecs.defaultJson,
) {
    @PublishedApi
    internal val cache: ConcurrentHashMap<KType, StorageCodec<*>> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> resolve(codec: StorageCodec<T>? = null): StorageCodec<T> {
        codec?.let { return it }

        if (T::class == String::class) {
            return StringCodec as StorageCodec<T>
        }
        if (T::class == ByteArray::class) {
            return ByteArrayCodec as StorageCodec<T>
        }

        val type = typeOf<T>()
        return cache.computeIfAbsent(type) {
            val serializer = json.serializersModule.serializer(type)
            JsonCodec(serializer, json)
        } as StorageCodec<T>
    }
}

object DefaultCodecRegistry {
    val instance: CodecRegistry = CodecRegistry()
}
