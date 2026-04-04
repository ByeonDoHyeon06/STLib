package studio.singlethread.lib.storage.json

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.singlethread.lib.storage.api.CompositeStorageApi
import studio.singlethread.lib.storage.api.Query
import studio.singlethread.lib.storage.api.config.DatabaseConfig
import studio.singlethread.lib.storage.api.config.StorageConfig
import studio.singlethread.lib.storage.api.exception.StorageSerializationException
import studio.singlethread.lib.storage.api.extensions.get
import studio.singlethread.lib.storage.api.extensions.set
import java.nio.file.Files
import java.nio.file.Path

class JsonStorageTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `json backend should persist and reload`() {
        val file = tempDir.resolve("stlib-storage.json")

        run {
            val api = CompositeStorageApi(listOf(JsonStorageFactory(primaryThreadChecker = { false })))
            val storage =
                api.create(
                    StorageConfig(
                        namespace = "json",
                        databaseConfig = DatabaseConfig.Json(file.toString()),
                    ),
                )

            storage.set(Query("users", "u1"), "value-1").join()
            storage.close()
            api.close()
        }

        run {
            val api = CompositeStorageApi(listOf(JsonStorageFactory(primaryThreadChecker = { false })))
            val storage =
                api.create(
                    StorageConfig(
                        namespace = "json",
                        databaseConfig = DatabaseConfig.Json(file.toString()),
                    ),
                )

            assertEquals("value-1", storage.get<String>(Query("users", "u1")).join())
            storage.remove(Query("users", "u1")).join()
            assertNull(storage.get<String>(Query("users", "u1")).join())

            storage.close()
            api.close()
        }
    }

    @Test
    fun `json backend should fail fast when storage file is corrupted`() {
        val file = tempDir.resolve("stlib-storage-corrupted.json")
        Files.writeString(file, "{ this-is-not-valid-json")

        val api = CompositeStorageApi(listOf(JsonStorageFactory(primaryThreadChecker = { false })))
        assertThrows(StorageSerializationException::class.java) {
            api.create(
                StorageConfig(
                    namespace = "json",
                    databaseConfig = DatabaseConfig.Json(file.toString()),
                ),
            )
        }
        api.close()
    }
}
