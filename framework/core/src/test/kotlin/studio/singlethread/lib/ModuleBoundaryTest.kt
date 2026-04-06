package studio.singlethread.lib

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

class ModuleBoundaryTest {
    @Test
    fun `framework api should not depend on platform packages`() {
        assertNoForbiddenReferences(
            root = Path.of("framework/api/src/main/kotlin"),
            module = "framework:api",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
                "studio.singlethread.lib.framework.bukkit" to "framework:bukkit coupling",
            ),
        )
    }

    @Test
    fun `framework kernel should not depend on platform packages`() {
        assertNoForbiddenReferences(
            root = Path.of("framework/kernel/src/main/kotlin"),
            module = "framework:kernel",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
            ),
        )
    }

    @Test
    fun `framework bukkit should not depend on framework core runtime packages`() {
        assertNoForbiddenReferences(
            root = Path.of("framework/bukkit/src/main/kotlin"),
            module = "framework:bukkit",
            forbidden = listOf(
                "studio.singlethread.lib.command" to "framework:core command package coupling",
                "studio.singlethread.lib.dashboard" to "framework:core dashboard package coupling",
                "studio.singlethread.lib.health" to "framework:core health package coupling",
                "studio.singlethread.lib.ui" to "framework:core ui package coupling",
            ),
        )
    }

    @Test
    fun `platform common should stay platform agnostic`() {
        assertNoForbiddenReferences(
            root = Path.of("platform/common/src/main/kotlin"),
            module = "platform:common",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
                "studio.singlethread.lib.framework.bukkit" to "framework:bukkit coupling",
            ),
        )
    }

    @Test
    fun `dependency common should stay platform agnostic`() {
        assertNoForbiddenReferences(
            root = Path.of("dependency/common/src/main/kotlin"),
            module = "dependency:common",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
                "studio.singlethread.lib.framework.bukkit" to "framework:bukkit coupling",
            ),
        )
    }

    @Test
    fun `storage common should stay platform agnostic`() {
        assertNoForbiddenReferences(
            root = Path.of("storage/common/src/main/kotlin"),
            module = "storage:common",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
                "studio.singlethread.lib.framework.bukkit" to "framework:bukkit coupling",
            ),
        )
    }

    @Test
    fun `configurate module should stay platform agnostic`() {
        assertNoForbiddenReferences(
            root = Path.of("configurate/src/main/kotlin"),
            module = "configurate",
            forbidden = listOf(
                "org.bukkit" to "platform-agnostic",
                "com.velocitypowered" to "platform-agnostic",
                "net.md_5" to "platform-agnostic",
                "studio.singlethread.lib.framework.bukkit" to "framework:bukkit coupling",
            ),
        )
    }

    private fun assertNoForbiddenReferences(
        root: Path,
        module: String,
        forbidden: List<Pair<String, String>>,
    ) {
        if (!Files.exists(root)) {
            return
        }

        Files.walk(root).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.extension == "kt" }
                .forEach { file ->
                    val source = Files.readString(file)
                    forbidden.forEach { (token, reason) ->
                        assertFalse(
                            source.contains(token),
                            "$module must not reference '$token' ($reason): $file",
                        )
                    }
                }
        }
    }
}
