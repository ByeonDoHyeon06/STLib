package studio.singlethread.lib.framework.api.lifecycle

import studio.singlethread.lib.framework.api.kernel.STKernel

interface STLifecycle {
    val kernel: STKernel

    fun initialize() = Unit

    fun load() = Unit

    fun enable() = Unit

    fun disable() = Unit
}
