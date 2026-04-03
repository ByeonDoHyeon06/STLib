package studio.singlethread.lib.dependency.common.model

enum class DependencyStatus {
    LOADED,
    SKIPPED,
    FAILED,
}

data class DependencyLoadResult(
    val library: LibraryDescriptor,
    val status: DependencyStatus,
    val message: String? = null,
    val error: Throwable? = null,
)
