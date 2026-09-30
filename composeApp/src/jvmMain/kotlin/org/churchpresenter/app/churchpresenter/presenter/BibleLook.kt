package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import androidx.compose.ui.unit.em
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import androidx.compose.ui.text.font.FontFamily
import org.churchpresenter.app.churchpresenter.composables.TextBackdropPainter

/**
 * Which verses one output draws and the translation styles it draws them in, resolved before
 * anything else -- the Lottie band needs no more than this.
 */
@Suppress("LongParameterList")
internal class BibleLook(
    val selectedVerses: List<SelectedVerse>,
    val appSettings: AppSettings,
    val isLowerThird: Boolean,
    val isLowerThirdVertical: Boolean,
    outputRole: String,
    val transitionAlpha: Float,
    val showBackground: Boolean,
    val crossfadeEnabled: Boolean,
    val bibleTranslations: List<Int>,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val bs = appSettings.bibleSettings
    val translationStack = bs.translationList()

    // Filter to the translations this screen is assigned. Selecting one no longer promotes it into
    // the primary slot the way the old "secondary" mode did: styling is looked up per verse by its
    // own translation, so a verse keeps its own colours and font wherever it lands.
    //
    // Matched by file name, not by position. `bibleTranslations` names positions in the *configured
    // stack*, but this list only carries the translations that actually produced text -- a module
    // whose file has gone, or which simply has no verse at this reference, is absent. Filtering by
    // position against a list with a hole in it hands the screen a different translation than the
    // one it was assigned, and does it silently: a critical-text module that stops at Mark 16:8
    // would flip that screen to the next language for exactly those verses.
    //
    // Drawn in the profile's own order, not the stack's: the Profiles tab lets a profile put its
    // translations in whatever order its room reads them, and the list it stores is that order.
    val assignedFileNames = bibleTranslations.mapNotNull { translationStack.getOrNull(it)?.fileName }.distinct()
    fun versesForOutput(verses: List<SelectedVerse>): List<SelectedVerse> = when {
        bibleTranslations.isEmpty() -> verses
        // Verses relayed from a linked instance or the companion server carry no translation
        // identity, so position is all there is to match on for those.
        verses.none { it.translationFileName.isNotBlank() } ->
            bibleTranslations.distinct().mapNotNull { verses.getOrNull(it) }
                .ifEmpty { verses.take(1) }
        else -> verses.filter { it.translationFileName in assignedFileNames }
            .sortedBy { assignedFileNames.indexOf(it.translationFileName) }
            .ifEmpty { verses.take(1) }
    }
    val effectiveVerses = versesForOutput(selectedVerses)

    /**
     * The styling for whatever ends up in slot [slot] of what this output draws.
     *
     * Resolved from the verse's own translation so the lower third styles what it is actually
     * showing, and only falls back to the stack position when the verse carries no identity.
     */
    fun slotStyle(slot: Int): BibleTranslationSettings {
        val fileName = effectiveVerses.getOrNull(slot)?.translationFileName
        return translationStack.firstOrNull { fileName != null && it.fileName == fileName }
            ?: translationStack.getOrNull(slot)
            ?: BibleTranslationSettings()
    }

    // The first two translations, which the lower third's classic (non-Lottie) band renders, and
    // the third and fourth, which only the Lottie band's own 3/4-slot templates reach. Full screen
    // draws the whole stack instead; a missing entry falls back to defaults so an unconfigured slot
    // still has a style.
    val t0 = slotStyle(0)
    val t1 = slotStyle(1)
    val t2 = slotStyle(2)
    val t3 = slotStyle(3)
    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.bibleLowerThirdBackground
    else appSettings.backgroundSettings.bibleBackground
}

/**
 * How one output draws its verses once there is a verse to draw: the first two translations' fonts,
 * colours, shadows, text styles and alignments, resolved for full screen or lower third and for a
 * key output. Forwards [BibleLook]'s values under their own names.
 */
