package studio.singlethread.lib.storage.api.exception

class StorageTimeoutException(
    message: String,
    cause: Throwable? = null,
) : StorageException(message, cause)
