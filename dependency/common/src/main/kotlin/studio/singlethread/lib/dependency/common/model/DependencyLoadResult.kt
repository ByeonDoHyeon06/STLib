package studio.singlethread.lib.dependency.common.model

enum class DependencyStatus {
    LOADED,
    PRESENT,
    SKIPPED_DISABLED,
    FAILED,
}

data class DependencyLoadResult(
    val library: LibraryDescriptor,
    val status: DependencyStatus,
    val message: String? = null,
    val detectedClassName: String? = null,
    val detectedLocation: String? = null,
    val detectedVersion: String? = null,
    val elapsedMillis: Long? = null,
    val error: Throwable? = null,
)
