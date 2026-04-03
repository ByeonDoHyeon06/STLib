package studio.singlethread.lib.storage.jdbc.internal.backend.jdbc

import studio.singlethread.lib.storage.api.WriteAction
import studio.singlethread.lib.storage.jdbc.internal.backend.StorageBackend
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

internal class JdbcStorageBackend(
    private val dataSource: DataSource,
    private val dialect: JdbcDialect,
) : StorageBackend {

    override fun initialize() {
        dataSource.connection.use { connection ->
            connection.prepareStatement(dialect.createTableSql).use { stmt ->
                stmt.execute()
            }
        }
    }

    override fun upsert(
        namespace: String,
        collection: String,
        key: String,
        value: ByteArray,
        updatedAtEpochMs: Long,
    ): WriteAction {
        dataSource.connection.use { connection ->
            val updated = updateValue(connection, namespace, collection, key, value, updatedAtEpochMs)
            if (updated > 0) {
                return WriteAction.UPDATE
            }

            return try {
                insertValue(connection, namespace, collection, key, value, updatedAtEpochMs)
                WriteAction.INSERT
            } catch (sqlException: SQLException) {
                if (isConstraintViolation(sqlException)) {
                    val retriedUpdate = updateValue(connection, namespace, collection, key, value, updatedAtEpochMs)
                    if (retriedUpdate > 0) WriteAction.UPDATE else throw sqlException
                } else {
                    throw sqlException
                }
            }
        }
    }

    override fun get(namespace: String, collection: String, key: String): ByteArray? {
        val sql =
            """
            SELECT value_blob
            FROM st_storage_entries
            WHERE namespace = ? AND collection = ? AND entry_key = ?
            LIMIT 1
            """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, namespace)
                stmt.setString(2, collection)
                stmt.setString(3, key)

                stmt.executeQuery().use { rs ->
                    return if (rs.next()) rs.getBytes("value_blob") else null
                }
            }
        }
    }

    override fun remove(namespace: String, collection: String, key: String): Boolean {
        val sql =
            """
            DELETE FROM st_storage_entries
            WHERE namespace = ? AND collection = ? AND entry_key = ?
            """.trimIndent()

        dataSource.connection.use { connection ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, namespace)
                stmt.setString(2, collection)
                stmt.setString(3, key)
                return stmt.executeUpdate() > 0
            }
        }
    }

    override fun exists(namespace: String, collection: String, key: String): Boolean {
        dataSource.connection.use { connection ->
            return exists(connection, namespace, collection, key)
        }
    }

    private fun updateValue(
        connection: Connection,
        namespace: String,
        collection: String,
        key: String,
        value: ByteArray,
        updatedAtEpochMs: Long,
    ): Int {
        val sql =
            """
            UPDATE st_storage_entries
            SET value_blob = ?, updated_at = ?
            WHERE namespace = ? AND collection = ? AND entry_key = ?
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setBytes(1, value)
            stmt.setLong(2, updatedAtEpochMs)
            stmt.setString(3, namespace)
            stmt.setString(4, collection)
            stmt.setString(5, key)
            return stmt.executeUpdate()
        }
    }

    private fun insertValue(
        connection: Connection,
        namespace: String,
        collection: String,
        key: String,
        value: ByteArray,
        updatedAtEpochMs: Long,
    ) {
        val sql =
            """
            INSERT INTO st_storage_entries(namespace, collection, entry_key, value_blob, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, namespace)
            stmt.setString(2, collection)
            stmt.setString(3, key)
            stmt.setBytes(4, value)
            stmt.setLong(5, updatedAtEpochMs)
            stmt.executeUpdate()
        }
    }

    private fun exists(connection: Connection, namespace: String, collection: String, key: String): Boolean {
        val sql =
            """
            SELECT 1
            FROM st_storage_entries
            WHERE namespace = ? AND collection = ? AND entry_key = ?
            LIMIT 1
            """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, namespace)
            stmt.setString(2, collection)
            stmt.setString(3, key)
            stmt.executeQuery().use { rs ->
                return rs.next()
            }
        }
    }

    private fun isConstraintViolation(sqlException: SQLException): Boolean {
        var current: SQLException? = sqlException
        while (current != null) {
            val sqlState = current.sqlState
            if (sqlState != null && sqlState.startsWith("23")) {
                return true
            }

            val message = current.message?.lowercase().orEmpty()
            if (
                message.contains("unique constraint") ||
                message.contains("duplicate entry") ||
                message.contains("duplicate key")
            ) {
                return true
            }

            current = current.nextException
        }
        return false
    }

    override fun close() {
        // DataSource lifecycle is owned by DataSourceRegistry.
    }
}
