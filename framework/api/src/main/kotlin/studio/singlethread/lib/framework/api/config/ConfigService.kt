package studio.singlethread.lib.framework.api.config

import java.nio.file.Path

interface ConfigService {
    fun <T : Any> load(path: Path, type: Class<T>): T

    fun <T : Any> save(path: Path, value: T, type: Class<T>)

    fun <T : Any> reload(path: Path, type: Class<T>): T
}
