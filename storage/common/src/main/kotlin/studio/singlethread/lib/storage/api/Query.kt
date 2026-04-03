package studio.singlethread.lib.storage.api

data class Query(
    val collection: String,
    val key: String,
) {
    init {
        require(collection.isNotBlank()) { "collection must not be blank" }
        require(key.isNotBlank()) { "key must not be blank" }
    }
}
