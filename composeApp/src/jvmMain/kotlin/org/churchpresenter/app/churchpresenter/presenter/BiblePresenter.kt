package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.utils.spacingEm
import org.churchpresenter.app.churchpresenter.utils.combinedTextDecoration
import org.churchpresenter.app.churchpresenter.utils.styledDisplayText
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.composables.rememberTextBackdropPainter
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.composables.LoopingVideoBackground
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault

private const val SHADOW_OFFSET_PX = 6f

/**
 * The smallest scale the search below will go down to before giving up.
 *
 * Only a backstop against a `fits` predicate that can never be satisfied — one whose height does not
 * actually depend on the scale it is handed. It sits four orders of magnitude below full size, which
 * is far past anything real: the worst genuine case, every translation of a long passage crammed into
 * a lower-third band, overflows by tens of times, not thousands. Nothing legible lives down here; a
 * fit found anywhere near it means the layout has a problem no fit scale can solve.
 */
private const val MIN_FIT_SCALE = 0.0001f

/** How strongly the rule between two translations reads against the verse text either side. */
private const val DIVIDER_ALPHA = 0.45f

/**
 * The largest scale at or below 1 whose content fits, per [fits].
 *
 * **Fitting is the guarantee.** Verse text shrinks to stay whole; it is never cut off to keep a size,
 * however many translations are stacked or however long the passage. So this does not take a floor:
 * full size is returned when it fits, and otherwise it starts from [startScale], halving while that
 * does not fit — down to [MIN_FIT_SCALE] — before bisecting upward for the largest scale that does.
 *
 * That halving is the part it used to be missing. It began at a fixed 15% floor and returned that
 * floor without ever testing it, so a caller could not tell "the largest scale that fits" from
 * "nothing in range fits" and got silent overflow for the second (issue #97). Raising the floor made
 * that worse rather than better: it turned unreadably-small into cut-off.
 *
 * A caveat for callers: everything contributing to the measured height has to scale with the argument.
 * A predicate holding part of its height fixed — a reference line measured once at full size — can be
 * unsatisfiable at any scale, and no search can rescue that.
 */
internal fun binarySearchFitScale(
    startScale: Float = 0.15f,
    iterations: Int = 8,
    fits: (scale: Float) -> Boolean
): Float {
    if (fits(1f)) return 1f
    var lo = startScale.coerceIn(MIN_FIT_SCALE, 1f)
    while (lo > MIN_FIT_SCALE && !fits(lo)) {
        lo = (lo / 2f).coerceAtLeast(MIN_FIT_SCALE)
    }
    var hi = 1f
    repeat(iterations) {
        val mid = (lo + hi) / 2f
        if (fits(mid)) lo = mid else hi = mid
    }
    return lo
}

/**
 * The line under (or over) a verse: "KJV John 3:16".
 *
 * The label is [BibleTranslationSettings.customAbbreviation] where the operator typed one, and the
 * module's own otherwise -- which is what the abbreviation box offers as its placeholder, so the two
 * agree about what a blank box means.
 *
 * Parts are joined rather than interpolated so an absent label costs no separator; the form this
 * replaced always emitted its leading space, so a translation with no abbreviation drew
 * " John 3:16".
 */
/** The line background and border box [BibleTranslationSettings] keeps for the verse, per output. */
internal fun BibleTranslationSettings.textBackdropFor(lowerThird: Boolean): TextBackdrop =
    if (lowerThird) lowerThirdTextBackdrop else textBackdrop

/** The same for the reference line. */
internal fun BibleTranslationSettings.referenceBackdropFor(lowerThird: Boolean): TextBackdrop =
    if (lowerThird) lowerThirdReferenceBackdrop else referenceBackdrop

internal fun buildRefText(verse: SelectedVerse, translation: BibleTranslationSettings): String {
    val label = if (translation.showAbbreviation) {
        translation.customAbbreviation.trim().ifBlank { verse.bibleAbbreviation.trim() }
    } else {
        ""
    }
    val verseRef = if (verse.verseRange.isNotEmpty()) verse.verseRange else verse.verseNumber.toString()
    return listOf(label, verse.bookName, "${verse.chapter}:$verseRef")
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}

