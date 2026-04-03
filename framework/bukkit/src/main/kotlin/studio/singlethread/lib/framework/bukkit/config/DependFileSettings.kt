package studio.singlethread.lib.framework.bukkit.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

@ConfigSerializable
class DependFileSettings {
    var runtime: RuntimeDependencySettings = RuntimeDependencySettings()
    var integrations: IntegrationSettings = IntegrationSettings()
}

@ConfigSerializable
class RuntimeDependencySettings {
    var loadDatabaseDrivers: Boolean = true
    var loadRedisBridge: Boolean = true
}

@ConfigSerializable
class IntegrationSettings {
    var itemsAdder: Boolean = true
    var oraxen: Boolean = true
    var nexo: Boolean = true
    var mmoItems: Boolean = true
    var ecoItems: Boolean = true
    var placeholderApi: Boolean = true
}
