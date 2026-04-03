package studio.singlethread.lib

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class ModuleBoundaryTest {
    @Test
    fun `framework api should not depend on platform packages`() {
        val apiRoot = Path.of("framework/api/src/main/kotlin")
        if (!Files.exists(apiRoot)) {
            return
        }

        Files.walk(apiRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .forEach { file ->
                    val source = Files.readString(file)
                    assertFalse(source.contains("org.bukkit"), "framework:api must stay platform-agnostic: $file")
                    assertFalse(source.contains("com.velocitypowered"), "framework:api must stay platform-agnostic: $file")
                    assertFalse(source.contains("net.md_5"), "framework:api must stay platform-agnostic: $file")
                }
        }
    }
}
