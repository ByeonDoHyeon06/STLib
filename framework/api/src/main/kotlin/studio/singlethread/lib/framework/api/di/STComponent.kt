package studio.singlethread.lib.framework.api.di

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class STComponent(
    val scope: STScope = STScope.SINGLETON,
)

