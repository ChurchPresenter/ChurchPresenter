package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import presentation.engine.DeckRasterizer
import presentation.engine.model.Deck
import presentation.engine.model.Direction
import presentation.engine.model.LayerSpec
import presentation.engine.model.LayerState
import presentation.engine.model.SlideTransitionSpec
import presentation.engine.model.TransitionType
import presentation.engine.timeline.TimelineEvaluator
import java.util.concurrent.ConcurrentHashMap

/**
 * One rasterized layer placed in the slide frame: bitmap pixels at [offsetXPx]/[offsetYPx],
 * transformed each frame by [state].
 */
data class PlacedLayer(
    val spec: LayerSpec,
    val bitmap: ImageBitmap,
    val offsetXPx: Int,
    val offsetYPx: Int,
    val state: LayerState
)

/**
 * An in-flight slide transition: the outgoing slide's last layers plus progress. The presenter
 * composites [fromLayers] under/over the incoming layers according to [type]/[direction].
 */
data class TransitionOverlay(
    val type: TransitionType,
    /** Movement direction of the incoming slide (directional types only). */
    val direction: Direction?,
    /** 0..1. */
    val progress: Float,
    val fromLayers: List<PlacedLayer>
)

/** The fully evaluated animated frame all output windows draw. */
data class PresentationFrame(
    val slideIndex: Int,
    /** Full-slide pixel geometry at the player's render scale. */
    val frameWidthPx: Int,
    val frameHeightPx: Int,
    /** Pixels per slide point — converts LayerState point offsets to pixels. */
    val scalePxPerPt: Float,
    val layers: List<PlacedLayer>,
    /** Build progress for the operator UI: completed steps / total steps. */
    val completedSteps: Int,
    val stepCount: Int,
    /** Non-null while the deck-defined transition into this slide is still running. */
    val transition: TransitionOverlay? = null
)

/**
 * Playback runtime for animated decks. Owns the layer bitmaps of the current slide (rasterized
 * once on IO, prefetching the next slide), the [TimelineEvaluator], and the click-step state
 * machine. [frame] is sampled by PresenterManager's `withFrameNanos` clock and published to
 * every output window — one evaluation for all of them (LottieFrameStream pattern).
 *
 * Not a ViewModel: pure rendering bridge owned by PresenterManager, like LottieFrameStream.
 */
