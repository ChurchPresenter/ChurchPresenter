package org.churchpresenter.lowerthird.render

import java.io.File
import java.security.MessageDigest
import java.util.Locale

private const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

/**
 * How long a scratch file must sit untouched before a sweep takes it.
 *
 * Comfortably longer than any render: the point is to reap what a crash stranded, never to
 * pull the file out from under a render still writing it.
 */
private const val STALE_SCRATCH_MS = 60L * 60 * 1000

/** Where [LottieRenderCache] keeps its entries, what each is named, and how the directory is kept in bounds. */
internal object LottieCacheFiles {

    /**
     * Resolved per call, not latched at object-init.
     *
     * This object is reachable from `CompanionServer`, `LowerThird` and `PresenterManager`, so in a
     * full suite run something touches it long before any one test does. Held as a plain `val`, it
     * would capture whatever `user.home` was at that first touch — the real one — and a test that
     * points `user.home` at a temp dir would still be handed the developer's own cache. Since
     * [evictOldEntries] deletes files, that is not a stale read but real data destroyed, and which
     * test class ran first would decide it. Same reasoning as `RecentLabelColors`.
     */
    val cacheDir: File
        get() = File(System.getProperty("user.home"), ".churchpresenter/lottie_render_cache")

    fun keyFor(lottieJson: String, v: LottieRenderCache.Variant): String {
        val md5 = MessageDigest.getInstance("MD5").digest(lottieJson.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return if (!v.clip) "${md5}_${v.width}x${v.height}_still"
        else "${md5}_${v.width}x${v.height}_${"%.2f".format(Locale.US, v.fps)}x${v.frameCount}_clip"
    }

    // Version in the filename so a format/behavior change invalidates old entries
    // (leftovers age out through eviction)
    fun cacheFile(key: String) = File(cacheDir, "${key}_v${LottieRenderCache.VERSION}.lrcc")

    /**
     * Brings the directory back inside [LottieRenderCache.MAX_ENTRIES] and [MAX_TOTAL_BYTES], oldest first.
     *
     * "Oldest" is by write time, not by use: reads never touch `lastModified`, so a clip played
     * every week can still go before one written after it and never played. That is the trade the
     * cheap policy buys, and it is only ever a re-render.
     *
     * Stranded scratch files go first. `renderToFile` deletes its own in a `finally`, so one is
     * only left when the process died mid-render — and since the sweep below counts `.lrcc` alone,
     * a leftover would otherwise sit outside both caps for ever.
     */
    fun evictOldEntries() {
        sweepStaleScratchFiles()
        val entries = cacheDir.listFiles { f -> f.extension == "lrcc" } ?: return
        val byAge = entries.sortedBy { it.lastModified() }
        var totalBytes = entries.sumOf { it.length() }
        var excessCount = entries.size - LottieRenderCache.MAX_ENTRIES
        for (f in byAge) {
            if (excessCount <= 0 && totalBytes <= MAX_TOTAL_BYTES) break
            totalBytes -= f.length()
            excessCount--
            f.delete()
        }
    }

    private fun sweepStaleScratchFiles() {
        val cutoff = System.currentTimeMillis() - STALE_SCRATCH_MS
        cacheDir.listFiles { f -> f.isFile && f.extension == "tmp" }?.forEach { scratch ->
            if (scratch.lastModified() < cutoff) runCatching { scratch.delete() }
        }
    }
}
