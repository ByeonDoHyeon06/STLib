package studio.singlethread.lib.dependency.bungee.loader

import net.md_5.bungee.api.plugin.Plugin
import studio.singlethread.lib.dependency.common.loader.AbstractLibbyDependencyLoader

class BungeeLibbyDependencyLoader(
    private val plugin: Plugin,
) : AbstractLibbyDependencyLoader() {
    override fun createManager(): Any {
        val managerClass = Class.forName("net.byteflux.libby.BungeeLibraryManager")
        return managerClass.getConstructor(Plugin::class.java).newInstance(plugin)
    }
}
