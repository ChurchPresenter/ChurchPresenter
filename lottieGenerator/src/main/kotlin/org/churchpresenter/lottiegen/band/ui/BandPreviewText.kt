package org.churchpresenter.lottiegen.band.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextMeasurer as ComposeTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import io.github.alexzhirkevich.compottie.ExperimentalCompottieApi
import io.github.alexzhirkevich.compottie.dynamic.LottieDynamicProperties
import io.github.alexzhirkevich.compottie.dynamic.rememberLottieDynamicProperties
import io.github.alexzhirkevich.compottie.internal.helpers.text.TextJustify
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.lottiegen.band.BandLayerNames
import org.churchpresenter.lottiegen.band.BandTextAlign
import org.churchpresenter.lottiegen.band.BandTimeline
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotBox
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.band.computeSlots
import org.churchpresenter.lottiegen.lottie.LottieTextShaping
import org.churchpresenter.lottiegen.lottie.ShapedLines
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import org.churchpresenter.lottiegen.render.GeneratorLottieFonts
import kotlin.math.ceil

private const val TICKER_GAP = "    "
private const val BOLD_WEIGHT = 700
private const val REGULAR_WEIGHT = 400

/**
 * The run-time text motions, done to the preview the way the app's player does them to the live
 * verse. The file carries `TYPEWRITER`, `TYPEWRITER_WORDS` and `TICKER` as still text — the
 * Compottie runtime has no text range selectors — and the player reveals or scrolls the string
 * itself, so the preview must too or those three look like nothing at all.
 *
 * Text drawn as whole lines ([grouped], see `TextShaping`) is laid out here too, as the app's band
 * lays it out: wrapped and placed by its shaped widths (see [ShapedLines]).
 *
 * Null for letter-by-letter text with a keyframed motion, which the file plays on its own.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
internal fun rememberPreviewTextMotion(
    cfg: BibleLottieGenConfig,
    timeline: BandTimeline,
    jsonString: String,
    grouped: Boolean,
): LottieDynamicProperties? {
    val ticker = cfg.textAnimation == TextAnimation.TICKER
    val placesLines = grouped && !ticker
    if (!cfg.textAnimation.isRuntimeDriven && !placesLines) return null
    val slots = computeSlots(cfg)
    val docs = readTextDocuments(jsonString)
    val previews = listOf(
        cfg.previewText1 to cfg.previewReference1,
        cfg.previewText2 to cfg.previewReference2,
        cfg.previewText3 to cfg.previewReference3,
        cfg.previewText4 to cfg.previewReference4,
    )
    val samples = BandLayerNames.SLOT_LAYER_NAMES.take(slots.count).flatMapIndexed { i, (textLayer, refLayer) ->
        val (text, reference) = previews[i]
        listOf(textLayer to text, refLayer to reference)
    }.toMap()
    val tickerSetup = TickerSetup(cfg, timeline, samples, docs)
    val measurer = rememberPreviewMeasurer()
    val placed = remember(cfg, jsonString, placesLines, measurer) {
        if (placesLines) placeSamples(cfg, samples, docs, measurer) else emptyMap()
    }
    return rememberLottieDynamicProperties(cfg, timeline, jsonString, placed) {
        if (ticker) {
            slots.slots.forEachIndexed { i, _ ->
                val (textLayer, refLayer) = BandLayerNames.SLOT_LAYER_NAMES[i]
                tickerLayer(textLayer, slots.slots[i].text, tickerSetup)
                // The reference rides at the head of the ticker line; its own slot goes quiet.
                textLayer(refLayer) { text { "" } }
            }
        } else {
            samples.keys.forEach { name ->
                val lines = placed[name]
                textLayer(name) {
                    text { original ->
                        val shown = lines?.text ?: original
                        val typed = if (cfg.textAnimation.isRuntimeDriven) {
                            revealedText(cfg.textAnimation, timeline, shown, frame)
                        } else {
                            shown
                        }
                        if (lines == null) typed else ShapedLines.applyPadding(typed, lines.pads)
                    }
                    if (lines != null) {
                        // Drawn from the box's left edge in a box too wide to wrap again; the
                        // padding puts each line where its justification would.
                        textJustify { TextJustify.Left }
                        size { original -> Size(NO_WRAP_WIDTH, original.height) }
                    }
                }
            }
        }
    }
}

/** A sample laid out as whole lines: its wrapped lines and what goes before each. */
private class PlacedSample(val text: String, val pads: List<String>)

/**
 * Each slot's sample wrapped and placed by its shaped widths, at the size the file fitted it to
 * and in the face the preview draws whole lines with.
 */
