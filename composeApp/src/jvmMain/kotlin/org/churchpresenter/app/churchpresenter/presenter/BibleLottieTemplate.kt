package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.IOException

/** A named span of a template's timeline, in frames. */
internal data class LottieSegment(val startFrame: Float, val durationFrames: Float) {
    val endFrame: Float get() = startFrame + durationFrames
}

/** A text slot's wrap box, in the template's own pixels. */
internal data class LottieSlotBox(val x: Float, val y: Float, val w: Float, val h: Float)

/** How the generator asked the player to move a slot's text, if it asked at all. */
internal enum class BandTextMotion { NONE, TYPEWRITER, TYPEWRITER_WORDS, TICKER }

/** A justification the template pins; null means the Bible settings decide. */
internal enum class BandTextAlign { LEFT, CENTER, RIGHT }

/** A template's frame timing and canvas, in its own pixels. */
internal data class LottieTemplateSize(
    val frameRate: Float,
    val width: Float,
    val height: Float,
    val totalFrames: Float,
) {
    /** A file with no frames or no canvas cannot be played, however well-formed. */
    val isPlayable: Boolean get() = frameRate > 0f && totalFrames > 0f && width > 0f && height > 0f
}

/**
 * What the generator wrote under `cp` for the player: the text motions it drives, pinned
 * alignments, and how long a verse takes to give way to the next — null in files from before
 * that was a setting, which are swapped in the time their text segments take.
 */
internal data class BandTemplateMeta(
    val textMotion: BandTextMotion = BandTextMotion.NONE,
    val tickerPxPerSecond: Float = DEFAULT_TICKER_SPEED,
    val textAlign: BandTextAlign? = null,
    val referenceAlign: BandTextAlign? = null,
    val swapMs: Long? = null,
)

/**
 * A Bible band template as the player reads it: the JSON, the segments the generator marked, the
 * text slots and their boxes, and the metadata under `cp`. Anything the file lacks gets a
 * default — a template with no markers is played in thirds, a slot with no box is left where
 * the file put it — so a hand-made Lottie still works, just with less finesse.
 */
internal class BibleLottieTemplate(
    val json: String,
    val size: LottieTemplateSize,
    val segments: Map<String, LottieSegment>,
    val slots: Map<String, LottieSlotBox>,
    val layerNames: Set<String>,
    val meta: BandTemplateMeta = BandTemplateMeta(),
) {
    val frameRate: Float get() = size.frameRate
    val width: Float get() = size.width
    val height: Float get() = size.height
    val totalFrames: Float get() = size.totalFrames

    fun segment(name: String): LottieSegment = segments.getValue(name)

    /**
     * The frame a [clock] lands on. ENTER runs from the start to the hold; EXIT from the hold to
     * the end; TEXT_SWAP is the incoming text's `text_in` — the outgoing text is [outgoingFrameAt].
     */
    fun frameAt(clock: BibleBandClock): Float {
        val p = clock.progress.coerceIn(0f, 1f)
        val hold = segment(SEGMENT_HOLD)
        return when (clock.phase) {
            BibleBandPhase.IDLE -> 0f
            BibleBandPhase.ENTER -> lerp(0f, hold.startFrame, p)
            BibleBandPhase.HOLD -> hold.startFrame
            BibleBandPhase.TEXT_SWAP -> segment(SEGMENT_TEXT_IN).let { lerp(it.startFrame, it.endFrame, p) }
            BibleBandPhase.EXIT -> lerp(hold.endFrame, totalFrames, p)
        }
    }

    /** The frame the text a swap is replacing lands on: `text_out`, played alongside the new text's `text_in`. */
    fun outgoingFrameAt(clock: BibleBandClock): Float =
        segment(SEGMENT_TEXT_OUT).let { lerp(it.startFrame, it.endFrame, clock.progress.coerceIn(0f, 1f)) }

    /** The painter's progress, 0..1 over the whole file. */
    fun progressAt(clock: BibleBandClock): Float = progressOf(frameAt(clock))

    /** [progressAt] for the text a swap is replacing. */
    fun outgoingProgressAt(clock: BibleBandClock): Float = progressOf(outgoingFrameAt(clock))

    private fun progressOf(frame: Float): Float =
        if (totalFrames <= 0f) 0f else (frame / totalFrames).coerceIn(0f, 1f)

    /**
     * How long a crossfade takes: what the file says, or else the longer of `text_out` and
     * `text_in`, so neither is cut short. The two segments are stretched or squeezed to fit.
     */
    fun swapMs(): Long =
        meta.swapMs?.coerceAtLeast(1L) ?: maxOf(segmentMs(SEGMENT_TEXT_OUT), segmentMs(SEGMENT_TEXT_IN))

    /** How long the named segments take to play back to back. */
    fun segmentMs(vararg names: String): Long {
        val frames = names.sumOf { segment(it).durationFrames.toDouble() }
        return ((frames / frameRate) * MILLIS_PER_SECOND).toLong().coerceAtLeast(1L)
    }

    /** Where a frame falls inside a segment, 0..1, for the text motions the player drives. */
    fun progressWithin(name: String, frame: Float): Float {
        val s = segment(name)
        if (s.durationFrames <= 0f) return 1f
        return ((frame - s.startFrame) / s.durationFrames).coerceIn(0f, 1f)
    }

    fun hasLayer(name: String): Boolean = name in layerNames

    companion object {
        const val SEGMENT_BG_IN = "bg_in"
        const val SEGMENT_TEXT_IN = "text_in"
        const val SEGMENT_HOLD = "hold"
        const val SEGMENT_TEXT_OUT = "text_out"
        const val SEGMENT_BG_OUT = "bg_out"

        const val LAYER_TEXT_1 = "Text1"
        const val LAYER_TEXT_2 = "Text2"
        const val LAYER_REFERENCE_1 = "Reference1"
        const val LAYER_REFERENCE_2 = "Reference2"
        const val SHADOW_SUFFIX = "Shadow"
        const val BAND_PREFIX = "Band"

        val TEXT_LAYERS = listOf(LAYER_TEXT_1, LAYER_REFERENCE_1, LAYER_TEXT_2, LAYER_REFERENCE_2)
    }
}

