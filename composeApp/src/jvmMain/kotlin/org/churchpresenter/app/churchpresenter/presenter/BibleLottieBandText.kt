package org.churchpresenter.app.churchpresenter.presenter

import kotlin.math.ceil

/**
 * What each layer of a ticker band shows: every text layer carries its own reference at its head and
 * scrolls as one line; every reference layer is left empty so nothing sits still beside the motion.
 * Read off [BibleLottieTemplate.TEXT_SLOT_LAYERS] rather than named one pair at a time, so a third
 * and fourth slot tick the same way the first two always have.
 */
internal fun tickerTexts(texts: Map<String, String>): Map<String, String> {
    val textLayers = BibleLottieTemplate.TEXT_SLOT_LAYERS.map { it.first }.toSet()
    val referenceOf = BibleLottieTemplate.TEXT_SLOT_LAYERS.associate { (text, reference) -> text to reference }
    return texts.mapValues { (name, text) ->
        when {
            name in textLayers -> tickerLine(texts[referenceOf.getValue(name)], text)
            referenceOf.containsValue(name) -> ""
            else -> text
        }
    }
}

/** The reference, a gap, then the text — what a ticker scrolls as one line. */
internal fun tickerLine(reference: String?, text: String): String =
    if (reference.isNullOrBlank() || text.isBlank()) text else "$reference$TICKER_GAP$text"

internal const val TICKER_GAP = "    "

/**
 * The part of [text] a typewriter has typed by [frame]: everything during the hold, growing
 * through `text_in`, shrinking back through `text_out`. Keyframed animations show all of it and
 * let the file do the moving.
 */
internal fun revealedText(template: BibleLottieTemplate, text: String, frame: Float): String {
    val motion = template.meta.textMotion
    if (motion == BandTextMotion.NONE || motion == BandTextMotion.TICKER) return text
    val textIn = template.segment(BibleLottieTemplate.SEGMENT_TEXT_IN)
    val textOut = template.segment(BibleLottieTemplate.SEGMENT_TEXT_OUT)
    val fraction = when {
        frame < textIn.startFrame -> 0f
        frame <= textIn.endFrame -> template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_IN, frame)
        frame < textOut.startFrame -> 1f
        frame <= textOut.endFrame -> 1f - template.progressWithin(BibleLottieTemplate.SEGMENT_TEXT_OUT, frame)
        else -> 0f
    }
    return when (motion) {
        BandTextMotion.TYPEWRITER -> text.take(ceil(text.length * fraction).toInt())
        else -> {
            val words = text.split(' ')
            words.take(ceil(words.size * fraction).toInt()).joinToString(" ")
        }
    }
}
