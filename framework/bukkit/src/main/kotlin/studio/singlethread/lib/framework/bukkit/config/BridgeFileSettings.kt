package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class BridgeFileSettings {
    @field:Comment("Bridge mode. LOCAL=memory only, REDIS=distributed only, COMPOSITE=local+distributed.")
    var mode: BridgeMode = BridgeMode.LOCAL

    @field:Comment("Default namespace used when channel string has no explicit namespace prefix.")
    var namespace: String = "stlib"

    @field:Comment("Node identifier. Use 'auto' to generate '<plugin>-<port>'.")
    var nodeId: String = "auto"

    @field:Comment("Default timeout for RPC requests in milliseconds.")
    var requestTimeoutMillis: Long = 3_000L

    @field:Comment("Redis backend settings used in REDIS/COMPOSITE mode.")
    var redis: RedisBridgeSettings = RedisBridgeSettings()
}

enum class BridgeMode {
    LOCAL,
    REDIS,
    COMPOSITE,
}

@ConfigSerializable
class RedisBridgeSettings {
    @field:Comment("Redis URI, e.g. redis://127.0.0.1:6379")
    var address: String = "redis://127.0.0.1:6379"

    @field:Comment("Optional Redis username.")
    var username: String = ""

    @field:Comment("Optional Redis password.")
    var password: String = ""

    @field:Comment("Redis database index.")
    var database: Int = 0

    @field:Comment("Connection timeout in milliseconds.")
    var connectTimeoutMillis: Long = 3_000L
}