private const val MILLIS_PER_SECOND = 1000.0
private const val METADATA_KEY = "cp"
private const val DEFAULT_TICKER_SPEED = 120f

/**
 * FillBounds is right whenever a band's own box is about as wide, relative to its height, as the
 * template itself -- true of every ordinary landscape output, since the template is authored to
 * match one. On a canvas narrow enough that the box's aspect falls below the template's own -- a
 * portrait output, where the box is pinned to the canvas width but the template still expects a
 * landscape-wide one -- FillBounds's independent width/height scale factors squish the template's
 * background art. Worse, a slot's text is measured and positioned in the template's own pixel
 * space before this scale is ever applied, so the same non-uniform stretch warps every glyph
 * along with it. Fit instead scales both dimensions by the same factor -- here, the narrower one,
 * driven by the box's width, which is what stays pinned to the canvas edge-to-edge -- so nothing
 * warps, at the cost of the band rendering shorter than its configured height asks for.
 */
internal fun bandContentScale(boxAspectRatio: Float, templateAspectRatio: Float): ContentScale =
    if (boxAspectRatio < templateAspectRatio) ContentScale.Fit else ContentScale.FillBounds

/**
 * How tall the band actually renders, as a fraction of the *whole canvas* height — not the
 * `bandFraction` a Lottie band was asked for, which [bandContentScale] above only honours when
 * the band's own box is at least as wide as the template. [AboveBandFill] draws its wash only
 * down to `bandFraction` when it is not painted behind the band, on the assumption that is
 * exactly where the band begins; once a narrow canvas makes [bandContentScale] choose `Fit`, the
 * band renders shorter than that and the wash stops short of it, leaving a gap between the wash's
 * own edge and the band the wash was meant to meet. Recomputes the same `boxAspectRatio` used
 * above from [canvasAspectRatio] and [bandFraction] rather than taking it as a parameter, since
 * this is called from the presenter's own canvas-level box, not the band's.
 */
internal fun effectiveBandFraction(
    canvasAspectRatio: Float,
    bandFraction: Float,
    templateAspectRatio: Float,
): Float {
    val boxAspectRatio = canvasAspectRatio / bandFraction
    return if (boxAspectRatio < templateAspectRatio) canvasAspectRatio / templateAspectRatio else bandFraction
}

/**
 * A file without the generator's markers still gets the five segments: the first and last
 * fifth are the band, the next fifth in from each end is the text, and the middle is the hold.
 */
