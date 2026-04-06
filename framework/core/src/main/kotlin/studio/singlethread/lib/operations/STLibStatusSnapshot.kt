package studio.singlethread.lib.operations

data class STLibStatusSnapshot(
    val storageBackend: String,
    val plugins: List<STLibStatusPlugin>,
)

data class STLibStatusPlugin(
    val name: String,
    val version: String,
    val status: String,
)
