package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.diagnostics.CrashReporter
import java.util.concurrent.ConcurrentHashMap

/**
 * A native resource the app holds open on someone else's behalf, and shares between the places that
 * draw it.
 *
 * Every one of these is a refcounted cache entry that owns something outside the JVM — an ffmpeg
 * process, a libvlc decoder, a Chromium process, an NDI receiver, a screen-grab loop. That is the
 * point of the list: **a JVM heap chart cannot see any of them**, so the ordinary memory telemetry
 * that would be reached for first is blind to exactly the leaks this app is prone to.
 */
internal enum class SharedResource {
    CAMERA_CAPTURE,
    VIDEO_DECODE,
    SCREEN_GRAB,
    NDI_RECEIVER,
    BROWSER_SOURCE,
}

/**
 * The high-water mark of each shared native resource, and whether it looks like a leak.
 *
 * **Why a census of counts rather than CPU or memory.** Three leaks were found in this area in one
 * week — a leaked ffmpeg process holding a camera, a leaked libvlc decoder, and a leaked headless
 * browser on every viewport resize. All three leak a *process or a native handle*, so a heap gauge
 * stays flat through all of them, and CPU is no better: a busy canvas legitimately costs a lot, and
 * without a per-machine baseline "expensive" and "three times more expensive than it should be" look
 * identical.
 *
 * What all three have in common is a count that goes up and does not come down. That is cheap to
 * watch, carries nothing about the operator, and is specific to the failure this app actually has.
 *
 * The peak is recorded rather than the live value because the moment of shutdown is not when a leak
 * is visible — a leak is visible in how high the count climbed while the service ran.
 */
internal object ResourceCensus {

    private val peaks = ConcurrentHashMap<SharedResource, Int>()

    /**
     * Notes that [liveCount] entries of [resource] were open at once.
     *
     * Called from each cache's `acquire` after the entry is added, which is the only moment the
     * count can rise.
     */
    fun record(resource: SharedResource, liveCount: Int) {
        peaks.merge(resource, liveCount, ::maxOf)
    }

    internal fun peak(resource: SharedResource): Int = peaks[resource] ?: 0

    internal fun snapshot(): Map<SharedResource, Int> =
        SharedResource.entries.associateWith { peak(it) }

    /** Tests share one JVM, and this is process-global state. */
    internal fun reset() = peaks.clear()

    private val reported = ReportOnce()

    /**
     * Reports the census once, at shutdown, and only when it looks like a leak.
     *
     * Not a metric on every run: a report that arrives from every install is a number nobody reads,
     * and the shape worth knowing is the tail. [looksLikeLeak] carries the judgement.
     */
    fun reportIfLeaky() {
        val counts = snapshot()
        if (!looksLikeLeak(counts)) return
        if (!reported.claim()) return
        CrashReporter.reportWarning(
            "Shared native resources climbed higher than a scene can explain",
            tags = mapOf("subsystem" to "resource_census") +
                counts.mapKeys { (resource, _) -> "census.${resource.name.lowercase()}" }
                    .mapValues { (_, peak) -> peak.toString() },
            extras = mapOf("census" to renderCensus(counts)),
        )
    }
}

/**
 * The most of one shared resource a real setup can be holding at once.
 *
 * Not a tuning knob and not a performance threshold — those are the ones that fire on a merely busy
 * machine and get muted. This is a bound on *distinct native handles*, and the entries are keyed by
 * what they open: one per camera device, one per video file, one per capture region, one per NDI
 * source, one per browser viewport. A schedule reaching eight simultaneous distinct video decodes is
 * not a booth that is working hard; it is a count that stopped coming down.
 *
 * Deliberately generous. A false negative costs one undetected leak, which is where we already were;
 * a false positive costs a report that trains everyone to ignore the next one.
 */
private const val IMPLAUSIBLE_LIVE_COUNT = 8

/** Whether any resource climbed past what a scene can account for. Pure, so it can be tested. */
internal fun looksLikeLeak(peaks: Map<SharedResource, Int>): Boolean =
    peaks.values.any { it >= IMPLAUSIBLE_LIVE_COUNT }

/** The census as a readable block. Counts only — there is nothing here to redact. */
internal fun renderCensus(peaks: Map<SharedResource, Int>): String =
    SharedResource.entries.joinToString("\n") { "${it.name.lowercase()}=${peaks[it] ?: 0}" }