@Suppress("LongParameterList")
internal class BibleStyle(
    val look: BibleLook,
    fonts: BibleFonts,
    colors: BibleColors,
    painters: BiblePainters,
) {
    val isKey get() = look.isKey
    val bs get() = look.bs
    val translationStack get() = look.translationStack
    val assignedFileNames get() = look.assignedFileNames
    val effectiveVerses get() = look.effectiveVerses
    val t0 get() = look.t0
    val t1 get() = look.t1
    val t2 get() = look.t2
    val t3 get() = look.t3
    val bgConfig get() = look.bgConfig
    val selectedVerses get() = look.selectedVerses
    val appSettings get() = look.appSettings
    val isLowerThird get() = look.isLowerThird
    val isLowerThirdVertical get() = look.isLowerThirdVertical
    val transitionAlpha get() = look.transitionAlpha
    val showBackground get() = look.showBackground
    val crossfadeEnabled get() = look.crossfadeEnabled
    val bibleTranslations get() = look.bibleTranslations

    fun versesForOutput(verses: List<SelectedVerse>): List<SelectedVerse> = look.versesForOutput(verses)

    val primaryBibleFontStyle = fonts.primaryBibleFontStyle
    val primaryBibleReferenceFontStyle = fonts.primaryBibleReferenceFontStyle
    val secondaryBibleFontStyle = fonts.secondaryBibleFontStyle
    val secondaryBibleReferenceFontStyle = fonts.secondaryBibleReferenceFontStyle
    val primaryBibleTextColor = colors.primaryBibleTextColor
    val primaryBibleReferenceTextColor = colors.primaryBibleReferenceTextColor
    val secondaryBibleTextColor = colors.secondaryBibleTextColor
    val secondaryBibleReferenceTextColor = colors.secondaryBibleReferenceTextColor
    val pTextPainter = painters.pTextPainter
    val pRefPainter = painters.pRefPainter
    val sTextPainter = painters.sTextPainter
    val sRefPainter = painters.sRefPainter

    val secondaryBible = effectiveVerses.getOrNull(1)

    // Resolve bold/italic/underline/shadow — use lower-third-specific values when applicable
    val pBold = if (isLowerThird) t0.lowerThirdTextBold else t0.textBold
    val pItalic = if (isLowerThird) t0.lowerThirdTextItalic else t0.textItalic
    val pUnderline = if (isLowerThird) t0.lowerThirdTextUnderline else t0.textUnderline
    val pShadow = if (isLowerThird) t0.lowerThirdTextShadow else t0.textShadow
    val prBold = if (isLowerThird) t0.lowerThirdReferenceBold else t0.referenceBold
    val prItalic = if (isLowerThird) t0.lowerThirdReferenceItalic else t0.referenceItalic
    val prUnderline = if (isLowerThird) t0.lowerThirdReferenceUnderline else t0.referenceUnderline
    val prShadow = if (isLowerThird) t0.lowerThirdReferenceShadow else t0.referenceShadow
    val sBold = if (isLowerThird) t1.lowerThirdTextBold else t1.textBold
    val sItalic = if (isLowerThird) t1.lowerThirdTextItalic else t1.textItalic
    val sUnderline = if (isLowerThird) t1.lowerThirdTextUnderline else t1.textUnderline
    val sShadow = if (isLowerThird) t1.lowerThirdTextShadow else t1.textShadow
    val srBold = if (isLowerThird) t1.lowerThirdReferenceBold else t1.referenceBold
    val srItalic = if (isLowerThird) t1.lowerThirdReferenceItalic else t1.referenceItalic
    val srUnderline = if (isLowerThird) t1.lowerThirdReferenceUnderline else t1.referenceUnderline
    val srShadow = if (isLowerThird) t1.lowerThirdReferenceShadow else t1.referenceShadow

    // The four settings that reach the text rather than the TextStyle alone. Spacing is turned into
    // a fraction of the em here so it keeps its proportion through the resolution scale and the
    // auto-fit below -- see `spacingEm`.
    val pStrike = if (isLowerThird) t0.lowerThirdTextStrikethrough else t0.textStrikethrough
    val prStrike = if (isLowerThird) t0.lowerThirdReferenceStrikethrough else t0.referenceStrikethrough
    val sStrike = if (isLowerThird) t1.lowerThirdTextStrikethrough else t1.textStrikethrough
    val srStrike = if (isLowerThird) t1.lowerThirdReferenceStrikethrough else t1.referenceStrikethrough
    val pTransform = if (isLowerThird) t0.lowerThirdTextTransform else t0.textTransform
    val prTransform = if (isLowerThird) t0.lowerThirdReferenceTransform else t0.referenceTransform
    val sTransform = if (isLowerThird) t1.lowerThirdTextTransform else t1.textTransform
    val srTransform = if (isLowerThird) t1.lowerThirdReferenceTransform else t1.referenceTransform
    val pLsEm = spacingEm(
        if (isLowerThird) t0.lowerThirdTextLetterSpacing else t0.textLetterSpacing,
        if (isLowerThird) t0.lowerThirdTextFontSize else t0.textFontSize,
    )
    val prLsEm = spacingEm(
        if (isLowerThird) t0.lowerThirdReferenceLetterSpacing else t0.referenceLetterSpacing,
        if (isLowerThird) t0.lowerThirdReferenceFontSize else t0.referenceFontSize,
    )
    val sLsEm = spacingEm(
        if (isLowerThird) t1.lowerThirdTextLetterSpacing else t1.textLetterSpacing,
        if (isLowerThird) t1.lowerThirdTextFontSize else t1.textFontSize,
    )
    val srLsEm = spacingEm(
        if (isLowerThird) t1.lowerThirdReferenceLetterSpacing else t1.referenceLetterSpacing,
        if (isLowerThird) t1.lowerThirdReferenceFontSize else t1.referenceFontSize,
    )
    val pWsEm = spacingEm(
        if (isLowerThird) t0.lowerThirdTextWordSpacing else t0.textWordSpacing,
        if (isLowerThird) t0.lowerThirdTextFontSize else t0.textFontSize,
    )
    val prWsEm = spacingEm(
        if (isLowerThird) t0.lowerThirdReferenceWordSpacing else t0.referenceWordSpacing,
        if (isLowerThird) t0.lowerThirdReferenceFontSize else t0.referenceFontSize,
    )
    val sWsEm = spacingEm(
        if (isLowerThird) t1.lowerThirdTextWordSpacing else t1.textWordSpacing,
        if (isLowerThird) t1.lowerThirdTextFontSize else t1.textFontSize,
    )
    val srWsEm = spacingEm(
        if (isLowerThird) t1.lowerThirdReferenceWordSpacing else t1.referenceWordSpacing,
        if (isLowerThird) t1.lowerThirdReferenceFontSize else t1.referenceFontSize,
    )

    // What actually goes on screen for each of the four elements: the transform applied and the word
    // breaks widened. Named per element so the call sites below stay one line each, and used for the
    // fit measurements too -- an uppercased verse is wider than the one it came from.
    fun pText(raw: String) = styledDisplayText(raw, pTransform, pLsEm, pWsEm)
    fun prText(raw: String) = styledDisplayText(raw, prTransform, prLsEm, prWsEm)
    fun sText(raw: String) = styledDisplayText(raw, sTransform, sLsEm, sWsEm)
    fun srText(raw: String) = styledDisplayText(raw, srTransform, srLsEm, srWsEm)

    // Per-element shadow helpers
    fun makeShadow(color: String, size: Int, opacity: Int, alphaScale: Float = 0.78f): Shadow {
        val base = parseHexColor(color)
        val mul = size / 100f
        val alpha = (opacity / 100f).coerceIn(0f, 1f)
        return Shadow(
            color = base.copy(alpha = alpha * alphaScale),
            offset = Offset(2f * mul, 2f * mul),
            blurRadius = 4f * mul
        )
    }

    val pBibleShadowVal = makeShadow(
        if (isLowerThird) t0.lowerThirdTextShadowColor else t0.textShadowColor,
        if (isLowerThird) t0.lowerThirdTextShadowSize else t0.textShadowSize,
        if (isLowerThird) t0.lowerThirdTextShadowOpacity else t0.textShadowOpacity
    )
    val pRefShadowVal = makeShadow(
        if (isLowerThird) t0.lowerThirdReferenceShadowColor else t0.referenceShadowColor,
        if (isLowerThird) t0.lowerThirdReferenceShadowSize else t0.referenceShadowSize,
        if (isLowerThird) t0.lowerThirdReferenceShadowOpacity else t0.referenceShadowOpacity
    )
    val sBibleShadowVal = makeShadow(
        if (isLowerThird) t1.lowerThirdTextShadowColor else t1.textShadowColor,
        if (isLowerThird) t1.lowerThirdTextShadowSize else t1.textShadowSize,
        if (isLowerThird) t1.lowerThirdTextShadowOpacity else t1.textShadowOpacity
    )
    val sRefShadowVal = makeShadow(
        if (isLowerThird) t1.lowerThirdReferenceShadowColor else t1.referenceShadowColor,
        if (isLowerThird) t1.lowerThirdReferenceShadowSize else t1.referenceShadowSize,
        if (isLowerThird) t1.lowerThirdReferenceShadowOpacity else t1.referenceShadowOpacity
    )

    // One painter per profile: each holds the last layout its Text reported, which is what lets the
    // bands and the box be drawn from the measured lines without a wrapper that would take up room.

    // Text styles from settings
    val primaryBibleTextStyle = TextStyle(
        fontWeight = if (pBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (pItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(pUnderline, pStrike),
        letterSpacing = pLsEm.em,
        shadow = if (pShadow) pBibleShadowVal else null
    )
    val primaryReferenceTextStyle = TextStyle(
        fontWeight = if (prBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (prItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(prUnderline, prStrike),
        letterSpacing = prLsEm.em,
        shadow = if (prShadow) pRefShadowVal else null
    )
    val secondaryBibleTextStyle = TextStyle(
        fontWeight = if (sBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (sItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(sUnderline, sStrike),
        letterSpacing = sLsEm.em,
        shadow = if (sShadow) sBibleShadowVal else null
    )
    val secondaryReferenceTextStyle = TextStyle(
        fontWeight = if (srBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (srItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = combinedTextDecoration(srUnderline, srStrike),
        letterSpacing = srLsEm.em,
        shadow = if (srShadow) sRefShadowVal else null
    )

    val primaryBibleHorizontalAlignment = when (
        if (isLowerThird) t0.lowerThirdTextHorizontalAlignment
        else t0.textHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val primaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) t0.lowerThirdReferenceHorizontalAlignment
        else t0.referenceHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val secondaryBibleHorizontalAlignment = when (
        if (isLowerThird) t1.lowerThirdTextHorizontalAlignment
        else t1.textHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val secondaryBibleReferenceHorizontalAlignment = when (
        if (isLowerThird) t1.lowerThirdReferenceHorizontalAlignment
        else t1.referenceHorizontalAlignment
    ) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }

    val primaryBibleReferencePosition = if (isLowerThird) t0.lowerThirdReferencePosition else t0.referencePosition
    val secondaryBibleReferencePosition = if (isLowerThird) t1.lowerThirdReferencePosition else t1.referencePosition

    // Combine vertical alignment with horizontal center
    val contentAlignment = when (appSettings.bibleSettings.verticalAlignment) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center  // MIDDLE or default
    }
}

/** The first two translations' fonts, remembered so a font is not looked up again on every frame. */
internal class BibleFonts(
    val primaryBibleFontStyle: FontFamily,
    val primaryBibleReferenceFontStyle: FontFamily,
    val secondaryBibleFontStyle: FontFamily,
    val secondaryBibleReferenceFontStyle: FontFamily,
)

/** The first two translations' colours -- white throughout for a key output. */
internal class BibleColors(
    val primaryBibleTextColor: Color,
    val primaryBibleReferenceTextColor: Color,
    val secondaryBibleTextColor: Color,
    val secondaryBibleReferenceTextColor: Color,
)

/** One backdrop painter per profile: each holds the last layout its Text reported. */
internal class BiblePainters(
    val pTextPainter: TextBackdropPainter,
    val pRefPainter: TextBackdropPainter,
    val sTextPainter: TextBackdropPainter,
    val sRefPainter: TextBackdropPainter,
)

@Composable
internal fun BibleLook.rememberBibleFonts(): BibleFonts {
    val primaryBibleFontStyle = remember(
        if (isLowerThird) t0.lowerThirdTextFontType else t0.textFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) t0.lowerThirdTextFontType else t0.textFontType)
    }
    val primaryBibleReferenceFontStyle = remember(
        if (isLowerThird) t0.lowerThirdReferenceFontType else t0.referenceFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) t0.lowerThirdReferenceFontType else t0.referenceFontType)
    }
    val secondaryBibleFontStyle = remember(
        if (isLowerThird) t1.lowerThirdTextFontType else t1.textFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) t1.lowerThirdTextFontType else t1.textFontType)
    }
    val secondaryBibleReferenceFontStyle = remember(
        if (isLowerThird) t1.lowerThirdReferenceFontType else t1.referenceFontType
    ) {
        systemFontFamilyOrDefault(if (isLowerThird) t1.lowerThirdReferenceFontType else t1.referenceFontType)
    }
    return BibleFonts(
        primaryBibleFontStyle,
        primaryBibleReferenceFontStyle,
        secondaryBibleFontStyle,
        secondaryBibleReferenceFontStyle,
    )
}

@Composable
internal fun BibleLook.rememberBibleColors(isKey: Boolean): BibleColors {
    // Resolve colors — key mode forces white for a proper key signal
    val primaryBibleTextColor = remember(
        if (isLowerThird) t0.lowerThirdTextColor else t0.textColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) t0.lowerThirdTextColor else t0.textColor)
    }
    val primaryBibleReferenceTextColor = remember(
        if (isLowerThird) t0.lowerThirdReferenceColor else t0.referenceColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) t0.lowerThirdReferenceColor else t0.referenceColor)
    }
    val secondaryBibleTextColor = remember(
        if (isLowerThird) t1.lowerThirdTextColor else t1.textColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) t1.lowerThirdTextColor else t1.textColor)
    }
    val secondaryBibleReferenceTextColor = remember(
        if (isLowerThird) t1.lowerThirdReferenceColor else t1.referenceColor, isKey
    ) {
        if (isKey) Color.White
        else parseHexColor(if (isLowerThird) t1.lowerThirdReferenceColor else t1.referenceColor)
    }
    return BibleColors(
        primaryBibleTextColor,
        primaryBibleReferenceTextColor,
        secondaryBibleTextColor,
        secondaryBibleReferenceTextColor,
    )
}

@Composable
internal fun BibleLook.rememberBiblePainters(): BiblePainters {
    // One painter per profile: each holds the last layout its Text reported, which is what lets the
    // bands and the box be drawn from the measured lines without a wrapper that would take up room.
    val pTextPainter = rememberTextBackdropPainter(t0.textBackdropFor(isLowerThird))
    val pRefPainter = rememberTextBackdropPainter(t0.referenceBackdropFor(isLowerThird))
    val sTextPainter = rememberTextBackdropPainter(t1.textBackdropFor(isLowerThird))
    val sRefPainter = rememberTextBackdropPainter(t1.referenceBackdropFor(isLowerThird))
    return BiblePainters(pTextPainter, pRefPainter, sTextPainter, sRefPainter)
}
