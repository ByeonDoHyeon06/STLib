package studio.singlethread.lib.platform.folia.support

object FoliaSupport {
    fun isFoliaRuntime(): Boolean {
        return runCatching {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
        }.isSuccess
    }
}
