package org.churchpresenter.liveoutput

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.diagnostics.CrashReporter
import org.churchpresenter.diagnostics.Log
import org.churchpresenter.lowerthird.render.LottieRenderCache
import java.io.File
import java.io.IOException

/** How many output sizes get frames of their own at once; any further size draws the desktop frames. */
internal const val MAX_OUTPUT_VARIANTS = 2

/**
 * The lower third's frames for outputs larger than the desktop variant.
 *
 * The desktop variant is rendered at the Lottie canvas (about 1080p), so a 4K output drawing it
 * resamples every pixel of a full canvas on every frame. Each output instead [hold]s its pixel size;
 * a size that [LottieRenderCache.outputVariant] scales past the desktop variant gets that variant
 * pre-rendered and streamed, and draws its frames one to one. Outputs whose sizes come to the same
 * variant share one stream, at most [MAX_OUTPUT_VARIANTS] streams run, and a stream is closed as
 * soon as no output held at its size remains. Until a size's frames are ready, [frameFor] is null
 * and the output draws what it always has: the desktop frames, or the live painter.
 *
 * Driven from the UI thread, as `LiveLowerThirdState` is; [prepare] is the render cache by default
 * and a test's own files otherwise.
 */
internal class LottieOutputFrames(
    private val scope: CoroutineScope,
    private val currentFrameIndex: () -> Int,
    private val prepare: (String, LottieRenderCache.Variant) -> Deferred<File> = LottieRenderCache::prepare,
) {
    /** One output size's pre-render, stream and published frame. */
    private class Entry(val variant: LottieRenderCache.Variant) {
        val frame = mutableStateOf<LottieFrame?>(null)
        var job: Job? = null

        // Read from the decode worker thread by the publish guard.
        @Volatile
        var stream: LottieFrameStream? = null
    }

    /**
     * One output's claim on its size; [resize] moves it, [close] gives it up. Nested rather than
     * inner: the Compose compiler reads a stability field an inner class lacks.
     */
    class Hold internal constructor(private val owner: LottieOutputFrames) : AutoCloseable {
        private var size: IntSize? = null

        fun resize(width: Int, height: Int) {
            val next = IntSize(width, height).takeIf { width > 0 && height > 0 }
            if (next == size) return
            // The new size is held before the old is let go, so a size both map to keeps its stream.
            next?.let { owner.holds[it] = (owner.holds[it] ?: 0) + 1 }
            size?.let(owner::release)
            size = next
            owner.reconcile()
        }

        override fun close() = resize(0, 0)
    }

    private val holds = HashMap<IntSize, Int>()
    private val entries = mutableStateMapOf<IntSize, Entry>()
    private var json: String? = null
    private var desktop: LottieRenderCache.Variant? = null

    /** Output sizes held right now; read by tests. */
    internal val heldSizes: Set<IntSize> get() = holds.keys

    /** The variants being pre-rendered or streamed right now; read by tests. */
    internal val variants: List<LottieRenderCache.Variant> get() = entries.values.map { it.variant }

    fun hold(): Hold = Hold(this)

    private fun release(size: IntSize) {
        val left = (holds[size] ?: return) - 1
        if (left > 0) holds[size] = left else holds.remove(size)
    }

    /**
     * The content every held size is rendered from, and the desktop variant it scales; null clears
     * every stream, as new content does before its desktop variant is known.
     */
    fun setContent(json: String?, desktop: LottieRenderCache.Variant?) {
        entries.keys.toList().forEach(::closeEntry)
        this.json = json
        this.desktop = desktop
        reconcile()
    }

    /** The frame an output [width]×[height] pixels big draws, or null for the desktop frames. */
    fun frameFor(width: Int, height: Int): LottieFrame? =
        variantFor(IntSize(width, height))?.let { entries[IntSize(it.width, it.height)]?.frame?.value }

    fun requestFrame(index: Int) {
        entries.values.forEach { it.stream?.requestFrame(index) }
    }

    private fun variantFor(size: IntSize): LottieRenderCache.Variant? =
        desktop?.let { LottieRenderCache.outputVariant(it, size.width, size.height) }

    /** Starts a stream for every size now held and closes those no longer held, largest first. */
    private fun reconcile() {
        val content = json
        val needed = if (content == null) emptyMap() else holds.keys
            .mapNotNull(::variantFor)
            .associateBy { IntSize(it.width, it.height) }
            .entries.sortedByDescending { it.key.width.toLong() * it.key.height }
            .take(MAX_OUTPUT_VARIANTS)
            .associate { it.key to it.value }
        (entries.keys - needed.keys).forEach(::closeEntry)
        if (content == null) return
        (needed - entries.keys).forEach { (size, variant) -> open(size, Entry(variant), content) }
    }

    private fun open(size: IntSize, entry: Entry, content: String) {
        entries[size] = entry
        // Asked for here rather than in the job, so renders queue in the order outputs asked.
        val pending = prepare(content, entry.variant)
        entry.job = scope.launch {
            var stream: LottieFrameStream? = null
            var adopted = false
            try {
                val file = pending.await()
                stream = LottieFrameStream(file = file, scope = scope) { frame ->
                    if (entry.stream === stream) entry.frame.value = frame
                }
                if (!openUnlessBlank(stream, file, entry.variant)) return@launch
                withContext(Dispatchers.Main) {
                    if (entries[size] === entry) {
                        entry.stream = stream
                        adopted = true
                    }
                }
                if (adopted) stream.requestFrame(currentFrameIndex())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lottiePreRenderFailed(e)
            } catch (e: IllegalArgumentException) {
                lottiePreRenderFailed(e)
            } catch (e: IllegalStateException) {
                lottiePreRenderFailed(e)
            } finally {
                if (!adopted) stream?.close()
            }
        }
    }

    private fun closeEntry(size: IntSize) {
        val entry = entries.remove(size) ?: return
        entry.job?.cancel()
        // Cleared before the stream closes, so nothing recomposes onto a bitmap being released.
        entry.frame.value = null
        entry.stream?.close()
        entry.stream = null
    }
}

/**
 * Opens [stream] on [file], a pre-render of [variant], and says whether its frames can be drawn.
 *
 * A silently-blank off-screen render is closed, its cache file discarded (so a later play
 * re-renders instead of re-reading the blank entry) and reported; the caller keeps drawing what it
 * drew before -- better than switching to bitmaps of nothing.
 */
internal suspend fun openUnlessBlank(
    stream: LottieFrameStream,
    file: File,
    variant: LottieRenderCache.Variant,
): Boolean {
    if (stream.open()) return true
    stream.close()
    file.delete()
    // The message stays constant so this groups as one issue; everything that varies goes in tags
    // and extras. A blank render is a property of the variant and the file it produced, so both are
    // in it.
    CrashReporter.reportWarning(
        "Lottie pre-render produced blank frames, discarded",
        tags = mapOf(
            "subsystem" to "lower_third",
            "lottie.clip" to variant.clip.toString(),
            "lottie.empty_cache_file" to (file.length() == 0L).toString()
        ),
        extras = mapOf(
            "variant" to "${variant.width}x${variant.height} " +
                "@${variant.fps}fps, ${variant.frameCount} frames",
            "cacheFileBytes" to file.length().toString()
        )
    )
    return false
}

internal fun lottiePreRenderFailed(e: Exception) {
    Log.error("PresenterManager", "Lottie pre-render failed: ${e.message}")
    CrashReporter.reportException(e, "Lottie pre-render")
}
