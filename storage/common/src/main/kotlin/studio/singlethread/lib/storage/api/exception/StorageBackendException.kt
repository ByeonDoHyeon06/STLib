package studio.singlethread.lib.storage.api.exception

class StorageBackendException(
    message: String,
    cause: Throwable? = null,
) : StorageException(message, cause)
