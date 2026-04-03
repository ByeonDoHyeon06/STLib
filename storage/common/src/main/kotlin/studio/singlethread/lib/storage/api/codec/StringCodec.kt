package studio.singlethread.lib.storage.api.codec

object StringCodec : StorageCodec<String> {
    override fun encode(value: String): ByteArray = value.encodeToByteArray()

    override fun decode(bytes: ByteArray): String = bytes.decodeToString()
}
