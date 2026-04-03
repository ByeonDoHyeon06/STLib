package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
class BridgeFileSettings {
    var mode: BridgeMode = BridgeMode.LOCAL
    var namespace: String = "stlib"
    var nodeId: String = "auto"
    var requestTimeoutMillis: Long = 3_000L
    var redis: RedisBridgeSettings = RedisBridgeSettings()
}

enum class BridgeMode {
    LOCAL,
    REDIS,
    COMPOSITE,
}

@ConfigSerializable
class RedisBridgeSettings {
    var address: String = "redis://127.0.0.1:6379"
    var username: String = ""
    var password: String = ""
    var database: Int = 0
    var connectTimeoutMillis: Long = 3_000L
}
