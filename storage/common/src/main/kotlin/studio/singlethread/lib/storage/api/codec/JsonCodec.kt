package studio.singlethread.lib.storage.api.codec

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.reflect.typeOf

class JsonCodec<T>(
    private val serializer: KSerializer<T>,
    private val json: Json = JsonCodecs.defaultJson,
) : StorageCodec<T> {
    override fun encode(value: T): ByteArray {
        return json.encodeToString(serializer, value).encodeToByteArray()
    }

    override fun decode(bytes: ByteArray): T {
        return json.decodeFromString(serializer, bytes.decodeToString())
    }
}

object JsonCodecs {
    val defaultJson: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> default(json: Json = defaultJson): StorageCodec<T> {
        val serializer = json.serializersModule.serializer(typeOf<T>()) as KSerializer<T>
        return JsonCodec(serializer, json)
    }
}
