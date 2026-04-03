package studio.singlethread.lib.storage.api

import java.time.Instant

enum class WriteAction {
    INSERT,
    UPDATE,
}

data class WriteResult(
    val action: WriteAction,
    val updatedAt: Instant,
    val durationMs: Long,
)
