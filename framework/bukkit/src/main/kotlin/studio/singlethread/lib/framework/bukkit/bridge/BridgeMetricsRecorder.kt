package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import java.util.concurrent.atomic.AtomicLong

internal class BridgeMetricsRecorder(
    private val pendingRequests: () -> Int = { 0 },
) {
    private val publishedMessages = AtomicLong(0L)
    private val requestSubmitted = AtomicLong(0L)
    private val requestSucceeded = AtomicLong(0L)
    private val requestTimedOut = AtomicLong(0L)
    private val requestNoHandler = AtomicLong(0L)
    private val requestErrored = AtomicLong(0L)
    private val requestRejectedBackpressure = AtomicLong(0L)
    private val responseMatched = AtomicLong(0L)
    private val responseLate = AtomicLong(0L)
    private val responseTargetMismatched = AtomicLong(0L)
    private val decodeFailures = AtomicLong(0L)

    fun markPublished() {
        publishedMessages.incrementAndGet()
    }

    fun markRequestSubmitted() {
        requestSubmitted.incrementAndGet()
    }

    fun markRequestSucceeded() {
        requestSucceeded.incrementAndGet()
    }

    fun markRequestTimedOut() {
        requestTimedOut.incrementAndGet()
    }

    fun markRequestNoHandler() {
        requestNoHandler.incrementAndGet()
    }

    fun markRequestErrored() {
        requestErrored.incrementAndGet()
    }

    fun markRequestRejectedBackpressure() {
        requestRejectedBackpressure.incrementAndGet()
    }

    fun markResponseMatched() {
        responseMatched.incrementAndGet()
    }

    fun markResponseLate() {
        responseLate.incrementAndGet()
    }

    fun markResponseTargetMismatched() {
        responseTargetMismatched.incrementAndGet()
    }

    fun markDecodeFailure() {
        decodeFailures.incrementAndGet()
    }

    fun snapshot(pendingRequestsOverride: Int? = null): BridgeMetricsSnapshot {
        return BridgeMetricsSnapshot(
            pendingRequests = (pendingRequestsOverride ?: pendingRequests()).coerceAtLeast(0),
            publishedMessages = publishedMessages.get(),
            requestSubmitted = requestSubmitted.get(),
            requestSucceeded = requestSucceeded.get(),
            requestTimedOut = requestTimedOut.get(),
            requestNoHandler = requestNoHandler.get(),
            requestErrored = requestErrored.get(),
            requestRejectedBackpressure = requestRejectedBackpressure.get(),
            responseMatched = responseMatched.get(),
            responseLate = responseLate.get(),
            responseTargetMismatched = responseTargetMismatched.get(),
            decodeFailures = decodeFailures.get(),
        )
    }
}
