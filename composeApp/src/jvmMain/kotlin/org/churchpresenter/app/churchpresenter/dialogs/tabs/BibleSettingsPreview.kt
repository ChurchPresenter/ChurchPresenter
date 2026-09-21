package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_preview_full_screen
import churchpresenter.composeapp.generated.resources.bible_preview_lower_third
import churchpresenter.composeapp.generated.resources.bible_preview_no_translations
import churchpresenter.composeapp.generated.resources.bible_preview_sample_book
import churchpresenter.composeapp.generated.resources.bible_preview_sample_verse
import churchpresenter.composeapp.generated.resources.bible_preview_sample_verse_long
import churchpresenter.composeapp.generated.resources.bible_preview_sample_verse_short
import churchpresenter.composeapp.generated.resources.preview_output_partial_translations_warning
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputWarning
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.presenter.BiblePresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.usesBibleLottieBand
import org.churchpresenter.bible.PreviewVerse
import org.churchpresenter.bible.defaultTranslationAbbreviation
import org.churchpresenter.bible.VerseTarget
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

private const val EMPTY_NOTE_ALPHA = 0.45f

/** John, in the internal numbering every `.spb` is keyed by. */
private const val JOHN = 43

/**
 * The three verses the sample selector switches between, all in John.
 *
 * One book, so [org.churchpresenter.bible.readPreviewVerses] reaches all three in a single forward
 * scan, and the New Testament, so any module with one carries them. The lengths are the point:
 * "Jesus wept." proves auto-fit does not blow two words up past the margins, and John 6:53 proves a
 * long verse still fits the band. A module that has none of them falls back to its own first verse
 * for all three, which is the honest report for an Old-Testament-only shelf.
 */
internal val BIBLE_PREVIEW_TARGETS: Map<PreviewSampleSlot, VerseTarget> = mapOf(
    PreviewSampleSlot.SHORT to VerseTarget(JOHN, 11, 35),
    PreviewSampleSlot.MEDIUM to VerseTarget(JOHN, 3, 16),
    PreviewSampleSlot.LONG to VerseTarget(JOHN, 6, 53),
)

/**
 * What each translation's preview quotes, at [slot]'s length -- the module's own text where it has
 * the verse, and an English sample where it does not.
 *
 * `internal` and separate from the panel because the on-screen preview pushes exactly this list at
 * the real outputs: one definition of "the sample", so the picture in the dialog and the picture on
 * the screen cannot drift apart.
 */
@Composable
internal fun bibleSampleVerses(
    translations: List<BibleTranslationSettings>,
    verses: Map<String, Map<VerseTarget, PreviewVerse>>,
    slot: PreviewSampleSlot,
    /** Each module's own title, keyed by file name -- what an un-renamed abbreviation comes from. */
    moduleTitles: Map<String, String>,
): List<SelectedVerse> {
    val target = BIBLE_PREVIEW_TARGETS.getValue(slot)
    val sampleBook = stringResource(Res.string.bible_preview_sample_book)
    val sampleVerse = stringResource(
        when (slot) {
            PreviewSampleSlot.SHORT -> Res.string.bible_preview_sample_verse_short
            PreviewSampleSlot.MEDIUM -> Res.string.bible_preview_sample_verse
            PreviewSampleSlot.LONG -> Res.string.bible_preview_sample_verse_long
        },
    )
    return translations.map { translation ->
        val verse = verses[translation.fileName]?.get(target)
        val moduleTitle = moduleTitles[translation.fileName].orEmpty()
        SelectedVerse(
            translationFileName = translation.fileName,
            // What a blank abbreviation box falls back to, which is what the presenter draws and
            // what the box offers as its placeholder. Derived from the module title where it has
            // one, and only then from the file name.
            bibleAbbreviation = defaultTranslationAbbreviation(moduleTitle, translation.fileName),
            bookName = verse?.bookName?.takeIf { it.isNotBlank() } ?: sampleBook,
            chapter = verse?.chapter ?: target.chapter,
            verseNumber = verse?.verseNumber ?: target.verse,
            verseText = verse?.text ?: sampleVerse,
        )
    }
}

/**
 * Which output the Bible preview stands for, and the preview itself.
 *
 * Split out of `StylePane` to keep it under the method-length gate: a rig can carry several
 * differently-shaped outputs at once, so the operator says which; the picker draws nothing until
 * there is more than one to choose between.
 *
 * A second warning, below the picker's own, covers a narrower case than "is Bible on at all": the
 * selected output may carry only some of the stack's translations (`ScreenAssignment.bibleTranslations`
 * -- empty means all of them). Comparing that count against the full stack rather than diffing the
 * actual indices: an output naming positions past the end of a stack that has since shrunk is not a
 * disagreement worth a warning, only a real subset is.
 */
