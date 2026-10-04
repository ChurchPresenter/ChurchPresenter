package org.churchpresenter.presenter

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ContentRegion
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.utils.Constants

internal const val BIBLE_SHADOW_OFFSET_PX = 6f

/**
 * The most translations a band will draw: the whole stack, as the full screen does.
 *
 * This was 4 on the reasoning that a narrow band has room for a 2x2 at most. That is a judgement
 * about legibility rather than a limit the layout has, and it was made where nothing could see it:
 * a stack of six on a band drew four and dropped two with nothing on screen saying so. The auto-fit
 * below already shrinks whatever it is given until every translation is inside its own cell, so a
 * band asked for six shows six -- small, and the operator can see that it is small and choose
 * differently.
 */
internal const val MAX_BIBLE_BAND_TRANSLATIONS = Constants.MAX_BIBLE_TRANSLATIONS

/** The gap between the lower third grid's rows and its columns, in the 1920x1080 reference space. */
internal const val BIBLE_LOWER_THIRD_GRID_GAP_DP = 12

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
internal const val BIBLE_DIVIDER_ALPHA = 0.45f

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

/** The stroke around the verse's glyphs, per output; [referenceOutlineFor] is its reference twin. */
internal fun BibleTranslationSettings.textOutlineFor(lowerThird: Boolean): TextOutline =
    if (lowerThird) lowerThirdTextOutline else textOutline

internal fun BibleTranslationSettings.referenceOutlineFor(lowerThird: Boolean): TextOutline =
    if (lowerThird) lowerThirdReferenceOutline else referenceOutline

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
    /** Full screen: the region the text alone is placed in, the background filling the screen -- see [textOnly]. */
    textRegion: ContentRegion? = null,
) {
    val look = BibleLook(
        selectedVerses = selectedVerses,
        appSettings = appSettings,
        isLowerThird = isLowerThird,
        isLowerThirdVertical = isLowerThirdVertical,
        outputRole = outputRole,
        transitionAlpha = transitionAlpha,
        showBackground = showBackground,
        crossfadeEnabled = crossfadeEnabled,
        bibleTranslations = bibleTranslations,
    )
    with(look) {
        // A Lottie band draws the whole band itself — text included — so it replaces everything
        // below rather than sitting under it. A file that is missing or is not a template falls
        // through to the classic band, the same way a missing picture falls back to the color.
        if (isLowerThird && usesBibleLottieBand(bgConfig)) {
            val template by rememberBibleLottieTemplate(bgConfig.backgroundLottie)
            val loaded = template
            if (loaded != null) {
                BibleLottieBandLayer(loaded, modifier)
                return
            }
        }

        // The Lottie band draws its own text and plays from the band clock alone, not from a verse on
        // screen -- so it has to be reachable even with nothing selected yet, or for the one frame a
        // switch passes through before `effectiveVerses` catches up. Reached only above; below this the
        // classic band and the full screen both need a verse to draw at all.
        effectiveVerses.firstOrNull() ?: return
        val style = BibleStyle(look, rememberBibleFonts(), rememberBibleColors(isKey), rememberBiblePainters())

        // A verse carries no background of its own, so this is the quick tray's pick, then the Bible
        // background, then the defaults — the same order and the same resolver songs go through.
        val resolvedBg = resolveBackground(
            settings = appSettings.backgroundSettings,
            config = bgConfig,
            isLowerThird = isLowerThird,
            showBackground = showBackground,
            transparentWhenBlank = LocalTransparentBlanking.current,
        )
        val backdrop = PresenterBackdrop(resolvedBg, rememberBackgroundBitmap(resolvedBg, isLowerThird))

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
                .then(if (!isLowerThird && !resolvedBg.isBlurred) backdrop.bgModifier else Modifier)
        ) {
            val density = LocalDensity.current
            val blurRadius = backgroundBlurRadius(resolvedBg.blurReferencePx, maxWidth)
            PresenterBackgroundLayers(
                background = resolvedBg,
                backgroundModifier = backdrop.bgModifier,
                isLowerThird = isLowerThird,
                blurRadius = blurRadius,
            )
            // Everything but the background, in the region when the background stays full screen.
            TextRegionBox(textRegion) {
                BibleFrameContent(style, backdrop, blurRadius, density)
            }
        }
    }
}

/** A Lottie band draws the whole band itself — text included — so it replaces everything else. */
@Composable
private fun BibleLook.BibleLottieBandLayer(loaded: BibleLottieTemplate, modifier: Modifier) {
    val lowerThirdFraction = appSettings.bibleSettings.lowerThirdHeightPercent / PERCENT
    val above = resolveAboveBand(appSettings.backgroundSettings, bgConfig)
    BoxWithConstraints(modifier.fillMaxSize()) {
        AboveBandFill(
            above = above,
            show = showBackground,
            bandFraction = effectiveBandFraction(
                canvasAspectRatio = maxWidth / maxHeight,
                bandFraction = lowerThirdFraction,
                templateAspectRatio = loaded.width / loaded.height,
            ),
        )
        BibleLottieBand(
            template = loaded,
            verses = effectiveVerses,
            // The outgoing verses go through the same per-output translation filter, so a
            // screen assigned one translation plays out the verse it was actually showing.
            outgoingVerses = versesForOutput(LocalBandOutgoing.current.verses),
            translations = listOf(t0, t1, t2, t3),
            bandFraction = lowerThirdFraction,
            bandClock = LocalLottieBandClock.current,
            isKey = isKey,
            showBackground = showBackground,
        )
    }
}
