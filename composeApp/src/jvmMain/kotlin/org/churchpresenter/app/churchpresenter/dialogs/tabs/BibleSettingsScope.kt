package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_scope_full_screen
import churchpresenter.composeapp.generated.resources.bible_scope_lower_third
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The basis a stored font size is expressed against.
 *
 * `BiblePresenter` scales every size by `min(widthPx / 1920, heightPx / 1080)` on whatever output it
 * draws, so "70" means "70 at 1920x1080" and auto-fit has to measure against that -- fitting against
 * a 4K panel's real pixels would hand back a size the presenter then scales up again.
 */
internal const val STYLING_BASIS_WIDTH = 1920
internal const val STYLING_BASIS_HEIGHT = 1080

/** The lower third's height is stored as a whole percentage of the output. */
private const val PERCENT = 100

/** What the output being styled actually is, in its own pixels. */
@Composable
internal fun scopeNote(settings: AppSettings, target: BibleStyleTarget): String {
    val size = previewOutputSize(settings)
    return if (target.isLowerThird) {
        stringResource(
            Res.string.bible_scope_lower_third,
            size.width,
            size.height * settings.projectionSettings.lowerThirdHeightPercent / PERCENT,
        )
    } else {
        stringResource(Res.string.bible_scope_full_screen, size.width, size.height)
    }
}

/**
 * The largest size at which [verse] still fits the output it is being styled for.
 *
 * A one-shot measurement written into the size field, not a stored mode: the operator asks for it
 * against the verse that is live, and can then edit the number it produced. The reference is
 * measured at its own configured size and its height taken off the box first, because the two are
 * drawn one above the other.
 */
internal fun autoFitFontSize(
    textMeasurer: TextMeasurer,
    settings: AppSettings,
    verses: List<SelectedVerse>,
    translation: BibleTranslationSettings,
    target: BibleStyleTarget,
): Int {
    val verse = verses.first()
    val style = translation.elementStyle(BibleStyleElement.TEXT, target)
    val referenceStyle = translation.elementStyle(BibleStyleElement.REFERENCE, target)
    // A second language live halves the full-screen height each one gets.
    val parallel = verses.size > 1
    val projection = settings.projectionSettings
    val bible = settings.bibleSettings
    // Measured against the basis the sizes are stored in, not the display's real resolution -- the
    // presenter fits text against that same basis, so fitting against 3840x2160 would hand back a
    // size the output then scales up again.
    val availableWidth = STYLING_BASIS_WIDTH - projection.windowLeft - projection.windowRight -
        bible.marginLeft - bible.marginRight
    val availableHeight = STYLING_BASIS_HEIGHT - projection.windowTop - projection.windowBottom -
        bible.marginTop - bible.marginBottom
    val baseStyle = TextStyle(
        fontFamily = systemFontFamilyOrDefault(style.fontType),
        fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
    )
    val referenceTextStyle = TextStyle(
        fontFamily = systemFontFamilyOrDefault(referenceStyle.fontType),
        fontWeight = if (referenceStyle.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (referenceStyle.italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = referenceStyle.fontSize.sp,
    )
    val referenceText = "${verse.bookName} ${verse.chapter}:${verse.verseNumber}"
    val referenceHeight = textMeasurer
        .measure(referenceText, referenceTextStyle, density = Density(1f)).size.height
    // Full screen splits the height between two languages when a second one is live; the lower
    // third gets whatever fraction of the output its band is configured to take.
    val boxHeight = if (target.isLowerThird) {
        availableHeight * projection.lowerThirdHeightPercent / 100
    } else if (parallel) {
        availableHeight / 2
    } else {
        availableHeight
    }
    return calculateAutoFitFontSize(
        textMeasurer,
        verse.verseText,
        baseStyle,
        availableWidth,
        boxHeight - referenceHeight,
    )
}
