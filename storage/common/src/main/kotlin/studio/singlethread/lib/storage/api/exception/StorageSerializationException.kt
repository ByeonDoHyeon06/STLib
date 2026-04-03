package studio.singlethread.lib.storage.api.exception

class StorageSerializationException(
    message: String,
    cause: Throwable? = null,
) : StorageException(message, cause)
