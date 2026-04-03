package studio.singlethread.lib.storage.api.codec

interface StorageCodec<T> {
    fun encode(value: T): ByteArray

    fun decode(bytes: ByteArray): T
}
