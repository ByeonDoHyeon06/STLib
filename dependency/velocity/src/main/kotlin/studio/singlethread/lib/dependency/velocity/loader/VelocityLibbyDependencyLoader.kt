package studio.singlethread.lib.dependency.velocity.loader

import com.velocitypowered.api.plugin.PluginContainer
import studio.singlethread.lib.dependency.common.loader.AbstractLibbyDependencyLoader

class VelocityLibbyDependencyLoader(
    private val pluginContainer: PluginContainer,
) : AbstractLibbyDependencyLoader() {
    override fun createManager(): Any {
        val managerClass = Class.forName("net.byteflux.libby.VelocityLibraryManager")
        return managerClass.getConstructor(PluginContainer::class.java).newInstance(pluginContainer)
    }
}
