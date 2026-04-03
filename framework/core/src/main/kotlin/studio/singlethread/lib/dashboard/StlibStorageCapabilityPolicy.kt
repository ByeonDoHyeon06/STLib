package studio.singlethread.lib.dashboard

class StlibStorageCapabilityPolicy(
    private val isCapabilityEnabled: (String) -> Boolean,
) {
    fun isStorageAvailable(): Boolean {
        return STORAGE_CAPABILITIES.any(isCapabilityEnabled)
    }

    private companion object {
        private val STORAGE_CAPABILITIES =
            listOf(
                "storage:json",
                "storage:jdbc",
                "storage:sqlite",
                "storage:mysql",
                "storage:postgresql",
            )
    }
}
