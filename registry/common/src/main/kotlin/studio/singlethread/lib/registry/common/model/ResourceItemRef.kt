package studio.singlethread.lib.registry.common.model

data class ResourceItemRef(
    val provider: String,
    val id: String,
) {
    val namespacedId: String
        get() = "$provider:$id"
}
