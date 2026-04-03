package studio.singlethread.lib.dependency.common.loader

import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor

interface DependencyLoader {
    fun load(library: LibraryDescriptor): DependencyLoadResult

    fun loadAll(libraries: Iterable<LibraryDescriptor>): List<DependencyLoadResult> {
        return libraries.map { load(it) }
    }
}

object NoopDependencyLoader : DependencyLoader {
    override fun load(library: LibraryDescriptor): DependencyLoadResult {
        return DependencyLoadResult(library, DependencyStatus.SKIPPED, "Dependency loading disabled")
    }
}
