package studio.singlethread.lib.dependency.common.model

data class LibraryDescriptor(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val repositories: List<RepositoryDescriptor> = emptyList(),
    val relocations: Map<String, String> = emptyMap(),
    val resolveTransitives: Boolean = true,
    val isolated: Boolean = false,
    val id: String? = null,
)

data class RepositoryDescriptor(
    val name: String,
    val url: String,
)
