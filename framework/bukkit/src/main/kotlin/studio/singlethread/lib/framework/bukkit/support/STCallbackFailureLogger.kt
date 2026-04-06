package studio.singlethread.lib.framework.bukkit.support

import java.util.logging.Level
import java.util.logging.Logger

internal object STCallbackFailureLogger {
    fun log(
        logger: Logger,
        subsystem: String,
        phase: String,
        error: Throwable,
        debugEnabled: () -> Boolean = { false },
    ) {
        val message = error.message?.ifBlank { null } ?: error::class.java.simpleName
        logger.warning("$subsystem callback failure at $phase ($message)")
        if (debugEnabled()) {
            logger.log(Level.WARNING, "$subsystem callback failure stacktrace at $phase", error)
        }
    }
}
