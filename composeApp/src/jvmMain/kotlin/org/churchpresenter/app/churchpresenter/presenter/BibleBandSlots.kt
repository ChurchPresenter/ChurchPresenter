package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
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
 * The Bible band: the first two verses of the stack and their references, in their translations'
 * faces. A template with one text slot shows both languages stacked in it, the way the classic
 * band stacks them, with the two references side by side.
 */
@Composable
internal fun BoxScope.BibleLottieBand(
    template: BibleLottieTemplate,
    verses: List<SelectedVerse>,
    t0: BibleTranslationSettings,
    t1: BibleTranslationSettings,
    bandFraction: Float,
    bandClock: BibleBandClock,
    isKey: Boolean,
    showBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = verses.firstOrNull() ?: return
    val secondary = verses.getOrNull(1)
    val hasSecondSlot = template.hasLayer(BibleLottieTemplate.LAYER_TEXT_2)
    val slots = remember(primary, secondary, t0, t1, isKey, hasSecondSlot) {
        val text1 = t0.textSlotStyle(isKey)
        val ref1 = t0.referenceSlotStyle(isKey)
        val text2 = t1.textSlotStyle(isKey)
        val ref2 = t1.referenceSlotStyle(isKey)
        val primaryText = applyTextTransform(primary.verseText, text1.transform)
        val primaryRef = applyTextTransform(buildRefText(primary, t0), ref1.transform)
        val secondaryText = secondary?.let { applyTextTransform(it.verseText, text2.transform) }.orEmpty()
        val secondaryRef = secondary?.let { applyTextTransform(buildRefText(it, t1), ref2.transform) }.orEmpty()
        if (hasSecondSlot || secondary == null) {
            mapOf(
                BibleLottieTemplate.LAYER_TEXT_1 to BandSlotText(primaryText, text1),
                BibleLottieTemplate.LAYER_REFERENCE_1 to BandSlotText(primaryRef, ref1),
                BibleLottieTemplate.LAYER_TEXT_2 to BandSlotText(secondaryText, text2),
                BibleLottieTemplate.LAYER_REFERENCE_2 to BandSlotText(secondaryRef, ref2),
            )
        } else {
            mapOf(
                BibleLottieTemplate.LAYER_TEXT_1 to BandSlotText("$primaryText\n$secondaryText", text1),
                BibleLottieTemplate.LAYER_REFERENCE_1 to
                    BandSlotText(
                        listOf(primaryRef, secondaryRef).filter { it.isNotBlank() }.joinToString(REFERENCE_SEPARATOR),
                        ref1,
                    ),
            )
        }
    }
    LottieBand(template, slots, bandFraction, bandClock, isKey, showBackground, modifier)
}

/** Between two references sharing one line. */
private const val REFERENCE_SEPARATOR = "  ·  "
