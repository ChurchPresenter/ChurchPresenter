package org.churchpresenter.web.offscreen

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

/**
 * What one page has done since it was last [drained][drain]: Chromium's paints and the copy each
 * took, and each output's draws, latency and skipped frames. Written from the paint thread, the
 * level worker and every window's draw; read by [OffscreenReporter] once a second.
 */
internal class OffscreenStats {
    private val paints = AtomicLong()
    private val copyNs = AtomicLong()
    private val copyMaxNs = AtomicLong()

    /** Copy times in [COPY_BIN_NS] bins, the last one taking everything longer, for the session's p95. */
    private val copyBins = AtomicLongArray(COPY_BINS)
    private val draws = ConcurrentHashMap<Any, Surface>()

    private class Surface {
        val draws = AtomicLong()
        val latencyNs = AtomicLong()
        val skipped = AtomicLong()
    }

    /** Chromium handed over a paint of the view, copied in [ns]. */
    fun paint(ns: Long) {
        paints.incrementAndGet()
        copyNs.addAndGet(ns)
        copyMaxNs.accumulateAndGet(ns, ::maxOf)
        copyBins.incrementAndGet((ns / COPY_BIN_NS).toInt().coerceAtMost(COPY_BINS - 1))
    }

    /** Output [surface] drew a picture [latencyNs] after its paint arrived, having skipped [gap] since its last. */
    fun draw(surface: Any, latencyNs: Long, gap: Long) {
        val s = draws.getOrPut(surface, ::Surface)
        s.draws.incrementAndGet()
        s.latencyNs.addAndGet(latencyNs)
        if (gap > 0) s.skipped.addAndGet(gap)
    }

    /** A surface that will draw no more, so it stops counting as the busiest. */
    fun forget(surface: Any) {
        draws.remove(surface)
    }

    /** The counts since the last call, the busiest output's standing for all of them. */
    fun drain(): Sample {
        val count = paints.getAndSet(0)
        val busiest = draws.values.maxByOrNull { it.draws.get() }
        val drawn = busiest?.draws?.getAndSet(0) ?: 0
        val latency = busiest?.latencyNs?.getAndSet(0) ?: 0
        val skipped = busiest?.skipped?.getAndSet(0) ?: 0
        draws.values.forEach { s ->
            s.draws.set(0)
            s.latencyNs.set(0)
            s.skipped.set(0)
        }
        return Sample(
            paints = count,
            copyNs = copyNs.getAndSet(0),
            copyMaxNs = copyMaxNs.getAndSet(0),
            draws = drawn,
            latencyNs = latency,
            skipped = skipped,
        )
    }

    /** The copy time at least 95% of the page's paints so far took no longer than, in ms; 0 before any. */
    fun copyP95Ms(): Double {
        val counts = LongArray(COPY_BINS) { copyBins.get(it) }
        val total = counts.sum()
        if (total == 0L) return 0.0
        var seen = 0L
        counts.forEachIndexed { bin, n ->
            seen += n
            if (seen * PERCENT >= total * P95) return (bin + 1) * COPY_BIN_NS / NS_PER_MS
        }
        return COPY_BINS * COPY_BIN_NS / NS_PER_MS
    }
}

/** One [OffscreenStats.drain]: Chromium's paints, and the busiest output's draws over the same time. */
internal data class Sample(
    val paints: Long,
    val copyNs: Long,
    val copyMaxNs: Long,
    val draws: Long,
    val latencyNs: Long,
    val skipped: Long,
)

internal const val NS_PER_MS = 1_000_000.0
private const val COPY_BIN_NS = 250_000L
private const val COPY_BINS = 200
private const val PERCENT = 100
private const val P95 = 95
