package org.churchpresenter.lottiegen.band.ui

import androidx.compose.runtime.Composable
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
import org.churchpresenter.lottiegen.band.BandTimeline
import org.churchpresenter.lottiegen.band.BibleLottieGenConfig
import org.churchpresenter.lottiegen.band.SlotBox
import org.churchpresenter.lottiegen.band.TextAnimation
import org.churchpresenter.lottiegen.band.computeSlots
import org.churchpresenter.lottiegen.lottie.TextMeasurer
import kotlin.math.ceil

private const val TICKER_GAP = "    "
private const val BOLD_WEIGHT = 700
private const val REGULAR_WEIGHT = 400

/**
 * The run-time text motions, done to the preview the way the app's player does them to the live
 * verse. The file carries `TYPEWRITER`, `TYPEWRITER_WORDS` and `TICKER` as still text — the
 * Compottie runtime has no text range selectors — and the player reveals or scrolls the string
 * itself, so the preview must too or those three look like nothing at all. Null for the keyframed
 * motions, which the file plays on its own.
 */
@OptIn(ExperimentalCompottieApi::class)
@Composable
internal fun rememberPreviewTextMotion(
    cfg: BibleLottieGenConfig,
    timeline: BandTimeline,
    jsonString: String,
): LottieDynamicProperties? {
    if (!cfg.textAnimation.isRuntimeDriven) return null
    val slots = computeSlots(cfg)
    val docs = readTextDocuments(jsonString)
    val samples = mapOf(
        BandLayerNames.TEXT_1 to cfg.previewText1,
        BandLayerNames.REFERENCE_1 to cfg.previewReference1,
        BandLayerNames.TEXT_2 to cfg.previewText2,
        BandLayerNames.REFERENCE_2 to cfg.previewReference2,
    )
    val ticker = TickerSetup(cfg, timeline, samples, docs)
    return rememberLottieDynamicProperties(cfg, timeline, jsonString) {
        if (cfg.textAnimation == TextAnimation.TICKER) {
            tickerLayer(BandLayerNames.TEXT_1, slots.text1, ticker)
            slots.text2?.let { tickerLayer(BandLayerNames.TEXT_2, it, ticker) }
            // The reference rides at the head of the ticker line; its own slot goes quiet.
            textLayer(BandLayerNames.REFERENCE_1) { text { "" } }
            textLayer(BandLayerNames.REFERENCE_2) { text { "" } }
        } else {
            samples.keys.forEach { name ->
                textLayer(name) { text { original -> revealedText(cfg.textAnimation, timeline, original, frame) } }
            }
        }
    }
}

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

private fun tickerLine(reference: String?, text: String): String =
    if (reference.isNullOrBlank() || text.isBlank()) text else "$reference$TICKER_GAP$text"

/**
 * The part of [text] a typewriter has typed by [frame]: growing through `text_in`, all of it
 * through the hold, shrinking back through `text_out` — what the player shows the live verse.
 */
private fun revealedText(motion: TextAnimation, t: BandTimeline, text: String, frame: Float): String {
    val fraction = when {
        frame < t.textStart -> 0f
        frame < t.holdStart -> (frame - t.textStart) / t.textInFrames
        frame < t.textOutStart -> 1f
        frame < t.bgOutStart -> 1f - (frame - t.textOutStart) / t.textOutFrames
        else -> 0f
    }.coerceIn(0f, 1f)
    return when (motion) {
        TextAnimation.TYPEWRITER -> text.take(ceil(text.length * fraction).toInt())
        else -> {
            val words = text.split(' ')
            words.take(ceil(words.size * fraction).toInt()).joinToString(" ")
        }
    }
}

/** What the file wrote for a text layer that the ticker needs back: the fitted size. */
private class TextDocument(val fontSize: Float)

private fun readTextDocuments(jsonString: String): Map<String, TextDocument> = try {
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
