package studio.singlethread.lib.storage.jdbc

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.PostgreSQLContainer
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.api.extensions.get
import studio.singlethread.lib.storage.api.extensions.set

class JdbcDatabaseCompatibilityTest {
    @Test
    fun `mysql and postgres flows should work`() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable)

        MySQLContainer("mysql:8.4").use { mysql ->
            mysql.start()
            runRemoteFlow(
                namespace = "mysql",
                databaseConfig =
                    DatabaseConfig.MySql(
                        host = mysql.host,
                        port = mysql.getMappedPort(MySQLContainer.MYSQL_PORT),
                        database = mysql.databaseName,
                        username = mysql.username,
                        password = mysql.password,
                    ),
                expected = "mysql-value",
            )
        }

        PostgreSQLContainer("postgres:16").use { postgres ->
            postgres.start()
            runRemoteFlow(
                namespace = "postgres",
                databaseConfig =
                    DatabaseConfig.PostgreSql(
                        host = postgres.host,
                        port = postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT),
                        database = postgres.databaseName,
                        username = postgres.username,
                        password = postgres.password,
                    ),
                expected = "postgres-value",
            )
        }
    }

    private fun runRemoteFlow(namespace: String, databaseConfig: DatabaseConfig, expected: String) {
        val factory = JdbcStorageFactory(primaryThreadChecker = { false })
        val api = CompositeStorageApi(listOf(factory))
        val storage =
            api.create(
                StorageConfig(
                    namespace = namespace,
                    databaseConfig = databaseConfig,
                ),
            )

        try {
            val query = Query("users", "remote-db")
            storage.set(query, expected).join()
            assertEquals(expected, storage.get<String>(query).join())
        } finally {
            storage.close()
            api.close()
        }
    }
}
