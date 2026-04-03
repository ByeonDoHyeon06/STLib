package studio.singlethread.lib.platform.common.bootstrap

import studio.singlethread.lib.framework.api.kernel.STKernel

interface PlatformBootstrap<T> {
    fun bootstrap(platform: T, kernel: STKernel)
}
