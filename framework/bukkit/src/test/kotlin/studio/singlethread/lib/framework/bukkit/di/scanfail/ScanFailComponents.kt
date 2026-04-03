package studio.singlethread.lib.framework.bukkit.di.scanfail

import studio.singlethread.lib.framework.api.di.STComponent

private interface MissingDependency

@STComponent
private class BrokenComponent(
    @Suppress("unused")
    private val dependency: MissingDependency,
)
