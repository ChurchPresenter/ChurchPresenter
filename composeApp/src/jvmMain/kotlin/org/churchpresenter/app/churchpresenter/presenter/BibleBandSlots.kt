package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.applyTextTransform
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.BibleTranslationSettings

/** The verse's lower-third typography as a band slot draws it. */
internal fun BibleTranslationSettings.textSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(lowerThirdTextFontType, lowerThirdTextBold, lowerThirdTextItalic),
    fontSizePt = lowerThirdTextFontSize,
    color = if (isKey) Color.White else parseHexColor(lowerThirdTextColor),
    letterSpacingPt = lowerThirdTextLetterSpacing,
    transform = lowerThirdTextTransform,
    justify = justifyOf(lowerThirdTextHorizontalAlignment),
    shadow = lowerThirdTextShadow,
    shadowColor = parseHexColor(lowerThirdTextShadowColor),
    shadowSizePercent = lowerThirdTextShadowSize,
    shadowOpacityPercent = lowerThirdTextShadowOpacity,
)

internal fun BibleTranslationSettings.referenceSlotStyle(isKey: Boolean) = BandSlotStyle(
    font = BandFontKey(lowerThirdReferenceFontType, lowerThirdReferenceBold, lowerThirdReferenceItalic),
    fontSizePt = lowerThirdReferenceFontSize,
    color = if (isKey) Color.White else parseHexColor(lowerThirdReferenceColor),
    letterSpacingPt = lowerThirdReferenceLetterSpacing,
    transform = lowerThirdReferenceTransform,
    justify = justifyOf(lowerThirdReferenceHorizontalAlignment),
    shadow = lowerThirdReferenceShadow,
    shadowColor = parseHexColor(lowerThirdReferenceShadowColor),
    shadowSizePercent = lowerThirdReferenceShadowSize,
    shadowOpacityPercent = lowerThirdReferenceShadowOpacity,
)

/**
 * The Bible band: as many verses of the stack as the template has slots for, and their
 * references, in their translations' faces. A template with one text slot shows every verse
 * stacked in it, the way the classic band stacks them, with every reference on one shared line.
 */
@Composable
internal fun BoxScope.BibleLottieBand(
    template: BibleLottieTemplate,
    verses: List<SelectedVerse>,
    outgoingVerses: List<SelectedVerse>,
    /** This band's translations, primary first -- [t0][BibleTranslationSettings] through the fourth. */
    translations: List<BibleTranslationSettings>,
    bandFraction: Float,
    bandClock: State<BibleBandClock>,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    if (verses.isEmpty()) return
    val availableSlots = template.textSlotCount
    val slots = remember(verses, translations, isKey, availableSlots) {
        bibleBandSlots(verses, translations, isKey, availableSlots)
    }
    val outgoingSlots = remember(outgoingVerses, translations, isKey, availableSlots) {
        if (outgoingVerses.isEmpty()) null else bibleBandSlots(outgoingVerses, translations, isKey, availableSlots)
    }
    LottieBand(template, slots, outgoingSlots, bandFraction, bandClock, isKey, showBackground, modifier)
}

/**
 * [verses] laid across the template's slots — the same mapping for the incoming and outgoing text.
 *
 * More verses than [availableSlots]: the first [availableSlots] of them, not crammed together --
 * matching how a song with more languages than slots behaves. Exactly one slot is the one
 * exception, inherited from before a band could have more than two: every verse stacks into it
 * together, references sharing one line, rather than showing the first and dropping the rest.
 */
internal fun bibleBandSlots(
    verses: List<SelectedVerse>,
    translations: List<BibleTranslationSettings>,
    isKey: Boolean,
    availableSlots: Int,
): Map<String, BandSlotText> {
    val t0 = translations.getOrElse(0) { BibleTranslationSettings() }
    val text1 = t0.textSlotStyle(isKey)
    val ref1 = t0.referenceSlotStyle(isKey)
    if (availableSlots <= 1 || verses.size <= 1) {
        val texts = verses.map { applyTextTransform(it.verseText, text1.transform) }
        val refs = verses.mapIndexed { i, v ->
            val t = translations.getOrElse(i) { t0 }
            applyTextTransform(buildRefText(v, t), t.referenceSlotStyle(isKey).transform)
        }
        return mapOf(
            BibleLottieTemplate.LAYER_TEXT_1 to BandSlotText(texts.joinToString("\n"), text1),
            BibleLottieTemplate.LAYER_REFERENCE_1 to
                BandSlotText(refs.filter { it.isNotBlank() }.joinToString(REFERENCE_SEPARATOR), ref1),
        )
    }
    val shown = verses.take(availableSlots)
    return buildMap {
        BibleLottieTemplate.TEXT_SLOT_LAYERS.forEachIndexed { i, (textLayer, refLayer) ->
            val verse = shown.getOrNull(i)
            val t = translations.getOrElse(i) { t0 }
            val text = t.textSlotStyle(isKey)
            val ref = t.referenceSlotStyle(isKey)
            val textString = verse?.let { applyTextTransform(it.verseText, text.transform) }.orEmpty()
            val refString = verse?.let { applyTextTransform(buildRefText(it, t), ref.transform) }.orEmpty()
            put(textLayer, BandSlotText(textString, text))
            put(refLayer, BandSlotText(refString, ref))
        }
    }
}

/** Between two references sharing one line. */
private const val REFERENCE_SEPARATOR = "  ·  "