class PresentationPlayer(
    val deck: Deck,
    private val renderWidthPx: Int = DeckRasterizer.DEFAULT_TARGET_WIDTH_PX
) {

    private class SlideLayers(
        val layers: List<RawLayer>,
        val evaluator: TimelineEvaluator?
    )

    private class RawLayer(
        val spec: LayerSpec,
        val bitmap: ImageBitmap,
        val offsetXPx: Int,
        val offsetYPx: Int
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rasterizer = DeckRasterizer(deck, renderWidthPx)
    private val rasterLock = Any()

    private val slideCache = ConcurrentHashMap<Int, SlideLayers>()
    private val loading = ConcurrentHashMap<Int, Job>()

    private val scalePxPerPt: Float = (renderWidthPx / deck.slideWidthPt).toFloat()
    private val frameWidthPx: Int = renderWidthPx
    private val frameHeightPx: Int = (deck.slideHeightPt * scalePxPerPt).toInt().coerceAtLeast(1)

    @Volatile private var slideIndex: Int = -1
    /** -1 = pre-click state (entrance targets hidden); 0..stepCount-1 = that step playing. */
    @Volatile private var stepIndex: Int = -1

    /** The slide playback currently points at — identity check for step navigation. */
    val currentSlideIndex: Int get() = slideIndex
    @Volatile private var stepStartNanos: Long = 0L
    @Volatile private var closed = false

    // Deck-defined transition into the current slide. Starts on the first evaluated frame after
    // the incoming slide's layers are ready (so the outgoing image holds during rasterization).
    @Volatile private var transitionSpec: SlideTransitionSpec? = null
    @Volatile private var transitionFromLayers: List<PlacedLayer> = emptyList()
    @Volatile private var transitionStartNanos: Long = 0L
    /** The last frame's placed layers — snapshot source for the next transition's "from" side. */
    @Volatile private var lastPlacedLayers: List<PlacedLayer> = emptyList()

    fun showSlide(index: Int) {
        if (index !in deck.slides.indices) return
        val outgoing = lastPlacedLayers
        val spec = deck.slides[index].transition
        transitionSpec = if (index != slideIndex && outgoing.isNotEmpty() && spec != null &&
            spec.type != TransitionType.NONE && spec.durationMs > 0
        ) spec else null
        transitionFromLayers = if (transitionSpec != null) outgoing else emptyList()
        transitionStartNanos = 0L
        slideIndex = index
        stepIndex = -1
        stepStartNanos = 0L
        ensureLoaded(index)
        ensureLoaded(index + 1)
        evictBeyondWindow(index)
    }

    /**
     * Advances one build step. Returns false when the current slide has no step left —
     * the caller then moves to the next slide.
     */
    fun advance(nowNanos: Long): Boolean {
        val evaluator = slideCache[slideIndex]?.evaluator ?: return false
        if (stepIndex + 1 >= evaluator.stepCount) return false
        stepIndex++
        stepStartNanos = nowNanos
        return true
    }

    /**
     * Steps one build back (instantly settled). Returns false when already at the pre-click
     * state — the caller then moves to the previous slide.
     */
    fun rewind(): Boolean {
        if (stepIndex < 0) return false
        stepIndex--
        stepStartNanos = 0L
        return true
    }

    /** True while the current step or a slide transition is still animating (clock keeps ticking). */
    fun isAnimating(nowNanos: Long): Boolean {
        if (transitionSpec != null) return true
        val slide = slideCache[slideIndex] ?: return false
        val evaluator = slide.evaluator ?: return false
        if (stepIndex < 0) return false
        val elapsed = (nowNanos - stepStartNanos) / 1_000_000
        val status = evaluator.evaluate(stepIndex, elapsed).status
        return !status.settled || status.indefiniteActive
    }

    /**
     * Samples the animated frame at [nowNanos]. Null while the slide's layers are still
     * rasterizing or the slide has no timeline/transition — callers fall back to the static
     * bitmap path.
     */
    fun frame(nowNanos: Long): PresentationFrame? {
        val index = slideIndex
        val slide = slideCache[index] ?: return null
        val evaluator = slide.evaluator
        val states: Map<String, LayerState> = when {
            evaluator == null -> emptyMap()
            stepIndex < 0 -> evaluator.initialFrame().layerStates
            else -> {
                val elapsed = ((nowNanos - stepStartNanos) / 1_000_000).coerceAtLeast(0)
                evaluator.evaluate(stepIndex.coerceAtMost(evaluator.stepCount - 1), elapsed).layerStates
            }
        }
        val placed = slide.layers.mapNotNull { raw ->
            val state = states[raw.spec.id]
                ?: if (raw.spec.initiallyVisible) LayerState.VISIBLE else LayerState.HIDDEN
            if (!state.visible) return@mapNotNull null
            PlacedLayer(raw.spec, raw.bitmap, raw.offsetXPx, raw.offsetYPx, state)
        }
        lastPlacedLayers = placed
        return PresentationFrame(
            slideIndex = index,
            frameWidthPx = frameWidthPx,
            frameHeightPx = frameHeightPx,
            scalePxPerPt = scalePxPerPt,
            layers = placed,
            completedSteps = (stepIndex + 1).coerceAtMost(evaluator?.stepCount ?: 0),
            stepCount = evaluator?.stepCount ?: 0,
            transition = transitionOverlay(nowNanos)
        )
    }

    private fun transitionOverlay(nowNanos: Long): TransitionOverlay? {
        val spec = transitionSpec ?: return null
        if (transitionStartNanos == 0L) transitionStartNanos = nowNanos
        val progress = ((nowNanos - transitionStartNanos) / 1_000_000f) / spec.durationMs
        if (progress >= 1f) {
            transitionSpec = null
            transitionFromLayers = emptyList()
            return null
        }
        return TransitionOverlay(
            type = spec.type,
            direction = spec.direction,
            progress = progress.coerceIn(0f, 1f),
            fromLayers = transitionFromLayers
        )
    }

    fun close() {
        closed = true
        scope.cancel()
        synchronized(rasterLock) {
            try {
                rasterizer.close()
            } catch (_: Exception) {
            }
        }
        slideCache.clear()
    }

    // ── Rasterization ─────────────────────────────────────────────────────────

    private fun ensureLoaded(index: Int) {
        if (closed || index !in deck.slides.indices) return
        if (slideCache.containsKey(index)) return
        loading.computeIfAbsent(index) {
            scope.launch {
                try {
                    val slide = deck.slides[index]
                    val rasterLayers = synchronized(rasterLock) {
                        if (closed) return@launch
                        rasterizer.rasterizeSlideLayers(index)
                    }
                    val raws = rasterLayers.map { layer ->
                        RawLayer(
                            spec = layer.spec,
                            bitmap = layer.image.toComposeImageBitmap(),
                            offsetXPx = layer.offsetXPx,
                            offsetYPx = layer.offsetYPx
                        )
                    }
                    val evaluator = slide.timeline?.let { timeline ->
                        TimelineEvaluator(
                            timeline = timeline,
                            slideWidthPt = deck.slideWidthPt,
                            slideHeightPt = deck.slideHeightPt,
                            layerBounds = slide.layers.associate { it.id to it.boundsPt },
                            initiallyHiddenLayerIds = slide.layers.filter { !it.initiallyVisible }.map { it.id }.toSet()
                        )
                    }
                    slideCache[index] = SlideLayers(raws, evaluator)
                } catch (e: Exception) {
                    CrashReporter.reportException(e, "Rasterizing presentation slide $index for playback")
                } finally {
                    loading.remove(index)
                }
            }
        }
    }

    /** Keep current−1 .. current+1; drop the rest (a few full-res layers each — RAM stays flat). */
    private fun evictBeyondWindow(current: Int) {
        slideCache.keys.filter { it < current - 1 || it > current + 1 }.forEach { slideCache.remove(it) }
    }
}
