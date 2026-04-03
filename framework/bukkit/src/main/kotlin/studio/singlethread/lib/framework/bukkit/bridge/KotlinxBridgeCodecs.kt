package studio.singlethread.lib.framework.bukkit.bridge

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import studio.singlethread.lib.framework.api.bridge.BridgeCodec

object KotlinxBridgeCodecs {
    private val defaultJson: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun <T : Any> json(
        serializer: KSerializer<T>,
        json: Json = defaultJson,
    ): BridgeCodec<T> {
        return KotlinxJsonBridgeCodec(
            serializer = serializer,
            json = json,
        )
    }

    fun string(): BridgeCodec<String> = StringBridgeCodec
}

private class KotlinxJsonBridgeCodec<T : Any>(
    private val serializer: KSerializer<T>,
    private val json: Json,
) : BridgeCodec<T> {
    override fun encode(value: T): String {
        return json.encodeToString(serializer, value)
    }

    override fun decode(payload: String): T {
        return json.decodeFromString(serializer, payload)
    }
}
