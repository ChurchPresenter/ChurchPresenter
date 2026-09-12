package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
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

/** What the generator wrote under `cp` for the player: the text motions it drives, and pinned alignments. */
internal data class BandTemplateMeta(
    val textMotion: BandTextMotion = BandTextMotion.NONE,
    val tickerPxPerSecond: Float = DEFAULT_TICKER_SPEED,
    val textAlign: BandTextAlign? = null,
    val referenceAlign: BandTextAlign? = null,
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

    /** The frame a [clock] lands on. ENTER runs from the start to the hold; EXIT from the hold to the end. */
    fun frameAt(clock: BibleBandClock): Float {
        val p = clock.progress.coerceIn(0f, 1f)
        val hold = segment(SEGMENT_HOLD)
        return when (clock.phase) {
            BibleBandPhase.IDLE -> 0f
            BibleBandPhase.ENTER -> lerp(0f, hold.startFrame, p)
            BibleBandPhase.HOLD -> hold.startFrame
            BibleBandPhase.TEXT_OUT -> segment(SEGMENT_TEXT_OUT).let { lerp(it.startFrame, it.endFrame, p) }
            BibleBandPhase.TEXT_IN -> segment(SEGMENT_TEXT_IN).let { lerp(it.startFrame, it.endFrame, p) }
            BibleBandPhase.EXIT -> lerp(hold.endFrame, totalFrames, p)
        }
    }

    /** The painter's progress, 0..1 over the whole file. */
    fun progressAt(clock: BibleBandClock): Float =
        if (totalFrames <= 0f) 0f else (frameAt(clock) / totalFrames).coerceIn(0f, 1f)

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

/** Reads and parses [path] off the UI thread; null while loading and for a file that is not a template. */
@Composable
internal fun rememberBibleLottieTemplate(path: String): State<BibleLottieTemplate?> =
    produceState<BibleLottieTemplate?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadBibleLottieTemplate(path) }
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

