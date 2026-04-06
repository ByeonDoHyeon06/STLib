package studio.singlethread.lib.framework.bukkit.bridge

import studio.singlethread.lib.framework.api.bridge.BridgeMetricsSnapshot
import studio.singlethread.lib.framework.api.bridge.BridgeResponseStatus
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

    fun published() {
        publishedMessages.incrementAndGet()
    }

    fun requestSubmitted() {
        requestSubmitted.incrementAndGet()
    }

    fun requestRejectedBackpressure() {
        requestRejectedBackpressure.incrementAndGet()
    }

    fun responseMatched() {
        responseMatched.incrementAndGet()
    }

    fun responseLate() {
        responseLate.incrementAndGet()
    }

    fun responseTargetMismatched() {
        responseTargetMismatched.incrementAndGet()
    }

    fun decodeFailure() {
        decodeFailures.incrementAndGet()
    }

    fun requestCompleted(status: BridgeResponseStatus) {
        when (status) {
            BridgeResponseStatus.SUCCESS -> requestSucceeded.incrementAndGet()
            BridgeResponseStatus.TIMEOUT -> requestTimedOut.incrementAndGet()
            BridgeResponseStatus.NO_HANDLER -> requestNoHandler.incrementAndGet()
            BridgeResponseStatus.ERROR -> requestErrored.incrementAndGet()
        }
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
