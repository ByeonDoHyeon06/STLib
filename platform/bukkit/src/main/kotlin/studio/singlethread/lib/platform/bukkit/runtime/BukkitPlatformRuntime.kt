package studio.singlethread.lib.platform.bukkit.runtime

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.framework.api.kernel.STKernel
import studio.singlethread.lib.framework.api.kernel.service
import studio.singlethread.lib.platform.bukkit.bootstrap.BukkitPlatformBootstrap
import studio.singlethread.lib.storage.api.StorageApi

object BukkitPlatformRuntime {
    private val bootstrap = BukkitPlatformBootstrap()

    fun bootstrap(plugin: JavaPlugin, kernel: STKernel) {
        bootstrap.bootstrap(plugin, kernel)
    }

    fun shutdown(kernel: STKernel) {
        kernel.service(StorageApi::class)?.close()
    }
}
