package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeCodec

object StringBridgeCodec : BridgeCodec<String> {
    override fun encode(value: String): String = value

    override fun decode(payload: String): String = payload
}
