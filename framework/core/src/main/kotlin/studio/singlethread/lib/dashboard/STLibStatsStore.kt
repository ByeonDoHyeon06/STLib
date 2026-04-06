package studio.singlethread.lib.dashboard

import studio.singlethread.lib.storage.api.CollectionStorage
import studio.singlethread.lib.storage.api.codec.StorageCodec
import studio.singlethread.lib.storage.api.extensions.unwrapCompletionException
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.Properties
import java.util.concurrent.TimeUnit

class STLibStatsStore(
    private val collectionProvider: () -> CollectionStorage,
    private val logWarning: (String) -> Unit,
    private val operationTimeoutMillis: Long = DEFAULT_OPERATION_TIMEOUT_MILLIS,
) {
    private val collection: CollectionStorage by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        collectionProvider()
    }

    fun load(): Map<String, STLibPersistedPluginStats> {
        val loaded = runCatching {
            collection.get(STATS_KEY, statsCodec)
                .orTimeout(operationTimeoutMillis, TimeUnit.MILLISECONDS)
                .join()
        }.onFailure { error ->
            logWarning("Failed to load STPlugin stats store from storage: ${error.unwrapCompletionException().message}")
        }.getOrNull() ?: emptyMap()
        return normalize(loaded)
    }

    fun save(stats: Map<String, STLibPersistedPluginStats>) {
        val normalized = normalize(stats)
        runCatching {
            collection.set(STATS_KEY, normalized, statsCodec)
                .orTimeout(operationTimeoutMillis, TimeUnit.MILLISECONDS)
                .join()
        }.onFailure { error ->
            logWarning("Failed to save STPlugin stats store to storage: ${error.unwrapCompletionException().message}")
        }
    }

    private fun normalize(
        source: Map<String, STLibPersistedPluginStats>,
    ): Map<String, STLibPersistedPluginStats> {
        val normalized = linkedMapOf<String, STLibPersistedPluginStats>()
        source.forEach { (rawKey, stats) ->
            val fallbackName = stats.name.ifBlank { rawKey }
            val key = keyOf(fallbackName)
            if (key.isBlank()) {
                return@forEach
            }

            normalized[key] =
                STLibPersistedPluginStats(
                    name = fallbackName,
                    totalEnableCount = stats.totalEnableCount.coerceAtLeast(0L),
                    totalDisableCount = stats.totalDisableCount.coerceAtLeast(0L),
                    totalCommandExecuted = stats.totalCommandExecuted.coerceAtLeast(0L),
                )
        }
        return normalized.toSortedMap()
    }

    private val statsCodec: StorageCodec<Map<String, STLibPersistedPluginStats>> =
        object : StorageCodec<Map<String, STLibPersistedPluginStats>> {
            override fun encode(value: Map<String, STLibPersistedPluginStats>): ByteArray {
                val properties = Properties()
                value.toSortedMap().forEach { (rawKey, stats) ->
                    val fallbackName = stats.name.ifBlank { rawKey }
                    val key = keyOf(fallbackName)
                    if (key.isBlank()) {
                        return@forEach
                    }
                    properties["$key.name"] = fallbackName
                    properties["$key.enable"] = stats.totalEnableCount.coerceAtLeast(0L).toString()
                    properties["$key.disable"] = stats.totalDisableCount.coerceAtLeast(0L).toString()
                    properties["$key.command"] = stats.totalCommandExecuted.coerceAtLeast(0L).toString()
                }

                val writer = StringWriter()
                properties.store(writer, null)
                return writer.toString().encodeToByteArray()
            }

            override fun decode(bytes: ByteArray): Map<String, STLibPersistedPluginStats> {
                if (bytes.isEmpty()) {
                    return emptyMap()
                }

                val properties = Properties()
                ByteArrayInputStream(bytes).reader(Charsets.UTF_8).use(properties::load)

                val keys = linkedSetOf<String>()
                properties.stringPropertyNames().forEach { property ->
                    when {
                        property.endsWith(".name") -> keys += property.removeSuffix(".name")
                        property.endsWith(".enable") -> keys += property.removeSuffix(".enable")
                        property.endsWith(".disable") -> keys += property.removeSuffix(".disable")
                        property.endsWith(".command") -> keys += property.removeSuffix(".command")
                    }
                }

                val decoded = linkedMapOf<String, STLibPersistedPluginStats>()
                keys.forEach { rawKey ->
                    val fallbackName = properties.getProperty("$rawKey.name").orEmpty().ifBlank { rawKey }
                    val key = keyOf(fallbackName)
                    if (key.isBlank()) {
                        return@forEach
                    }

                    decoded[key] =
                        STLibPersistedPluginStats(
                            name = fallbackName,
                            totalEnableCount = properties.getProperty("$rawKey.enable").toLongOrZero(),
                            totalDisableCount = properties.getProperty("$rawKey.disable").toLongOrZero(),
                            totalCommandExecuted = properties.getProperty("$rawKey.command").toLongOrZero(),
                        )
                }

                return decoded
            }
        }

    private fun String?.toLongOrZero(): Long {
        return this?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    }

    companion object {
        private const val STATS_KEY = "snapshot:v1"
        private const val DEFAULT_OPERATION_TIMEOUT_MILLIS = 5_000L

        fun keyOf(pluginName: String): String {
            return pluginName.trim().lowercase()
        }
    }
}
