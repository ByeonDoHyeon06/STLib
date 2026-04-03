package studio.singlethread.lib.framework.api.di

@Target(
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class STInject