private fun placeSamples(
    cfg: BibleLottieGenConfig,
    samples: Map<String, String>,
    docs: Map<String, TextDocument>,
    measurer: ComposeTextMeasurer,
): Map<String, PlacedSample> {
    val slots = computeSlots(cfg)
    val boxes = slots.slots.flatMapIndexed { i, slot ->
        val (textLayer, refLayer) = BandLayerNames.SLOT_LAYER_NAMES[i]
        listOf(textLayer to slot.text, refLayer to slot.reference)
    }.toMap()
    val fontStyle = if (cfg.previewItalic) FontStyle.Italic else FontStyle.Normal
    val font = GeneratorLottieFonts.load(cfg.previewFontFamily, cfg.previewBold, fontStyle)
    return samples.mapNotNull { (name, sample) ->
        val doc = docs[name] ?: return@mapNotNull null
        val box = boxes[name] ?: return@mapNotNull null
        val style = TextStyle(
            fontFamily = FontFamily(font),
            fontWeight = if (cfg.previewBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = fontStyle,
            fontSize = doc.fontSize.sp,
        )
        val shaped = ShapedLines.measuredBy(measurer, style)
        val isReference = BandLayerNames.SLOT_LAYER_NAMES.any { it.second == name }
        val align = if (isReference) cfg.referenceAlign else cfg.textAlign
        val alignment = when (align) {
            BandTextAlign.LEFT -> 0f
            BandTextAlign.RIGHT -> 1f
            else -> 0.5f
        }
        val width = box.w.toFloat()
        val lines = if (isReference) listOf(sample) else shaped.wrap(sample, width)
        name to PlacedSample(lines.joinToString("\n"), lines.map { shaped.padding(it, width, alignment) })
    }.toMap()
}

/** A measurer at density 1, so a size in template pixels measures in template pixels — as the band's. */
@Composable
private fun rememberPreviewMeasurer(): ComposeTextMeasurer {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) { ComposeTextMeasurer(resolver, Density(1f), LayoutDirection.Ltr) }
}

/** The box width whole-line text is handed, so the player never wraps a padded line again. */
private const val NO_WRAP_WIDTH = 1_000_000f

/** What every ticker line is built and timed from. */
private class TickerSetup(
    val cfg: BibleLottieGenConfig,
    val timeline: BandTimeline,
    val samples: Map<String, String>,
    val docs: Map<String, TextDocument>,
)

/** The verse layer as a ticker: one unwrapped line, reference first, run in from the right and out at the left. */
@OptIn(ExperimentalCompottieApi::class)
private fun LottieDynamicProperties.tickerLayer(name: String, slot: SlotBox, setup: TickerSetup) {
    val referenceName = if (name == BandLayerNames.TEXT_1) BandLayerNames.REFERENCE_1 else BandLayerNames.REFERENCE_2
    val line = tickerLine(setup.samples[referenceName], setup.samples[name].orEmpty())
    val doc = setup.docs[name] ?: return
    val cfg = setup.cfg
    val weight = if (cfg.previewBold) BOLD_WEIGHT else REGULAR_WEIGHT
    val lineWidth = TextMeasurer.measure(line, cfg.previewFontFamily, doc.fontSize, weight, "none").width
    val travel = lineWidth + slot.w.toFloat()
    textLayer(name) {
        text { line }
        textJustify { TextJustify.Left }
        size { Size(travel, slot.h.toFloat()) }
        position { original ->
            val seconds = (frame - setup.timeline.textStart) / setup.timeline.frameRate
            val offset = (seconds * cfg.tickerPxPerSecond).mod(travel)
            Offset((slot.x + slot.w).toFloat() - offset, original.y)
        }
    }
}

internal fun tickerLine(reference: String?, text: String): String =
    if (reference.isNullOrBlank() || text.isBlank()) text else "$reference$TICKER_GAP$text"

/**
 * The part of [text] a typewriter has typed by [frame]: growing through `text_in`, all of it
 * through the hold, shrinking back through `text_out` — what the player shows the live verse.
 */
internal fun revealedText(motion: TextAnimation, t: BandTimeline, text: String, frame: Float): String {
    val fraction = when {
        frame < t.textStart -> 0f
        frame < t.holdStart -> (frame - t.textStart) / t.textInFrames
        frame < t.textOutStart -> 1f
        frame < t.bgOutStart -> 1f - (frame - t.textOutStart) / t.textOutFrames
        else -> 0f
    }.coerceIn(0f, 1f)
    return when (motion) {
        TextAnimation.TYPEWRITER -> LottieTextShaping.typedText(text, fraction)
        else -> {
            val words = text.split(' ')
            words.take(ceil(words.size * fraction).toInt()).joinToString(" ")
        }
    }
}

/** What the file wrote for a text layer that the ticker needs back: the fitted size. */
internal class TextDocument(val fontSize: Float)

internal fun readTextDocuments(jsonString: String): Map<String, TextDocument> = try {
    val layers = Json.parseToJsonElement(jsonString).jsonObject["layers"]?.jsonArray.orEmpty()
    layers.mapNotNull { it as? JsonObject }.mapNotNull { layer ->
        val name = layer["nm"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val doc = layer["t"]?.jsonObject?.get("d")?.jsonObject?.get("k")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("s")?.jsonObject ?: return@mapNotNull null
        val size = doc["s"]?.jsonPrimitive?.floatOrNull ?: return@mapNotNull null
        name to TextDocument(size)
    }.toMap()
} catch (_: IllegalArgumentException) {
    emptyMap()
}