@Composable
fun BiblePresenter(
    modifier: Modifier = Modifier,
    selectedVerses: List<SelectedVerse>,
    appSettings: AppSettings,
    isLowerThird: Boolean = false,
    // Only changes the band's geometry (a right-anchored vertical strip instead of a bottom
    // horizontal band) — isLowerThird alone still selects all the *LowerThird* styling fields
    // (fonts/colors/sizes/etc.) for both orientations, so there's one style profile to maintain.
    isLowerThirdVertical: Boolean = false,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transitionAlpha: Float = 1f,
    showBackground: Boolean = true,
    crossfadeEnabled: Boolean = false,
    /** Positions in the translation stack this output shows; empty means all of them. */
    bibleTranslations: List<Int> = emptyList(),
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
    val assignedFileNames = bibleTranslations.mapNotNull { translationStack.getOrNull(it)?.fileName }.toSet()
    val effectiveVerses = when {
        bibleTranslations.isEmpty() -> selectedVerses
        // Verses relayed from a linked instance or the companion server carry no translation
        // identity, so position is all there is to match on for those.
        selectedVerses.none { it.translationFileName.isNotBlank() } ->
            selectedVerses.filterIndexed { index, _ -> index in bibleTranslations }
                .ifEmpty { selectedVerses.take(1) }
        else -> selectedVerses.filter { it.translationFileName in assignedFileNames }
            .ifEmpty { selectedVerses.take(1) }
    }

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

    // The first two translations, which the lower third renders. Full screen draws the whole stack
    // instead; a missing entry falls back to defaults so an unconfigured slot still has a style.
    val t0 = slotStyle(0)
    val t1 = slotStyle(1)

    // Resolve font families — use lower-third-specific values when applicable
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

    effectiveVerses.firstOrNull() ?: return
    val secondaryBible = effectiveVerses.getOrNull(1)

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
    val pTextPainter = rememberTextBackdropPainter(t0.textBackdropFor(isLowerThird))
    val pRefPainter = rememberTextBackdropPainter(t0.referenceBackdropFor(isLowerThird))
    val sTextPainter = rememberTextBackdropPainter(t1.textBackdropFor(isLowerThird))
    val sRefPainter = rememberTextBackdropPainter(t1.referenceBackdropFor(isLowerThird))

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

    val bgConfig = if (isLowerThird) appSettings.backgroundSettings.bibleLowerThirdBackground
    else appSettings.backgroundSettings.bibleBackground

    // A verse carries no background of its own, so this is the quick tray's pick, then the Bible
    // background, then the defaults — the same order and the same resolver songs go through.
    val resolvedBg = resolveBackground(
        settings = appSettings.backgroundSettings,
        config = bgConfig,
        isLowerThird = isLowerThird,
        showBackground = showBackground,
        transparentWhenBlank = LocalTransparentBlanking.current,
    )
    val backgroundImageBitmap = rememberBackgroundBitmap(resolvedBg, isLowerThird)
    val useVideoBackground = resolvedBg.usesVideo
    val effectiveOpacity = resolvedBg.opacity
    val bgModifier: Modifier = backgroundModifier(resolvedBg, backgroundImageBitmap)

    // Fade-in on first appearance (covers background + text)
    val fadeInDuration = appSettings.bibleSettings.transitionDuration.toInt().coerceAtLeast(100)
    var enterAlpha by remember { mutableStateOf(if (appSettings.bibleSettings.fadeIn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (appSettings.bibleSettings.fadeIn && enterAlpha < 1f) {
            val anim = Animatable(0f)
            anim.animateTo(1f, tween(durationMillis = fadeInDuration)) {
                enterAlpha = this.value
            }
            enterAlpha = 1f
        }
    }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .graphicsLayer { alpha = transitionAlpha * enterAlpha }
            .then(if (!isLowerThird && !resolvedBg.isBlurred) bgModifier else Modifier)
    ) {
        val density = LocalDensity.current
        val scaleFactor = presenterScale(maxWidth, maxHeight)
        val blurRadius = backgroundBlurRadius(resolvedBg.blurReferencePx, maxWidth)
        PresenterBackgroundLayers(
            background = resolvedBg,
            backgroundModifier = bgModifier,
            isLowerThird = isLowerThird,
            blurRadius = blurRadius,
        )

        // Scale shadow to be visible at projection resolution
        fun scaleElementShadow(color: String, size: Int, opacity: Int): Shadow {
            val base = parseHexColor(color)
            val mul = size / 100f
            val alpha = (opacity / 100f).coerceIn(0f, 1f)
            return Shadow(
                color = base.copy(alpha = alpha),
                offset = Offset(SHADOW_OFFSET_PX * scaleFactor * mul, SHADOW_OFFSET_PX * scaleFactor * mul),
                blurRadius = 12f * scaleFactor * mul
            )
        }
        val primaryBibleTextStyleScaled = if (pShadow)
            primaryBibleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) t0.lowerThirdTextShadowColor else t0.textShadowColor,
                if (isLowerThird) t0.lowerThirdTextShadowSize else t0.textShadowSize,
                if (isLowerThird) t0.lowerThirdTextShadowOpacity else t0.textShadowOpacity
            )) else primaryBibleTextStyle
        val primaryReferenceTextStyleScaled = if (prShadow)
            primaryReferenceTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) t0.lowerThirdReferenceShadowColor else t0.referenceShadowColor,
                if (isLowerThird) t0.lowerThirdReferenceShadowSize else t0.referenceShadowSize,
                if (isLowerThird) t0.lowerThirdReferenceShadowOpacity else t0.referenceShadowOpacity
            )) else primaryReferenceTextStyle
        val secondaryBibleTextStyleScaled = if (sShadow)
            secondaryBibleTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) t1.lowerThirdTextShadowColor else t1.textShadowColor,
                if (isLowerThird) t1.lowerThirdTextShadowSize else t1.textShadowSize,
                if (isLowerThird) t1.lowerThirdTextShadowOpacity else t1.textShadowOpacity
            )) else secondaryBibleTextStyle
        val secondaryReferenceTextStyleScaled = if (srShadow)
            secondaryReferenceTextStyle.copy(shadow = scaleElementShadow(
                if (isLowerThird) t1.lowerThirdReferenceShadowColor else t1.referenceShadowColor,
                if (isLowerThird) t1.lowerThirdReferenceShadowSize else t1.referenceShadowSize,
                if (isLowerThird) t1.lowerThirdReferenceShadowOpacity else t1.referenceShadowOpacity
            )) else secondaryReferenceTextStyle

        val effectivePrimaryBibleSize =
            if (isLowerThird) t0.lowerThirdTextFontSize else t0.textFontSize
        val effectivePrimaryReferenceSize =
            if (isLowerThird) t0.lowerThirdReferenceFontSize else t0.referenceFontSize
        val effectiveSecondaryBibleSize =
            if (isLowerThird) t1.lowerThirdTextFontSize else t1.textFontSize
        val effectiveSecondaryReferenceSize =
            if (isLowerThird) t1.lowerThirdReferenceFontSize else t1.referenceFontSize
        val scaledPrimaryBibleSize = (effectivePrimaryBibleSize * scaleFactor).sp
        val scaledPrimaryReferenceSize = (effectivePrimaryReferenceSize * scaleFactor).sp
        val scaledSecondaryBibleSize = (effectiveSecondaryBibleSize * scaleFactor).sp
        val scaledSecondaryReferenceSize = (effectiveSecondaryReferenceSize * scaleFactor).sp
        val leftOffSet = ((appSettings.projectionSettings.windowLeft + appSettings.bibleSettings.marginLeft) * scaleFactor).dp
        val rightOffSet = ((appSettings.projectionSettings.windowRight + appSettings.bibleSettings.marginRight) * scaleFactor).dp
        val topOffSet = ((appSettings.projectionSettings.windowTop + appSettings.bibleSettings.marginTop) * scaleFactor).dp
        val bottomOffSet = ((appSettings.projectionSettings.windowBottom + appSettings.bibleSettings.marginBottom) * scaleFactor).dp

        if (isLowerThird) {
            val lowerThirdFraction = appSettings.bibleSettings.lowerThirdHeightPercent / 100f
            // Background stretches full width at bottom third, text respects padding on top —
            // same band geometry for horizontal and vertical; isLowerThirdVertical only forces
            // bilingual content to stack instead of side-by-side, see TextContent below.
            // `Modifier.blur` fades a layer's own edge to transparent, so blurring the band
            // itself let whatever is behind — the default lower third's own color — show through
            // along the band's top as a hairline the width of the blur.
            // The fill is drawn larger than the band and the band clips it, so the fade
            // `Modifier.blur` leaves around a layer's own edge falls out of sight. Grown rather
            // than scaled: the picture is cropped from a slightly larger rectangle instead of
            // being stretched, which a band is wide enough to show.
            val bandBleed = if (resolvedBg.isBlurred) blurRadius * BLUR_EDGE_BLEED else 0.dp
            // Read out here: the band Box's own scope shadows this one.
            val bandFillWidth = maxWidth + bandBleed * 2
            val bandFillHeight = maxHeight * lowerThirdFraction + bandBleed * 2
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(lowerThirdFraction)
                    .align(Alignment.BottomCenter)
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .requiredSize(width = bandFillWidth, height = bandFillHeight)
                        .then(if (resolvedBg.isBlurred) Modifier.blur(blurRadius) else Modifier)
                        .then(if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) Modifier else bgModifier)
                ) {
                    if (resolvedBg.type == Constants.BACKGROUND_IMAGE && backgroundImageBitmap != null) {
                        Image(
                            painter = BitmapPainter(backgroundImageBitmap),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            // Cropped from the middle of the picture, not its bottom edge. Scaled
                            // to the band's width a photo is several times the band's height, so
                            // anchoring it to the bottom showed the strip below the subject — the
                            // desk under a photo of someone reading — and never the photo itself.
                            // The Background tab's preview crops from the center; this is what
                            // makes the two agree.
                            alignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize().alpha(effectiveOpacity)
                        )
                    }
                    if (useVideoBackground) {
                        LoopingVideoBackground(
                            videoPath = resolvedBg.videoPath,
                            modifier = Modifier.fillMaxSize().alpha(effectiveOpacity),
                        )
                    }
                }
            }
            if (resolvedBg.dimPercent > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(lowerThirdFraction)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = resolvedBg.dimPercent / PERCENT))
                )
            }
            // Gradient overlay
            if (bgConfig.gradientEnabled) {
                val gradientTop = parseHexColor(bgConfig.gradientTopColor).copy(alpha = bgConfig.gradientTopOpacity)
                val gradientBottom = parseHexColor(bgConfig.gradientBottomColor).copy(alpha = bgConfig.gradientBottomOpacity)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(lowerThirdFraction)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to gradientTop,
                                    bgConfig.gradientPosition to gradientBottom,
                                    1.0f to gradientBottom
                                )
                            )
                        )
                )
            }
        }

        // Outer box for padding/alignment — not animated
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = leftOffSet, end = rightOffSet, top = topOffSet, bottom = bottomOffSet),
            contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
        ) {
            val innerModifier = if (isLowerThird)
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(appSettings.bibleSettings.lowerThirdHeightPercent / 100f)
                    .align(Alignment.BottomCenter)
                    .clipToBounds()
            else
                Modifier.align(contentAlignment)

            val textMeasurer = rememberTextMeasurer()


            // Only animate the text content — background is never inside this block
            @Composable
            fun TextContent(verses: List<SelectedVerse>) {
                val primary = verses.first()
                val secondary = verses.getOrNull(1)
                // A settings file that names only a secondary bible still means "bilingual" -- the
                // condition this replaced keyed off exactly that, and dropping it stopped the second
                // language rendering for those files.
                // The second clause looks redundant — `withTranslations` keeps the legacy name in step
                // with the stack — but it is what covers a legacy file whose secondary is configured
                // and whose primary is not, where the stack collapses to one entry. It does mean a
                // hand-edited file with a stale `secondaryBible` can admit a second language this
                // output never asked for; that is a narrower problem than dropping one it did.
                val isParallelIntended = translationStack.size > 1 || bs.secondaryBible.isNotEmpty()
                val showParallelLayout = isParallelIntended && secondary != null && (!isLowerThird || t1.lowerThirdEnabled)
                val showSecondary = secondary != null && showParallelLayout

                // Full screen always draws the ordered stack, however many translations there are:
                // each line reads its own style profile and a shared fit scale keeps the whole stack
                // on screen. The lower third keeps its own one/two-language layouts below, because a
                // narrow band cannot usefully hold more than a couple of languages anyway.
                if (!isLowerThird) {
                    val configured = translationStack
                    // Only draw as many languages as this machine is set up for. A verse can arrive
                    // carrying a second translation -- from a linked instance -- while nothing here
                    // is configured to show one, and it must not appear unasked.
                    val allowed = if (isParallelIntended) maxOf(configured.size, 2) else 1
                    val visible = verses.take(allowed).mapIndexedNotNull { index, verse ->
                        val style = configured.firstOrNull { it.fileName == verse.translationFileName }
                            ?: configured.getOrNull(index)
                            ?: BibleTranslationSettings(fileName = verse.translationFileName)
                        verse to style
                    }
                    // Fills the frame rather than hugging its text: each translation gets an equal
                    // band of the height and is aligned inside it, which is what the 50/50 split this
                    // replaced did for two. Hugging left every translation bunched against the
                    // configured alignment with the whole remainder as one empty strip -- with two
                    // bibles and the default bottom alignment, an empty top half that read as a
                    // section of its own.
                    //
                    // Clipped, unlike the single-language paths that never needed it: the fit search
                    // below is measured against the bands, so anything it cannot get under would
                    // otherwise draw through the configured margins and off the output.
                    BoxWithConstraints(modifier = innerModifier.fillMaxSize().clipToBounds()) {
                        // How the stack is arranged. Both arrangements were hardcoded here until
                        // `BibleSettings` grew the two `bilingualLayout` fields; their defaults are what this
                        // path already drew, so nothing moves until an operator picks the other option.
                        val sideBySide = bs.bilingualLayout == Constants.BILINGUAL_SIDE_BY_SIDE
                        val slots = visible.size.coerceAtLeast(1)
                        // The spacing between two translations plus the divider's own line, along whichever
                        // axis they are laid out on. It scales with the fit, so every scale the search probes
                        // has to recompute it.
                        fun gapsPx(scale: Float): Int {
                            val gapCount = (visible.size - 1).coerceAtLeast(0)
                            val gap = with(density) { (bs.multiTranslationSpacing * scale).dp.roundToPx() }
                            val divider = if (bs.multiTranslationDivider) {
                                with(density) { 1.dp.roundToPx() }
                            } else {
                                0
                            }
                            return gapCount * (gap + divider)
                        }
                        // Side by side, a translation gets a column of the width and the whole height; stacked,
                        // the whole width and a band of the height. The measurement below has to agree with
                        // whichever it is, or the fit search solves for a box the text is not drawn in.
                        fun itemWidth(scale: Float): Int =
                            if (sideBySide) {
                                ((constraints.maxWidth - gapsPx(scale)) / slots).coerceAtLeast(1)
                            } else {
                                constraints.maxWidth
                            }
                        fun alignment(value: String) = when (value) {
                            Constants.LEFT -> TextAlign.Start
                            Constants.RIGHT -> TextAlign.End
                            else -> TextAlign.Center
                        }
                        fun textStyle(item: BibleTranslationSettings): TextStyle {
                            val shadowEnabled = item.textShadow
                            val shadow = if (shadowEnabled) scaleElementShadow(
                                item.textShadowColor,
                                item.textShadowSize,
                                item.textShadowOpacity,
                            ) else null
                            return TextStyle(
                                fontWeight = if (item.textBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (item.textItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = combinedTextDecoration(item.textUnderline, item.textStrikethrough),
                                letterSpacing = spacingEm(item.textLetterSpacing, item.textFontSize).em,
                                shadow = shadow,
                            )
                        }
                        fun referenceStyle(item: BibleTranslationSettings): TextStyle {
                            val shadowEnabled = item.referenceShadow
                            val shadow = if (shadowEnabled) scaleElementShadow(
                                item.referenceShadowColor,
                                item.referenceShadowSize,
                                item.referenceShadowOpacity,
                            ) else null
                            return TextStyle(
                                fontWeight = if (item.referenceBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (item.referenceItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = combinedTextDecoration(
                                    item.referenceUnderline,
                                    item.referenceStrikethrough,
                                ),
                                letterSpacing = spacingEm(item.referenceLetterSpacing, item.referenceFontSize).em,
                                shadow = shadow,
                            )
                        }
                        // The stack's equivalent of the pText/prText helpers above: this path styles
                        // every translation from its own profile rather than from t0/t1, so the
                        // transform and word spacing have to be read per item.
                        fun itemText(item: BibleTranslationSettings, raw: String) = styledDisplayText(
                            raw,
                            item.textTransform,
                            spacingEm(item.textLetterSpacing, item.textFontSize),
                            spacingEm(item.textWordSpacing, item.textFontSize),
                        )
                        fun itemRefText(item: BibleTranslationSettings, raw: String) = styledDisplayText(
                            raw,
                            item.referenceTransform,
                            spacingEm(item.referenceLetterSpacing, item.referenceFontSize),
                            spacingEm(item.referenceWordSpacing, item.referenceFontSize),
                        )
                        fun blockHeight(verse: SelectedVerse, item: BibleTranslationSettings, scale: Float): Int {
                            val textSize = (item.textFontSize * scaleFactor * scale).sp
                            val refSize = (item.referenceFontSize * scaleFactor * scale).sp
                            val textFont = systemFontFamilyOrDefault(item.textFontType)
                            val refFont = systemFontFamilyOrDefault(item.referenceFontType)
                            val widthConstraint = Constraints(maxWidth = itemWidth(scale))
                            return textMeasurer.measure(
                                itemText(item, verse.verseText),
                                textStyle(item).copy(fontFamily = textFont, fontSize = textSize),
                                constraints = widthConstraint,
                            ).size.height +
                                textMeasurer.measure(
                                    itemRefText(item, buildRefText(verse, item)),
                                    referenceStyle(item).copy(fontFamily = refFont, fontSize = refSize),
                                    constraints = widthConstraint,
                                ).size.height
                        }
                        // What one translation has to fit in. Stacked that is the frame less the gaps split
                        // evenly; side by side the gaps come out of the width instead, so each column keeps the
                        // whole height.
                        fun bandHeight(scale: Float): Int =
                            if (sideBySide) {
                                constraints.maxHeight
                            } else {
                                (constraints.maxHeight - gapsPx(scale)) / slots
                            }
                        // One scale for the whole stack, so every translation reads at the same size,
                        // and no floor: a full stack of six shrinks until the whole of every one of them
                        // is inside its band. Everything measured here scales with the argument bar the
                        // 1dp dividers, so a fitting scale always exists to be found.
                        //
                        // Per band rather than against the total, now that each has its own: a long
                        // verse can no longer borrow the slack of a short one beside it and push its
                        // own band's text out through the clip.
                        fun everyBlockFits(scale: Float): Boolean {
                            val band = bandHeight(scale)
                            return visible.all { (verse, item) -> blockHeight(verse, item, scale) <= band }
                        }
                        // No full-size gate in front of the search: its own opening probe is that same
                        // measurement and returns 1f when it fits, so gating here measured every
                        // translation twice over.
                        val fitScale = binarySearchFitScale(iterations = 10) { scale -> everyBlockFits(scale) }
                        // The type of one translation, laid out inside whatever slot the container gives it.
                        // Hoisted so the two containers below share it: they differ only in the axis they lay
                        // their slots out on and in the divider that separates them, never in the text.
                        val translationBlock: @Composable (SelectedVerse, BibleTranslationSettings) -> Unit =
                            { verse, item ->
                                val textSize = (item.textFontSize * scaleFactor * fitScale).sp
                                val refSize = (item.referenceFontSize * scaleFactor * fitScale).sp
                                val textFont = systemFontFamilyOrDefault(item.textFontType)
                                val refFont = systemFontFamilyOrDefault(item.referenceFontType)
                                val textColor = if (isKey) Color.White else parseHexColor(item.textColor)
                                val refColor = if (isKey) Color.White else parseHexColor(item.referenceColor)
                                val textAlign = alignment(item.textHorizontalAlignment)
                                val refAlign = alignment(item.referenceHorizontalAlignment)
                                val refPosition = item.referencePosition
                                // Per translation, not per profile: every one in the stack draws
                                // from its own settings, so each needs a painter of its own.
                                val itemTextPainter =
                                    rememberTextBackdropPainter(item.textBackdropFor(isLowerThird))
                                val itemRefPainter =
                                    rememberTextBackdropPainter(item.referenceBackdropFor(isLowerThird))
                                Column(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                                    if (refPosition == Constants.POSITION_ABOVE) {
                                        Text(
                                            itemRefText(item, buildRefText(verse, item)),
                                            Modifier.fillMaxWidth().then(itemRefPainter.modifier),
                                            color = refColor,
                                            fontFamily = refFont,
                                            fontSize = refSize,
                                            textAlign = refAlign,
                                            style = referenceStyle(item),
                                            onTextLayout = itemRefPainter::onTextLayout,
                                        )
                                    }
                                    Text(
                                        itemText(item, verse.verseText),
                                        Modifier.fillMaxWidth().then(itemTextPainter.modifier),
                                        color = textColor,
                                        fontFamily = textFont,
                                        fontSize = textSize,
                                        textAlign = textAlign,
                                        style = textStyle(item),
                                        onTextLayout = itemTextPainter::onTextLayout,
                                    )
                                    if (refPosition == Constants.POSITION_BELOW) {
                                        Text(
                                            itemRefText(item, buildRefText(verse, item)),
                                            Modifier.fillMaxWidth().then(itemRefPainter.modifier),
                                            color = refColor,
                                            fontFamily = refFont,
                                            fontSize = refSize,
                                            textAlign = refAlign,
                                            style = referenceStyle(item),
                                            onTextLayout = itemRefPainter::onTextLayout,
                                        )
                                    }
                                }
                            }
                        val dividerColor = if (isKey) Color.White else Color.White.copy(alpha = DIVIDER_ALPHA)
                        // Half the spacing either side of the divider, so the rule sits on the centre line of
                        // the gap whether or not it is drawn -- as it did when this was one Column.
                        val halfGap = (bs.multiTranslationSpacing * fitScale / 2f).dp
                        if (sideBySide) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                visible.forEachIndexed { index, (verse, item) ->
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxHeight().clipToBounds(),
                                        contentAlignment = contentAlignment,
                                    ) {
                                        translationBlock(verse, item)
                                    }
                                    if (index < visible.lastIndex) {
                                        Spacer(modifier = Modifier.width(halfGap))
                                        if (bs.multiTranslationDivider) {
                                            VerticalDivider(color = dividerColor, thickness = 1.dp)
                                        }
                                        Spacer(modifier = Modifier.width(halfGap))
                                    }
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                visible.forEachIndexed { index, (verse, item) ->
                                    Box(
                                        modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                                        contentAlignment = contentAlignment,
                                    ) {
                                        translationBlock(verse, item)
                                    }
                                    if (index < visible.lastIndex) {
                                        Spacer(modifier = Modifier.height(halfGap))
                                        if (bs.multiTranslationDivider) {
                                            HorizontalDivider(color = dividerColor, thickness = 1.dp)
                                        }
                                        Spacer(modifier = Modifier.height(halfGap))
                                    }
                                }
                            }
                        }
                    }
                    return
                }

                // isLowerThirdVertical forces bilingual/parallel content to stack (one below the
                // other) instead of the side-by-side Row split below — same band/geometry as
                // horizontal otherwise, see the routing to the single-column "else" branch.
                //
                // `bilingualLayoutLowerThird` now takes the same route by choice rather than by
                // shape: Top/Bottom on a horizontal band falls through to that same stacked branch,
                // which is why making the band stack needed no second layout written for it. A
                // vertical strip still stacks whatever the setting says — it has no width to split.
                // A band splits across its width only when it has a width to split: a vertical strip
                // and a Top/Bottom choice both send it to the stacked branch instead.
                val bandSplits = bs.bilingualLayoutLowerThird == Constants.BILINGUAL_SIDE_BY_SIDE &&
                    !isLowerThirdVertical
                if (showParallelLayout && isLowerThird && bandSplits) {
                    val sec = secondary
                    // Lower third: side-by-side Row layout (50/50) with matched auto-fit
                    BoxWithConstraints(
                        modifier = innerModifier,
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // Pre-compute fit scales for both halves, then use the min so fonts match
                        val halfWidth = (constraints.maxWidth - with(density) { 16.dp.roundToPx() }) / 2
                        val halfConstraint = Constraints(maxWidth = halfWidth.coerceAtLeast(1))
                        // Use 90% of available height as safety margin for line spacing/shadow/padding offsets
                        val availH = (constraints.maxHeight * 0.90f).toInt()

                        val primaryRefText = buildRefText(primary, t0)
                        val secondaryRefText = buildRefText(sec, t1)

                        // Binary search for the largest scale where both primary and secondary fit
                        // Scale both verse AND reference text together so everything shrinks proportionally
                        val initialPRefH = textMeasurer.measure(
                            prText(primaryRefText),
                            primaryReferenceTextStyle.copy(
                                fontFamily = primaryBibleReferenceFontStyle,
                                fontSize = scaledPrimaryReferenceSize,
                            ),
                            constraints = halfConstraint,
                        ).size.height
                        val initialSRefH = textMeasurer.measure(
                            srText(secondaryRefText),
                            secondaryReferenceTextStyle.copy(
                                fontFamily = secondaryBibleReferenceFontStyle,
                                fontSize = scaledSecondaryReferenceSize,
                            ),
                            constraints = halfConstraint,
                        ).size.height
                        val initialPH = textMeasurer.measure(
                            pText(primary.verseText),
                            primaryBibleTextStyle.copy(
                                fontFamily = primaryBibleFontStyle,
                                fontSize = scaledPrimaryBibleSize,
                            ),
                            constraints = halfConstraint,
                        ).size.height
                        val initialSH = textMeasurer.measure(
                            sText(sec.verseText),
                            secondaryBibleTextStyle.copy(
                                fontFamily = secondaryBibleFontStyle,
                                fontSize = scaledSecondaryBibleSize,
                            ),
                            constraints = halfConstraint,
                        ).size.height
                        val needsScaling = (initialPRefH + initialPH > availH) || (initialSRefH + initialSH > availH)

                        val matchedScale = if (needsScaling) {
                            binarySearchFitScale { scale ->
                                val pRefH = textMeasurer.measure(
                                    prText(primaryRefText),
                                    primaryReferenceTextStyle.copy(
                                        fontFamily = primaryBibleReferenceFontStyle,
                                        fontSize = scaledPrimaryReferenceSize * scale,
                                    ),
                                    constraints = halfConstraint,
                                ).size.height
                                val sRefH = textMeasurer.measure(
                                    srText(secondaryRefText),
                                    secondaryReferenceTextStyle.copy(
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryReferenceSize * scale,
                                    ),
                                    constraints = halfConstraint,
                                ).size.height
                                val pH = textMeasurer.measure(
                                    pText(primary.verseText),
                                    primaryBibleTextStyle.copy(
                                        fontFamily = primaryBibleFontStyle,
                                        fontSize = scaledPrimaryBibleSize * scale,
                                    ),
                                    constraints = halfConstraint,
                                ).size.height
                                val sH = textMeasurer.measure(
                                    sText(sec.verseText),
                                    secondaryBibleTextStyle.copy(
                                        fontFamily = secondaryBibleFontStyle,
                                        fontSize = scaledSecondaryBibleSize * scale,
                                    ),
                                    constraints = halfConstraint,
                                ).size.height
                                (pRefH + pH <= availH) && (sRefH + sH <= availH)
                            }
                        } else 1f
                        val pBibleSize = scaledPrimaryBibleSize * matchedScale
                        val sBibleSize = scaledSecondaryBibleSize * matchedScale
                        // Use the smaller of the two so both sides display at the same visual size
                        val matchedBibleSize = if (sBibleSize.value < pBibleSize.value) sBibleSize else pBibleSize
                        val scaledPrimaryRefSize = scaledPrimaryReferenceSize * matchedScale
                        val scaledSecondaryRefSize = scaledSecondaryReferenceSize * matchedScale

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left half: primary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(pRefPainter.modifier),
                                        textAlign = primaryBibleReferenceHorizontalAlignment,
                                        fontFamily = primaryBibleReferenceFontStyle,
                                        fontSize = scaledPrimaryRefSize,
                                        text = prText(primaryRefText),
                                        color = primaryBibleReferenceTextColor,
                                        style = primaryReferenceTextStyleScaled,
                                        onTextLayout = pRefPainter::onTextLayout,
                                    )
                                }
                                Text(
                                    modifier = Modifier.fillMaxWidth().then(pTextPainter.modifier),
                                    textAlign = primaryBibleHorizontalAlignment,
                                    fontFamily = primaryBibleFontStyle,
                                    fontSize = matchedBibleSize,
                                    text = pText(primary.verseText),
                                    color = primaryBibleTextColor,
                                    style = primaryBibleTextStyleScaled,
                                    onTextLayout = pTextPainter::onTextLayout,
                                )
                                if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(pRefPainter.modifier),
                                        textAlign = primaryBibleReferenceHorizontalAlignment,
                                        fontFamily = primaryBibleReferenceFontStyle,
                                        fontSize = scaledPrimaryRefSize,
                                        text = prText(primaryRefText),
                                        color = primaryBibleReferenceTextColor,
                                        style = primaryReferenceTextStyleScaled,
                                        onTextLayout = pRefPainter::onTextLayout,
                                    )
                                }
                            }
                            // Right half: secondary bible
                            Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.Bottom)) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(sRefPainter.modifier),
                                        textAlign = secondaryBibleReferenceHorizontalAlignment,
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryRefSize,
                                        text = srText(secondaryRefText),
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyleScaled,
                                        onTextLayout = sRefPainter::onTextLayout,
                                    )
                                }
                                Text(
                                    modifier = Modifier.fillMaxWidth().then(sTextPainter.modifier),
                                    textAlign = secondaryBibleHorizontalAlignment,
                                    fontFamily = secondaryBibleFontStyle,
                                    fontSize = matchedBibleSize,
                                    text = sText(sec.verseText),
                                    color = secondaryBibleTextColor,
                                    style = secondaryBibleTextStyleScaled,
                                    onTextLayout = sTextPainter::onTextLayout,
                                )
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(sRefPainter.modifier),
                                        textAlign = secondaryBibleReferenceHorizontalAlignment,
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryRefSize,
                                        text = srText(secondaryRefText),
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyleScaled,
                                        onTextLayout = sRefPainter::onTextLayout,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Single-column layout: the lower third, in either orientation, with or without a
                    // second language. Full screen never reaches here -- the stack above returns for
                    // every !isLowerThird case, whatever the stack holds.
                    BoxWithConstraints(
                        modifier = innerModifier,
                        contentAlignment = if (isLowerThird) Alignment.BottomCenter else contentAlignment
                    ) {
                        // Auto-scale bible text if it overflows the available height
                        val widthConstraint = Constraints(maxWidth = constraints.maxWidth)
                        val primaryRefText = buildRefText(primary, t0)
                        // Empty when there is no second language, which is the same as the zero height
                        // that branch contributes.
                        val secondaryRefText = secondary?.let { buildRefText(it, t1) } ?: ""

                        val maxH = constraints.maxHeight
                        // The reference lines scale with the verse rather than staying at full size.
                        // Held fixed, they were a floor the search could not get under: a band whose
                        // references alone overfill it had no fitting scale to find, so the text ran off
                        // the bottom however far the verse shrank. This is also the lower third's path.
                        //
                        // Handed straight to the search rather than gated on a full-size measurement
                        // first: the search's own opening probe is that same measurement and returns 1f
                        // when it fits, so a gate here only measured the whole passage twice.
                        val fitScale = binarySearchFitScale { scale ->
                            val pRefH = textMeasurer.measure(
                                prText(primaryRefText),
                                primaryReferenceTextStyle.copy(
                                    fontFamily = primaryBibleReferenceFontStyle,
                                    fontSize = scaledPrimaryReferenceSize * scale,
                                ),
                                constraints = widthConstraint,
                            ).size.height
                            val pH = textMeasurer.measure(
                                pText(primary.verseText),
                                primaryBibleTextStyle.copy(
                                    fontFamily = primaryBibleFontStyle,
                                    fontSize = scaledPrimaryBibleSize * scale,
                                ),
                                constraints = widthConstraint,
                            ).size.height
                            val sRefH = if (showSecondary) {
                                textMeasurer.measure(
                                    srText(secondaryRefText),
                                    secondaryReferenceTextStyle.copy(
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = scaledSecondaryReferenceSize * scale,
                                    ),
                                    constraints = widthConstraint,
                                ).size.height
                            } else 0
                            val sH = if (showSecondary) {
                                textMeasurer.measure(
                                    sText(secondary.verseText),
                                    secondaryBibleTextStyle.copy(
                                        fontFamily = secondaryBibleFontStyle,
                                        fontSize = scaledSecondaryBibleSize * scale,
                                    ),
                                    constraints = widthConstraint,
                                ).size.height
                            } else 0
                            pRefH + pH + sRefH + sH <= maxH
                        }
                        val fittedPrimaryRefSize = scaledPrimaryReferenceSize * fitScale
                        val fittedSecondaryRefSize = scaledSecondaryReferenceSize * fitScale
                        val fittedPrimaryBibleSize = scaledPrimaryBibleSize * fitScale
                        val fittedSecondaryBibleSize = scaledSecondaryBibleSize * fitScale
                        // Use the smaller so both primary and secondary display at the same visual size
                        val matchedFittedSize = if (showSecondary && fittedSecondaryBibleSize.value < fittedPrimaryBibleSize.value) fittedSecondaryBibleSize else fittedPrimaryBibleSize

                        Column(
                            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                            verticalArrangement = if (isLowerThird) Arrangement.Bottom else Arrangement.Top
                        ) {
                            if (primaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                Text(
                                    modifier = Modifier.fillMaxWidth().then(pRefPainter.modifier),
                                    textAlign = primaryBibleReferenceHorizontalAlignment,
                                    fontFamily = primaryBibleReferenceFontStyle,
                                    fontSize = fittedPrimaryRefSize,
                                    text = prText(buildRefText(primary, t0)),
                                    color = primaryBibleReferenceTextColor,
                                    style = primaryReferenceTextStyleScaled,
                                    onTextLayout = pRefPainter::onTextLayout,
                                )
                            }
                            Text(
                                modifier = Modifier.fillMaxWidth().then(pTextPainter.modifier),
                                textAlign = primaryBibleHorizontalAlignment,
                                fontFamily = primaryBibleFontStyle,
                                fontSize = matchedFittedSize,
                                text = pText(primary.verseText),
                                color = primaryBibleTextColor,
                                style = primaryBibleTextStyleScaled,
                                onTextLayout = pTextPainter::onTextLayout,
                            )
                            if (primaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                Text(
                                    modifier = Modifier.fillMaxWidth().then(pRefPainter.modifier),
                                    textAlign = primaryBibleReferenceHorizontalAlignment,
                                    fontFamily = primaryBibleReferenceFontStyle,
                                    fontSize = fittedPrimaryRefSize,
                                    text = prText(buildRefText(primary, t0)),
                                    color = primaryBibleReferenceTextColor,
                                    style = primaryReferenceTextStyleScaled,
                                    onTextLayout = pRefPainter::onTextLayout,
                                )
                            }
                            if (showSecondary) {
                                if (secondaryBibleReferencePosition == Constants.POSITION_ABOVE) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(sRefPainter.modifier),
                                        textAlign = secondaryBibleReferenceHorizontalAlignment,
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = fittedSecondaryRefSize,
                                        text = srText(buildRefText(secondary, t1)),
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyleScaled,
                                        onTextLayout = sRefPainter::onTextLayout,
                                    )
                                }
                                Text(
                                    modifier = Modifier.fillMaxWidth().then(sTextPainter.modifier),
                                    textAlign = secondaryBibleHorizontalAlignment,
                                    fontFamily = secondaryBibleFontStyle,
                                    fontSize = matchedFittedSize,
                                    text = sText(secondary.verseText),
                                    color = secondaryBibleTextColor,
                                    style = secondaryBibleTextStyleScaled,
                                    onTextLayout = sTextPainter::onTextLayout,
                                )
                                if (secondaryBibleReferencePosition == Constants.POSITION_BELOW) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth().then(sRefPainter.modifier),
                                        textAlign = secondaryBibleReferenceHorizontalAlignment,
                                        fontFamily = secondaryBibleReferenceFontStyle,
                                        fontSize = fittedSecondaryRefSize,
                                        text = srText(buildRefText(secondary, t1)),
                                        color = secondaryBibleReferenceTextColor,
                                        style = secondaryReferenceTextStyleScaled,
                                        onTextLayout = sRefPainter::onTextLayout,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (crossfadeEnabled || appSettings.bibleSettings.fadeIn || appSettings.bibleSettings.fadeOut) {
                // Transition system:
                // - Fade in: first appearance fades from transparent
                // - Crossfade: switching verses blends old/new simultaneously
                // - Fade out: handled externally when clearing display
                val duration = bs.transitionDuration.toInt().coerceAtLeast(100)
                val isCrossfade = crossfadeEnabled
                var displayedCurrent by remember { mutableStateOf(effectiveVerses) }
                var displayedPrevious by remember { mutableStateOf<List<SelectedVerse>>(emptyList()) }
                var currentAlpha by remember { mutableStateOf(1f) }
                var previousAlpha by remember { mutableStateOf(0f) }
                val pendingQueue = remember { kotlinx.coroutines.channels.Channel<List<SelectedVerse>>(kotlinx.coroutines.channels.Channel.CONFLATED) }

                // Queue verse changes
                LaunchedEffect(effectiveVerses) {
                    if (displayedCurrent != effectiveVerses) {
                        pendingQueue.send(effectiveVerses)
                    }
                }

                // Process verse switches (crossfade between verses)
                LaunchedEffect(Unit) {
                    for (nextVerses in pendingQueue) {
                        if (displayedCurrent == nextVerses) continue

                        if (isCrossfade) {
                            // Crossfade: both layers animate simultaneously
                            displayedPrevious = displayedCurrent
                            displayedCurrent = nextVerses
                            previousAlpha = 1f
                            currentAlpha = 0f
                            val anim = Animatable(0f)
                            anim.animateTo(1f, tween(durationMillis = duration)) {
                                currentAlpha = this.value
                                previousAlpha = 1f - this.value
                            }
                        } else {
                            // No crossfade — just swap instantly
                            displayedCurrent = nextVerses
                        }
                        currentAlpha = 1f
                        previousAlpha = 0f
                        displayedPrevious = emptyList()
                    }
                }

                // transitionAlpha handles fade out (driven from main.kt when clearing display)
                Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = transitionAlpha }) {
                    if (displayedPrevious.isNotEmpty() && previousAlpha > 0f) {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = previousAlpha }) {
                            TextContent(displayedPrevious)
                        }
                    }
                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = currentAlpha }) {
                        TextContent(displayedCurrent)
                    }
                }
            } else {
                Box(modifier = Modifier.graphicsLayer { alpha = transitionAlpha }) {
                    TextContent(effectiveVerses)
                }
            }
        }
    }
}
