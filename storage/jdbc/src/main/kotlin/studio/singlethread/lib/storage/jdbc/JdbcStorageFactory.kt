package studio.singlethread.lib.storage.jdbc

import studio.singlethread.lib.dependency.common.loader.DependencyLoader
import studio.singlethread.lib.dependency.common.loader.NoopDependencyLoader
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.storage.api.Storage
import studio.singlethread.lib.storage.api.StorageFactory
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.jdbc.internal.StorageImpl
import studio.singlethread.lib.storage.jdbc.internal.backend.StorageBackend
import studio.singlethread.lib.storage.jdbc.internal.backend.jdbc.JdbcDialectResolver
import studio.singlethread.lib.storage.jdbc.internal.backend.jdbc.JdbcStorageBackend
import studio.singlethread.lib.storage.jdbc.internal.datasource.DataSourceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class JdbcStorageFactory(
    private val primaryThreadChecker: () -> Boolean,
    private val dependencyLoader: DependencyLoader = NoopDependencyLoader,
) : StorageFactory {
    private val closed = AtomicBoolean(false)
    private val dataSourceRegistry = DataSourceRegistry()
    private val backends = ConcurrentHashMap<DatabaseConfig, StorageBackend>()
    private val storages = ConcurrentHashMap.newKeySet<StorageImpl>()

    override fun supports(databaseConfig: DatabaseConfig): Boolean {
        return databaseConfig is DatabaseConfig.SQLite ||
            databaseConfig is DatabaseConfig.MySql ||
            databaseConfig is DatabaseConfig.PostgreSql
    }

    override fun create(config: StorageConfig): Storage {
        ensureOpen()

        val databaseConfig = config.databaseConfig
        require(supports(databaseConfig)) {
            "Unsupported database config for JDBC factory: ${databaseConfig::class.qualifiedName}"
        }

        loadRuntimeDriverIfNeeded(databaseConfig)

        val backend = backends.computeIfAbsent(databaseConfig) { dbConfig ->
            val dialect = JdbcDialectResolver.resolve(dbConfig)
            val dataSource = dataSourceRegistry.getOrCreate(dbConfig)
            JdbcStorageBackend(dataSource, dialect).also { it.initialize() }
        }

        lateinit var storage: StorageImpl
        storage =
            StorageImpl(
                config = config,
                backend = backend,
                primaryThreadChecker = primaryThreadChecker,
                onClose = { storages.remove(storage) },
            )

        storages.add(storage)
        return storage
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }

        storages.toList().forEach { runCatching { it.close() } }
        storages.clear()

        backends.values.forEach { runCatching { it.close() } }
        backends.clear()

        dataSourceRegistry.close()
    }

    private fun ensureOpen() {
        check(!closed.get()) { "JdbcStorageFactory is already closed" }
    }

    private fun loadRuntimeDriverIfNeeded(databaseConfig: DatabaseConfig) {
        val library =
            when (databaseConfig) {
                is DatabaseConfig.SQLite -> {
                    LibraryDescriptor(
                        groupId = "org.xerial",
                        artifactId = "sqlite-jdbc",
                        version = "3.46.1.3",
                    )
                }

                is DatabaseConfig.MySql -> {
                    LibraryDescriptor(
                        groupId = "com.mysql",
                        artifactId = "mysql-connector-j",
                        version = "8.4.0",
                    )
                }

                is DatabaseConfig.PostgreSql -> {
                    LibraryDescriptor(
                        groupId = "org.postgresql",
                        artifactId = "postgresql",
                        version = "42.7.3",
                    )
                }

                else -> return
            }

        val result = dependencyLoader.load(library)
        if (result.status == DependencyStatus.FAILED) {
            throw IllegalStateException(
                "Failed to load runtime DB dependency ${library.groupId}:${library.artifactId}:${library.version}. ${result.message}",
                result.error,
            )
        }
    }
}
