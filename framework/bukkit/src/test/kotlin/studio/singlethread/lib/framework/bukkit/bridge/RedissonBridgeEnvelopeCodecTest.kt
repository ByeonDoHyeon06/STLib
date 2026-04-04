package studio.singlethread.lib.framework.bukkit.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus

class RedissonBridgeEnvelopeCodecTest {
    @Test
    fun `publish envelope decode should keep empty trailing payload`() {
        val envelopeClass = nestedClass("PublishEnvelope")
        val encoded = encode(envelopeClass, "node-a", "")
        val decoded = decode(envelopeClass, encoded)

        assertNotNull(decoded)
        assertEquals("node-a", field(decoded, "sourceNode"))
        assertEquals("", field(decoded, "payload"))
    }

    @Test
    fun `request envelope decode should keep empty target and payload`() {
        val envelopeClass = nestedClass("RequestEnvelope")
        val encoded = encode(envelopeClass, "req-1", "node-a", null, "")
        val decoded = decode(envelopeClass, encoded)

        assertNotNull(decoded)
        assertEquals("req-1", field(decoded, "requestId"))
        assertEquals("node-a", field(decoded, "sourceNode"))
        assertEquals(null, field(decoded, "targetNode"))
        assertEquals("", field(decoded, "payload"))
    }

    @Test
    fun `response envelope decode should keep empty payload and responder`() {
        val envelopeClass = nestedClass("ResponseEnvelope")
        val encoded = encode(envelopeClass, "req-1", BridgeResponseStatus.NO_HANDLER, null, null, null)
        val decoded = decode(envelopeClass, encoded)

        assertNotNull(decoded)
        assertEquals("req-1", field(decoded, "requestId"))
        assertEquals(BridgeResponseStatus.NO_HANDLER, field(decoded, "status"))
        assertEquals(null, field(decoded, "payload"))
        assertEquals(null, field(decoded, "responderNode"))
    }

    private fun nestedClass(simpleName: String): Class<*> {
        return Class.forName("$REDISSON_SERVICE_CLASS\$$simpleName")
    }

    private fun encode(
        envelopeClass: Class<*>,
        vararg args: Any?,
    ): String {
        val constructor =
            envelopeClass.declaredConstructors
                .first { it.parameterCount == args.size }
                .apply { isAccessible = true }
        val instance = constructor.newInstance(*args)
        val encode = envelopeClass.getDeclaredMethod("encode").apply { isAccessible = true }
        return encode.invoke(instance) as String
    }

    private fun decode(
        envelopeClass: Class<*>,
        raw: String,
    ): Any? {
        val companion =
            envelopeClass.getDeclaredField("Companion")
                .apply { isAccessible = true }
                .get(null)
        val decode =
            companion.javaClass.getDeclaredMethod("decode", String::class.java)
                .apply { isAccessible = true }
        return decode.invoke(companion, raw)
    }

    private fun field(
        value: Any?,
        name: String,
    ): Any? {
        if (value == null) {
            return null
        }
        return value.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .get(value)
    }

    companion object {
        private const val REDISSON_SERVICE_CLASS =
            "studio.singlethread.lib.framework.bukkit.bridge.RedissonBridgeService"
    }
}
