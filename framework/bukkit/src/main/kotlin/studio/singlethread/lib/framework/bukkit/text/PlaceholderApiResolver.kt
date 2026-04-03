package studio.singlethread.lib.framework.bukkit.text

import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import java.lang.reflect.Method
import java.util.logging.Logger

class PlaceholderApiResolver private constructor(
    private val setPlaceholdersMethod: Method,
    private val logger: Logger,
) : PlaceholderResolver {
    override fun resolve(
        sender: CommandSender?,
        message: String,
    ): String {
        val offlinePlayer = sender as? OfflinePlayer
        return runCatching {
            setPlaceholdersMethod.invoke(null, offlinePlayer, message) as? String ?: message
        }.onFailure { error ->
            logger.warning("PlaceholderAPI resolve failed: ${error.message}")
        }.getOrDefault(message)
    }

    companion object {
        fun createOrNull(logger: Logger): PlaceholderApiResolver? {
            return runCatching {
                val clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
                val method = clazz.getMethod("setPlaceholders", OfflinePlayer::class.java, String::class.java)
                PlaceholderApiResolver(method, logger)
            }.onFailure { error ->
                logger.warning("PlaceholderAPI bridge setup failed: ${error.message}")
            }.getOrNull()
        }
    }
}
