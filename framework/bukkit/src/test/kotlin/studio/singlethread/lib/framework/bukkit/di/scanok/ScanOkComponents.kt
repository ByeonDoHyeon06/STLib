package studio.singlethread.lib.framework.bukkit.di.scanok

import studio.singlethread.lib.framework.api.di.STComponent
import studio.singlethread.lib.framework.api.di.STScope

@STComponent(scope = STScope.SINGLETON)
class ScanSingleton

@STComponent(scope = STScope.PROTOTYPE)
class ScanPrototype(
    @Suppress("unused")
    private val singleton: ScanSingleton,
)
