package studio.singlethread.lib.framework.api.di

interface ComponentContainer {
    fun <T : Any> resolve(type: Class<T>): T

    fun scan(packageRoot: String): ComponentScanSummary
}

data class ComponentScanSummary(
    val packageRoot: String,
    val discovered: Int,
    val validated: Int,
    val singletonComponents: Int,
    val prototypeComponents: Int,
)
