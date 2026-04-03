package studio.singlethread.lib.dependency.common.loader

import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.dependency.common.model.RepositoryDescriptor

abstract class AbstractLibbyDependencyLoader : DependencyLoader {
    private val manager: Any by lazy {
        createManager().also(::configureManager)
    }

    protected abstract fun createManager(): Any

    override fun load(library: LibraryDescriptor): DependencyLoadResult {
        return runCatching {
            registerRepositories(library.repositories)
            val managerValue = manager
            val libraryInstance = buildLibrary(library)
            val libraryClass = Class.forName("net.byteflux.libby.Library")

            managerValue.javaClass
                .getMethod("loadLibrary", libraryClass)
                .invoke(managerValue, libraryInstance)

            DependencyLoadResult(library, DependencyStatus.LOADED)
        }.getOrElse { error ->
            DependencyLoadResult(
                library = library,
                status = DependencyStatus.FAILED,
                message = error.message,
                error = error,
            )
        }
    }

    private fun configureManager(manager: Any) {
        runCatching {
            manager.javaClass.getMethod("addMavenCentral").invoke(manager)
        }
    }

    private fun registerRepositories(repositories: List<RepositoryDescriptor>) {
        if (repositories.isEmpty()) {
            return
        }

        val managerValue = manager
        repositories.forEach { repo ->
            runCatching {
                managerValue.javaClass
                    .getMethod("addRepository", String::class.java, String::class.java)
                    .invoke(managerValue, repo.name, repo.url)
            }
        }
    }

    private fun buildLibrary(descriptor: LibraryDescriptor): Any {
        val libraryClass = Class.forName("net.byteflux.libby.Library")
        val builder = libraryClass.getMethod("builder").invoke(null)

        callBuilderString(builder, "groupId", descriptor.groupId)
        callBuilderString(builder, "artifactId", descriptor.artifactId)
        callBuilderString(builder, "version", descriptor.version)

        descriptor.id?.let { callBuilderString(builder, "id", it) }

        if (descriptor.isolated) {
            callBuilderBoolean(builder, "isolatedLoad", true)
        }

        descriptor.relocations.forEach { (from, to) ->
            runCatching {
                builder.javaClass
                    .getMethod("relocate", String::class.java, String::class.java)
                    .invoke(builder, from, to)
            }
        }

        return builder.javaClass.getMethod("build").invoke(builder)
    }

    private fun callBuilderString(builder: Any, method: String, value: String) {
        builder.javaClass.getMethod(method, String::class.java).invoke(builder, value)
    }

    private fun callBuilderBoolean(builder: Any, method: String, value: Boolean) {
        builder.javaClass
            .getMethod(method, Boolean::class.javaPrimitiveType)
            .invoke(builder, value)
    }
}
