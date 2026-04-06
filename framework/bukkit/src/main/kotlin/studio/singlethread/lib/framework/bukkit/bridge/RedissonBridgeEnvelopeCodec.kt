package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
import java.nio.charset.StandardCharsets
import java.util.Base64

internal data class PublishEnvelope(
    val sourceNode: String,
    val payload: String,
) {
    fun encode(): String {
        return listOf(sourceNode, encodeBase64(payload)).joinToString("|")
    }

    companion object {
        fun decode(raw: String): PublishEnvelope? {
            val parts = raw.split('|', limit = 2)
            if (parts.size != 2) {
                return null
            }
            return PublishEnvelope(
                sourceNode = parts[0],
                payload = decodeBase64(parts[1]),
            )
        }
    }
}

internal data class RequestEnvelope(
    val requestId: String,
    val sourceNode: String,
    val targetNode: String?,
    val payload: String,
) {
    fun encode(): String {
        return listOf(
            requestId,
            sourceNode,
            targetNode.orEmpty(),
            encodeBase64(payload),
        ).joinToString("|")
    }

    companion object {
        fun decode(raw: String): RequestEnvelope? {
            val parts = raw.split('|', limit = 4)
            if (parts.size != 4) {
                return null
            }
            return RequestEnvelope(
                requestId = parts[0],
                sourceNode = parts[1],
                targetNode = parts[2].ifBlank { null },
                payload = decodeBase64(parts[3]),
            )
        }
    }
}

internal data class ResponseEnvelope(
    val requestId: String,
    val status: BridgeResponseStatus,
    val message: String?,
    val payload: String?,
    val responderNode: String?,
) {
    fun encode(): String {
        return listOf(
            requestId,
            status.name,
            encodeBase64(message.orEmpty()),
            encodeBase64(payload.orEmpty()),
            responderNode.orEmpty(),
        ).joinToString("|")
    }

    companion object {
        fun decode(raw: String): ResponseEnvelope? {
            val parts = raw.split('|', limit = 5)
            if (parts.size != 5) {
                return null
            }
            return ResponseEnvelope(
                requestId = parts[0],
                status =
                    runCatching { BridgeResponseStatus.valueOf(parts[1]) }
                        .getOrElse { return null },
                message = decodeBase64(parts[2]).ifBlank { null },
                payload = decodeBase64(parts[3]).ifBlank { null },
                responderNode = parts[4].ifBlank { null },
            )
        }
    }
}

private fun encodeBase64(value: String): String {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))
}

private fun decodeBase64(value: String): String {
    if (value.isBlank()) {
        return ""
    }
    return String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
