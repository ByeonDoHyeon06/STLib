package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Comment

@ConfigSerializable
class DependFileSettings {
    @field:Comment("Runtime dependency loading toggles.")
    var runtime: RuntimeDependencySettings = RuntimeDependencySettings()

    @field:Comment("External integration toggles.")
    var integrations: IntegrationSettings = IntegrationSettings()
}

@ConfigSerializable
class RuntimeDependencySettings {
    @field:Comment("Download/load JDBC drivers at runtime using Libby.")
    var loadDatabaseDrivers: Boolean = true

    @field:Comment("Download/load Redisson bridge dependency at runtime using Libby.")
    var loadRedisBridge: Boolean = true
}

@ConfigSerializable
class IntegrationSettings {
    @field:Comment("Enable ItemsAdder integration.")
    var itemsAdder: Boolean = true

    @field:Comment("Enable Oraxen integration.")
    var oraxen: Boolean = true

    @field:Comment("Enable Nexo integration.")
    var nexo: Boolean = true

    @field:Comment("Enable MMOItems integration.")
    var mmoItems: Boolean = true

    @field:Comment("Enable EcoItems integration.")
    var ecoItems: Boolean = true

    @field:Comment("Enable PlaceholderAPI text parsing integration.")
    var placeholderApi: Boolean = true
}
