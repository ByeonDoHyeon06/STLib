package org.redisson.api.listener

fun interface MessageListener<T> {
    fun onMessage(channel: CharSequence, message: T)
}
