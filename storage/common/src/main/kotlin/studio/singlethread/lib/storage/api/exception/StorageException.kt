package studio.singlethread.lib.storage.api.exception

open class StorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
