package studio.singlethread.lib.storage.api.extensions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.function.Consumer
import java.util.function.Function

fun <T> CompletableFuture<T>.then(block: Consumer<in T>): CompletableFuture<T> {
    return thenApply { value ->
        block.accept(value)
        value
    }
}

fun <T, R> CompletableFuture<T>.thenMap(transform: Function<in T, out R>): CompletableFuture<R> {
    return thenApply { value -> transform.apply(value) }
}

fun <T> CompletableFuture<T>.onError(handler: Consumer<in Throwable>): CompletableFuture<T> {
    return whenComplete { _, throwable ->
        if (throwable != null) {
            handler.accept(throwable.unwrapCompletionException())
        }
    }
}

fun Throwable.unwrapCompletionException(): Throwable {
    return when (this) {
        is CompletionException -> this.cause ?: this
        is ExecutionException -> this.cause ?: this
        else -> this
    }
}
