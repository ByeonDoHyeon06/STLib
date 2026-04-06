package studio.singlethread.lib.framework.bukkit.bootstrap.step

import org.bukkit.plugin.java.JavaPlugin
import studio.singlethread.lib.dependency.bukkit.loader.BukkitLibbyDependencyLoader
import studio.singlethread.lib.dependency.common.model.DependencyLoadResult
import studio.singlethread.lib.dependency.common.model.DependencyStatus
import studio.singlethread.lib.dependency.common.model.LibraryDescriptor
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object RuntimeDependencyBootstrap {
    fun load(
        plugin: JavaPlugin,
        dependencyLoader: BukkitLibbyDependencyLoader,
        library: LibraryDescriptor,
        preflightClassNames: List<String> = emptyList(),
        softTimeoutMillis: Long = DEFAULT_SOFT_TIMEOUT_MILLIS,
        warningThresholdMillis: Long = DEFAULT_WARNING_THRESHOLD_MILLIS,
    ): DependencyLoadResult {
        val startNanos = System.nanoTime()
        val presentClassInfo = detectPresentClass(preflightClassNames, plugin.javaClass.classLoader)
        if (presentClassInfo != null) {
            return DependencyLoadResult(
                library = library,
                status = DependencyStatus.PRESENT,
                message = presentClassInfo.describe(),
                detectedClassName = presentClassInfo.className,
                detectedLocation = presentClassInfo.location,
                detectedVersion = presentClassInfo.version,
                elapsedMillis = elapsedMillis(startNanos),
            )
        }

        val timeoutMillis = softTimeoutMillis.coerceAtLeast(1L)
        val raw = executeLoadWithSoftTimeout(plugin, dependencyLoader, library, timeoutMillis)

        val elapsedMillis = elapsedMillis(startNanos)
        val result =
            raw.copy(
                elapsedMillis = elapsedMillis,
                detectedVersion =
                    raw.detectedVersion
                        ?: if (raw.status == DependencyStatus.LOADED) library.version else null,
            )
        logSoftTimeoutExceeded(plugin, library, elapsedMillis, softTimeoutMillis, warningThresholdMillis)
        return result
    }

    private fun executeLoadWithSoftTimeout(
        plugin: JavaPlugin,
        dependencyLoader: BukkitLibbyDependencyLoader,
        library: LibraryDescriptor,
        timeoutMillis: Long,
    ): DependencyLoadResult {
        val executor =
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "stlib-dependency-loader-${library.artifactId}").apply {
                    isDaemon = true
                }
            }
        return try {
            val future =
                executor.submit<DependencyLoadResult> {
                    val thread = Thread.currentThread()
                    val previousClassLoader = thread.contextClassLoader
                    thread.contextClassLoader = plugin.javaClass.classLoader
                    try {
                        dependencyLoader.load(library)
                    } finally {
                        thread.contextClassLoader = previousClassLoader
                    }
                }
            try {
                future.get(timeoutMillis, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                future.cancel(true)
                DependencyLoadResult(
                    library = library,
                    status = DependencyStatus.FAILED,
                    message = "dependency loading timed out after ${timeoutMillis}ms",
                    error = TimeoutException("dependency loading timed out after ${timeoutMillis}ms"),
                )
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                future.cancel(true)
                DependencyLoadResult(
                    library = library,
                    status = DependencyStatus.FAILED,
                    message = "dependency loading interrupted",
                    error = error,
                )
            } catch (error: ExecutionException) {
                val cause = error.cause ?: error
                DependencyLoadResult(
                    library = library,
                    status = DependencyStatus.FAILED,
                    message = cause.message ?: "dependency loading failed",
                    error = cause,
                )
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun logSoftTimeoutExceeded(
        plugin: JavaPlugin,
        library: LibraryDescriptor,
        elapsedMillis: Long,
        softTimeoutMillis: Long,
        warningThresholdMillis: Long,
    ) {
        val threshold = softTimeoutMillis.coerceAtLeast(1L)
        if (elapsedMillis < threshold || elapsedMillis < warningThresholdMillis.coerceAtLeast(1L)) {
            return
        }
        plugin.logger.warning(
            "Dependency load ${library.coordinates()} exceeded soft-timeout ${threshold}ms (elapsed=${elapsedMillis}ms)",
        )
    }

    private fun elapsedMillis(startNanos: Long): Long {
        return ((System.nanoTime() - startNanos) / 1_000_000L).coerceAtLeast(0L)
    }

    private fun detectPresentClass(
        classNames: List<String>,
        classLoader: ClassLoader,
    ): PresentClassInfo? {
        return classNames.asSequence()
            .mapNotNull { className ->
                val loadedClass = runCatching { Class.forName(className, false, classLoader) }.getOrNull()
                    ?: return@mapNotNull null
                PresentClassInfo(
                    className = className,
                    location = loadedClass.protectionDomain?.codeSource?.location?.toExternalForm(),
                    version = detectClassVersion(loadedClass),
                )
            }.firstOrNull()
    }

    private fun detectClassVersion(type: Class<*>): String? {
        val packageVersion =
            type.`package`?.implementationVersion
                ?: type.`package`?.specificationVersion
        if (!packageVersion.isNullOrBlank()) {
            return packageVersion
        }

        val location = type.protectionDomain?.codeSource?.location ?: return null
        val path = runCatching { Path.of(location.toURI()) }.getOrNull() ?: return null
        if (!path.fileName.toString().endsWith(".jar")) {
            return null
        }
        return runCatching {
            JarFile(path.toFile()).use { jar ->
                val attributes = jar.manifest?.mainAttributes
                attributes?.getValue("Implementation-Version")
                    ?.ifBlank { null }
                    ?: attributes?.getValue("Bundle-Version")?.ifBlank { null }
            }
        }.getOrNull()
    }

    private fun LibraryDescriptor.coordinates(): String {
        return "$groupId:$artifactId:$version"
    }

    private data class PresentClassInfo(
        val className: String,
        val location: String?,
        val version: String?,
    ) {
        fun describe(): String {
            val details = mutableListOf<String>()
            details += className
            if (!version.isNullOrBlank()) {
                details += "version=$version"
            }
            if (!location.isNullOrBlank()) {
                details += "source=$location"
            }
            return "already available on classpath (${details.joinToString(", ")})"
        }
    }

    private const val DEFAULT_SOFT_TIMEOUT_MILLIS = 12_000L
    private const val DEFAULT_WARNING_THRESHOLD_MILLIS = 1_500L
}
