package studio.singlethread.lib.storage.api.codec

object ByteArrayCodec : StorageCodec<ByteArray> {
    override fun encode(value: ByteArray): ByteArray = value

    override fun decode(bytes: ByteArray): ByteArray = bytes
}