@Composable
internal fun BiblePreviewWithOutputPicker(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    target: BibleStyleTarget,
    selectedVerses: List<SelectedVerse>,
) {
    val previewOutput = rememberPreviewOutput(settings, Constants.PREVIEW_TAB_BIBLE, Presenting.BIBLE)
    PreviewOutputPicker(
        settings = settings,
        tabId = Constants.PREVIEW_TAB_BIBLE,
        mode = Presenting.BIBLE,
        onSettingsChange = onSettingsChange,
    )
    val translationCount = settings.bibleSettings.translationList().size
    val shownCount = previewOutput.assignment.bibleTranslations
        .let { if (it.isEmpty()) translationCount else it.count { position -> position in 0 until translationCount } }
    if (translationCount >= 2 && shownCount < translationCount) {
        PreviewOutputWarning(stringResource(Res.string.preview_output_partial_translations_warning))
    }
    // Centred and capped rather than filling the pane: see SETTINGS_PREVIEW_MAX_HEIGHT.
    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BiblePreviewPanel(
            settings = settings,
            target = target,
            output = previewOutput.size,
            selectedVerses = selectedVerses,
            modifier = Modifier.width(
                minOf(maxWidth, SETTINGS_PREVIEW_MAX_HEIGHT * previewOutput.size.aspectRatio),
            ),
        )
    }
}

/**
 * What the configured styling puts on screen -- drawn by [BiblePresenter] itself.
 *
 * This composes the **real presenter** at the output's own size and scales the result down. It does
 * not reproduce the presenter's layout, and that is the point: an earlier version of this file
 * recomputed the scale factor, the margins, the auto-fit and the band arithmetic by hand, and every
 * one of them was a separate opportunity to disagree with the output. They all did, in different
 * ways and on different screens. Rendering the presenter means the preview is right by
 * construction, and stays right when the presenter changes.
 *
 * Backgrounds are left off: the presenter would decode an image or start a video for a picture a
 * few hundred dp wide, and what is being previewed here is the type.
 */
@Composable
internal fun BiblePreviewPanel(
    settings: AppSettings,
    target: BibleStyleTarget,
    /**
     * The output this preview stands for, in its own pixels -- the caller's decision, not this
     * panel's: the global Bible tab asks the operator which output to preview (see
     * `rememberPreviewOutput`/`PreviewOutputPicker`), while a per-output Customize dialog already
     * knows exactly which one it is editing and must not guess a different one.
     */
    output: PreviewOutputSize,
    /** What each translation quotes -- see [bibleSampleVerses]. */
    selectedVerses: List<SelectedVerse>,
    modifier: Modifier = Modifier,
) {
    val bible = settings.bibleSettings
    // Which lower third the outputs are actually set up for: the bottom band and the right-hand
    // strip are different shapes, and previewing the wrong one misreports where the text sits.
    val vertical = settings.projectionSettings.screenAssignments.any { it.isLowerThirdVertical }

    Box(
        modifier = modifier
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
    ) {
        if (selectedVerses.isEmpty()) {
            Text(
                text = stringResource(Res.string.bible_preview_no_translations),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = EMPTY_NOTE_ALPHA),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            ScaledPresenterBox(output) {
                BiblePresenter(
                    selectedVerses = selectedVerses,
                    appSettings = settings,
                    isLowerThird = target.isLowerThird,
                    isLowerThirdVertical = target.isLowerThird && vertical,
                    // A Lottie band is the text, so it has to be drawn; a backdrop is not.
                    showBackground = target.isLowerThird &&
                        usesBibleLottieBand(settings.backgroundSettings.bibleLowerThirdBackground),
                )
            }
        }
        MarginGuide(
            output = output,
            settings = settings,
            margins = PreviewMargins(
                left = bible.marginLeft,
                right = bible.marginRight,
                top = bible.marginTop,
                bottom = bible.marginBottom,
            ),
            bandPercent = bible.lowerThirdHeightPercent,
            lowerThird = target.isLowerThird,
        )
        PreviewBadge(
            label = stringResource(
                if (target.isLowerThird) {
                    Res.string.bible_preview_lower_third
                } else {
                    Res.string.bible_preview_full_screen
                },
            ),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
    }
}