private const val FALLBACK_SEGMENT_FRACTION = 0.2f

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Reads a template out of its JSON; null when it is not a Lottie at all. */
internal fun parseBibleLottieTemplate(json: String): BibleLottieTemplate? = try {
    val obj = Json.parseToJsonElement(json).jsonObject
    val fr = obj["fr"]?.jsonPrimitive?.floatOrNull ?: return null
    val ip = obj["ip"]?.jsonPrimitive?.floatOrNull ?: 0f
    val op = obj["op"]?.jsonPrimitive?.floatOrNull ?: return null
    val w = obj["w"]?.jsonPrimitive?.floatOrNull ?: return null
    val h = obj["h"]?.jsonPrimitive?.floatOrNull ?: return null
    val size = LottieTemplateSize(frameRate = fr, width = w, height = h, totalFrames = op - ip)
    if (!size.isPlayable) {
        null
    } else {
        val layers = obj["layers"]?.jsonArray.orEmpty().mapNotNull { it as? JsonObject }
        val meta = obj[METADATA_KEY] as? JsonObject
        BibleLottieTemplate(
            json = json,
            size = size,
            segments = readSegments(obj["markers"] as? JsonArray, size.totalFrames),
            slots = readSlots(meta, layers),
            layerNames = layers.mapNotNull { it["nm"]?.jsonPrimitive?.contentOrNull }.toSet(),
            meta = BandTemplateMeta(
                textMotion = readTextMotion(meta),
                tickerPxPerSecond = meta?.get("tickerPxPerSecond")?.jsonPrimitive?.floatOrNull ?: DEFAULT_TICKER_SPEED,
                textAlign = readAlign(meta, "textAlign"),
                referenceAlign = readAlign(meta, "referenceAlign"),
                swapMs = meta?.get("swapMs")?.jsonPrimitive?.longOrNull,
            ),
        )
    }
} catch (_: IllegalArgumentException) {
    null
} catch (_: kotlinx.serialization.SerializationException) {
    null
}

private fun readSegments(markers: JsonArray?, totalFrames: Float): Map<String, LottieSegment> {
    val named = markers.orEmpty().mapNotNull { it as? JsonObject }.mapNotNull { m ->
        val name = m["cm"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val tm = m["tm"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
        val dr = m["dr"]?.jsonPrimitive?.floatOrNull ?: 0f
        name to LottieSegment(tm, dr)
    }.toMap()
    val required = listOf(
        BibleLottieTemplate.SEGMENT_BG_IN, BibleLottieTemplate.SEGMENT_TEXT_IN, BibleLottieTemplate.SEGMENT_HOLD,
        BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT,
    )
    if (required.all { it in named }) return named
    val fifth = totalFrames * FALLBACK_SEGMENT_FRACTION
    val order = listOf(
        BibleLottieTemplate.SEGMENT_BG_IN, BibleLottieTemplate.SEGMENT_TEXT_IN, BibleLottieTemplate.SEGMENT_HOLD,
        BibleLottieTemplate.SEGMENT_TEXT_OUT, BibleLottieTemplate.SEGMENT_BG_OUT,
    )
    return order.mapIndexed { i, name -> name to LottieSegment(fifth * i, fifth) }.toMap()
}

private fun readAlign(meta: JsonObject?, key: String): BandTextAlign? =
    when (meta?.get(key)?.jsonPrimitive?.contentOrNull) {
        "LEFT" -> BandTextAlign.LEFT
        "CENTER" -> BandTextAlign.CENTER
        "RIGHT" -> BandTextAlign.RIGHT
        else -> null
    }

private fun readTextMotion(meta: JsonObject?): BandTextMotion =
    when (meta?.get("textAnimation")?.jsonPrimitive?.contentOrNull) {
        "TYPEWRITER" -> BandTextMotion.TYPEWRITER
        "TYPEWRITER_WORDS" -> BandTextMotion.TYPEWRITER_WORDS
        "TICKER" -> BandTextMotion.TICKER
        else -> BandTextMotion.NONE
    }

/**
 * Bumped when a template has been written, so everything showing one reads it again.
 *
 * The band generator saves over the same path it loaded from, so the path alone cannot tell a new
 * template from the old one: without this, editing a band and saving it did nothing until the app
 * was restarted, and the generator's own controls looked broken.
 */
private val templateGeneration = mutableStateOf(0)

/** Re-reads every band template from disk. Call after one has been written. */
internal fun invalidateBibleLottieTemplates() {
    templateGeneration.value++
}

/** Reads and parses [path] off the UI thread; null while loading and for a file that is not a template. */
@Composable
internal fun rememberBibleLottieTemplate(path: String): State<BibleLottieTemplate?> {
    val generation = templateGeneration.value
    // Held across a reload rather than reset to null the way produceState would: a null template
    // drops the presenter through to the classic band, so re-reading the file would flash the whole
    // lower third. Only a change of path starts blank, which is a different band anyway.
    val state = remember(path) { mutableStateOf<BibleLottieTemplate?>(null) }
    LaunchedEffect(path, generation) {
        state.value = withContext(Dispatchers.IO) { loadBibleLottieTemplate(path) }
    }
    return state
}

internal fun loadBibleLottieTemplate(path: String): BibleLottieTemplate? {
    if (path.isBlank()) return null
    val file = File(path)
    if (!file.isFile) return null
    val text = try {
        file.readText()
    } catch (_: IOException) {
        return null
    }
    return parseBibleLottieTemplate(text)
}

