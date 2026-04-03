package studio.singlethread.lib.framework.bukkit.management

data class STPluginDescriptor(
    val name: String,
    val version: String,
    val mainClass: String,
) {
    init {
        require(name.isNotBlank()) { "name must not be blank" }
        require(version.isNotBlank()) { "version must not be blank" }
        require(mainClass.isNotBlank()) { "mainClass must not be blank" }
    }
}

