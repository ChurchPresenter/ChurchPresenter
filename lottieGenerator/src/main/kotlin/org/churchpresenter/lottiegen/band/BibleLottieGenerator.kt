package org.churchpresenter.lottiegen.band

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.churchpresenter.lottiegen.lottie.LottieBuilder
import kotlin.math.roundToInt

/**
 * Builds a Bible lower-third template: a band, up to two verse slots with their references, and
 * the markers that tell the player where the band's entrance ends and its exit begins.
 */
object BibleLottieGenerator {
    fun generate(cfg: BibleLottieGenConfig): JsonObject {
        require(cfg.canvasW > 0 && cfg.canvasH > 0) { "Canvas must be at least 1×1" }
        val timeline = BandTimeline.from(cfg)
        val slots = computeSlots(cfg)
        val builder = LottieBuilder(cfg.canvasW, cfg.canvasH, timeline.frameRate)
        builder.setDuration(
            inFrames = timeline.holdStart,
            holdFrames = timeline.holdFrames,
            outFrames = timeline.textOutFrames + timeline.bgOutFrames,
        )
        // The first layer in a Lottie is the topmost, so the text goes in before the band it sits on.
        textSlots(cfg, slots).forEach { builder.addTextSlot(it, cfg, timeline) }
        builder.addBandBackground(cfg, slots, timeline)
        timeline.emitMarkers(builder)
        return withMetadata(builder.toJson(), cfg, slots)
    }

    /**
     * What the player needs that Lottie has no field for, under one key it ignores: which text
     * motions are the player's to drive, and how fast a ticker runs. Players that are not
     * ChurchPresenter skip unknown keys, so the file stays an ordinary Lottie.
     */
    private fun withMetadata(
        lottie: JsonObject,
        cfg: BibleLottieGenConfig,
        slots: BandSlots,
    ): JsonObject = buildJsonObject {
        lottie.forEach { (key, value) -> put(key, value) }
        put(
            METADATA_KEY,
            buildJsonObject {
                put(METADATA_KIND, JsonPrimitive(METADATA_KIND_BAND))
                put(METADATA_VERSION, JsonPrimitive(METADATA_VERSION_1))
                put(METADATA_TEXT_ANIMATION, JsonPrimitive(cfg.textAnimation.name))
                put(METADATA_TICKER_SPEED, JsonPrimitive(cfg.tickerPxPerSecond))
                // The crossfade is the player's, not the file's: nothing in the timeline plays
                // two verses at once, so its length has nowhere to go but here.
                put(METADATA_SWAP_MS, JsonPrimitive((cfg.swapSeconds * MILLIS_PER_SECOND).roundToInt()))
                put(METADATA_TEXT_ALIGN, JsonPrimitive(cfg.textAlign.name))
                put(METADATA_REFERENCE_ALIGN, JsonPrimitive(cfg.referenceAlign.name))
                // The slot boxes themselves. The text documents' `ps` is where the sample's
                // baseline sits, not the box, so a player that laid live text out from it would
                // start an ascent too low and the matte would cut the glyphs' bottoms.
                put(
                    METADATA_SLOTS,
                    buildJsonObject {
                        slots.slots.forEachIndexed { i, slot ->
                            val (textLayer, refLayer) = BandLayerNames.SLOT_LAYER_NAMES[i]
                            put(textLayer, slot.text.toJson())
                            put(refLayer, slot.reference.toJson())
                        }
                    },
                )
            },
        )
    }

    private fun SlotBox.toJson() = buildJsonArray {
        add(JsonPrimitive(x)); add(JsonPrimitive(y)); add(JsonPrimitive(w)); add(JsonPrimitive(h))
    }

    const val METADATA_KEY = "cp"
    const val METADATA_KIND = "kind"
    const val METADATA_KIND_BAND = "bible-band"
    const val METADATA_VERSION = "version"
    const val METADATA_VERSION_1 = 1
    const val METADATA_TEXT_ANIMATION = "textAnimation"
    const val METADATA_TICKER_SPEED = "tickerPxPerSecond"
    const val METADATA_SWAP_MS = "swapMs"
    private const val MILLIS_PER_SECOND = 1000f
    const val METADATA_TEXT_ALIGN = "textAlign"
    const val METADATA_REFERENCE_ALIGN = "referenceAlign"
    const val METADATA_SLOTS = "slots"

    /** [cfg]'s preview text and reference for slot [index] -- the fourth pair the config carries. */
    private fun previewOf(cfg: BibleLottieGenConfig, index: Int): Pair<String, String> = when (index) {
        0 -> cfg.previewText1 to cfg.previewReference1
        1 -> cfg.previewText2 to cfg.previewReference2
        2 -> cfg.previewText3 to cfg.previewReference3
        else -> cfg.previewText4 to cfg.previewReference4
    }

    private fun textSlots(cfg: BibleLottieGenConfig, slots: BandSlots): List<TextSlot> = buildList {
        val textSize = cfg.previewTextSizePx.toDouble()
        slots.slots.forEachIndexed { i, slot ->
            val (textLayer, refLayer) = BandLayerNames.SLOT_LAYER_NAMES[i]
            val (previewText, previewReference) = previewOf(cfg, i)
            add(TextSlot(textLayer, slot.text, previewText, textSize, cfg.previewTextColor))
            add(
                TextSlot(
                    refLayer, slot.reference, previewReference,
                    cfg.previewReferenceSizePx.toDouble(), cfg.previewReferenceColor, isReference = true,
                ),
            )
        }
    }
}
