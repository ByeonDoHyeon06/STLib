package studio.singlethread.lib.storage.jdbc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.api.exception.StorageMainThreadSyncException
import studio.singlethread.lib.storage.api.extensions.get
import studio.singlethread.lib.storage.api.extensions.set
import java.nio.file.Path

class JdbcStorageBehaviorTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `crud and namespace isolation should work`() {
        val factory = JdbcStorageFactory(primaryThreadChecker = { false })
        val api = CompositeStorageApi(listOf(factory))

        val storageA =
            api.create(
                StorageConfig(
                    namespace = "plugin-a",
                    databaseConfig = DatabaseConfig.SQLite(tempDir.resolve("shared.db").toString()),
                ),
            )

        val storageB =
            api.create(
                StorageConfig(
                    namespace = "plugin-b",
                    databaseConfig = DatabaseConfig.SQLite(tempDir.resolve("shared.db").toString()),
                ),
            )

        try {
            val query = Query("users", "same-key")
            storageA.set(query, "A").join()
            storageB.set(query, "B").join()

            assertTrue(storageA.exists(query).join())
            assertTrue(storageB.exists(query).join())
            assertEquals("A", storageA.get<String>(query).join())
            assertEquals("B", storageB.get<String>(query).join())

            assertTrue(storageA.remove(query).join())
            assertFalse(storageA.exists(query).join())
            assertTrue(storageB.exists(query).join())
        } finally {
            storageA.close()
            storageB.close()
            api.close()
        }
    }

    @Test
    fun `sync operations should be blocked on main thread checker`() {
        val factory = JdbcStorageFactory(primaryThreadChecker = { true })
        val api = CompositeStorageApi(listOf(factory))

        val storage =
            api.create(
                StorageConfig(
                    namespace = "main-thread",
                    databaseConfig = DatabaseConfig.SQLite(tempDir.resolve("main-thread.db").toString()),
                ),
            )

        try {
            assertThrows<StorageMainThreadSyncException> {
                storage.setSync(Query("users", "u1"), "payload", studio.singlethread.lib.storage.api.codec.StringCodec)
            }
        } finally {
            storage.close()
            api.close()
        }
    }
}
