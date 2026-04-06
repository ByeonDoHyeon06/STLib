package studio.singlethread.lib.lifecycle

class STLibLifecycleLogger(
    private val translate: (key: String, placeholders: Map<String, String>) -> String,
    private val logInfo: (String) -> Unit,
) {
    fun initialize() {
        log("stlib.lifecycle.initialize")
    }

    fun loadComplete() {
        log("stlib.lifecycle.load_complete")
    }

    fun enabled() {
        log("stlib.lifecycle.enabled")
    }

    fun disabled() {
        log("stlib.lifecycle.disabled")
    }

    private fun log(key: String) {
        logInfo(translate(key, emptyMap()))
    }
}
