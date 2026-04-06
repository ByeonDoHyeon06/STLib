package studio.singlethread.lib.framework.api.scheduler

import java.time.Duration

val Int.millis: Duration
    get() = Duration.ofMillis(this.toLong())

val Long.millis: Duration
    get() = Duration.ofMillis(this)

val Int.ticks: Duration
    get() = Duration.ofMillis(this.toLong() * 50L)

val Long.ticks: Duration
    get() = Duration.ofMillis(this * 50L)

val Int.seconds: Duration
    get() = Duration.ofSeconds(this.toLong())

val Long.seconds: Duration
    get() = Duration.ofSeconds(this)

val Int.minutes: Duration
    get() = Duration.ofMinutes(this.toLong())

val Long.minutes: Duration
    get() = Duration.ofMinutes(this)

val Int.hours: Duration
    get() = Duration.ofHours(this.toLong())

val Long.hours: Duration
    get() = Duration.ofHours(this)
