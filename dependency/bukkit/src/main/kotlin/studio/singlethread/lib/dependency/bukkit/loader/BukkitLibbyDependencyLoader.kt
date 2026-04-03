package studio.singlethread.lib.dependency.bukkit.loader

import org.bukkit.plugin.Plugin
import studio.singlethread.lib.dependency.common.loader.AbstractLibbyDependencyLoader

class BukkitLibbyDependencyLoader(
    private val plugin: Plugin,
) : AbstractLibbyDependencyLoader() {
    override fun createManager(): Any {
        val managerClass = Class.forName("net.byteflux.libby.BukkitLibraryManager")
        return managerClass.getConstructor(Plugin::class.java).newInstance(plugin)
    }
}
