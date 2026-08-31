package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.customize_no_preview
import org.churchpresenter.app.churchpresenter.composables.BackgroundConfigFill
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.ScreenAssignment
import org.jetbrains.compose.resources.stringResource

/**
 * The 16:9 stage beside the Customize dialog's controls — what this output will actually draw.
 *
 * Every category previews through the **real presenter**, scaled down by [ScaledPresenterBox],
 * rather than through a picture of one: the Bible and Song panels here are the same
 * [BiblePreviewPanel] and [SongPreviewPanel] the global settings tabs use, so a style edited on one
 * screen's Customize dialog and the same style edited on the global tab are drawn by one piece of
 * code and cannot disagree.
 *
 * The dialog previously had no preview at all — an operator set a font size, closed the dialog and
 * went to look at the projector. The whole point of a per-screen override is that the screen is
 * different from the others, which is exactly when guessing is worst.
 */
@Composable
internal fun CustomizeStagePanel(
    pane: CustomizePane,
    element: CustomizeElement?,
    settings: AppSettings,
    assignment: ScreenAssignment,
    slot: PreviewSampleSlot,
    modifier: Modifier = Modifier,
) {
    val lowerThird = LocalOutputStyleScope.current == OutputStyleScope.LOWER_THIRD
    // Sized by WIDTH alone by its caller, so the `aspectRatio` inside each panel is free to set the
    // height. Handed fixed constraints instead -- `fillMaxSize` -- `aspectRatio` cannot satisfy
    // them and silently gives up, and the stage comes out stretched to the column rather than
    // shaped like the screen it is previewing.
    Box(modifier = modifier.testTag(CUSTOMIZE_STAGE_TAG)) {
        when (pane) {
            CustomizePane.BIBLE -> BibleStage(settings, lowerThird, slot)
            CustomizePane.SONGS -> SongStage(settings, assignment, lowerThird, slot)
            CustomizePane.DICTIONARY -> DictionaryStage(settings)
            CustomizePane.BACKGROUND -> BackgroundStage(settings, element, lowerThird)
            // The stage monitor's own tab already draws its zone layout at full size; a second,
            // smaller copy of it beside the controls would say nothing the tab does not.
            CustomizePane.STAGE_MONITOR -> NoStage()
        }
    }
}

/** The sample verse, in every translation the stack carries, at [slot]'s length. */
@Composable
private fun BibleStage(settings: AppSettings, lowerThird: Boolean, slot: PreviewSampleSlot) {
    // A shelf with no translations configured yet still has a Bible *style*, so the preview draws
    // the sample against stock settings rather than reporting "no translations" — the operator is
    // here to see type, and the type is set whether or not a module is installed.
    val translations = settings.bibleSettings.translationList()
        .ifEmpty { listOf(BibleTranslationSettings()) }
    BiblePreviewPanel(
        settings = settings,
        target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN,
        // No module text: the preview reads no `.spb` here, so every translation falls back to the
        // English sample. What is being previewed is the styling, and the styling does not care
        // which words carry it.
        selectedVerses = bibleSampleVerses(
            translations = translations,
            verses = emptyMap(),
            slot = slot,
            moduleTitles = emptyMap(),
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A verse and the chorus behind it, with the look-ahead and chart this output is set up for. */
@Composable
private fun SongStage(
    settings: AppSettings,
    assignment: ScreenAssignment,
    lowerThird: Boolean,
    slot: PreviewSampleSlot,
) {
    SongPreviewPanel(
        settings = settings,
        target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN,
        // Taken from this output's own assignment rather than from a switch above the preview: the
        // global tab asks "what should this picture contain", but here the screen has already
        // answered — it either has a look-ahead and a chart or it does not.
        showLookAhead = assignment.songLookAhead,
        showChords = assignment.showChords,
        sections = songSampleSections(slot),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A Strong's card, drawn by [DictionaryPresenter] at the output's own size. */
@Composable
private fun DictionaryStage(settings: AppSettings) {
    val output = previewOutputSize(settings)
    StageFrame(settings) {
        ScaledPresenterBox(output) {
            DictionaryPresenter(
                entry = DICTIONARY_SAMPLE_ENTRY,
                dictionarySettings = settings.dictionarySettings,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The surface itself, as far as a still tile can draw it.
 *
 * [BackgroundConfigFill] is the same tile the global Background tab paints its rail and its stage
 * with — colour, gradient or picture, dimmed and faded the way the presenter draws it. A video
 * shows as black there and here alike: spinning up VLC for a few hundred dp is not worth what it
 * costs, and the alternative is two different answers to "what does this background look like".
 */
@Composable
private fun BackgroundStage(settings: AppSettings, element: CustomizeElement?, lowerThird: Boolean) {
    val scope = (element ?: CustomizeElement.BACKGROUND_DEFAULT).backgroundScope(lowerThird)
    val config = settings.backgroundSettings.configFor(scope)
    StageFrame(settings) {
        BackgroundConfigFill(config, Modifier.fillMaxSize())
    }
}

/** The plate the two hand-built stages sit on, matching the two presenter-backed panels' frame. */
@Composable
private fun StageFrame(settings: AppSettings, content: @Composable () -> Unit) {
    val output = previewOutputSize(settings)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(output.aspectRatio)
            .clipToBounds()
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
        content = { content() },
    )
}

/** What the stage says where the category has nothing to draw. */
@Composable
private fun NoStage() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(NO_STAGE_RATIO)
            .background(Color(PREVIEW_BACKGROUND), RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(Res.string.customize_no_preview),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = NO_STAGE_ALPHA),
        )
    }
}

private const val NO_STAGE_ALPHA = 0.45f

/** 16:9, so the empty plate is the shape a stage would have been. */
private const val NO_STAGE_RATIO = 16f / 9f

/**
 * The entry the dictionary stage quotes.
 *
 * `agape` rather than a random number: it is the word most likely to be on screen when someone is
 * setting up a dictionary card, and it exercises every one of the five styled fields — the original
 * word in Greek script, the transliteration, the reference, a definition long enough to wrap, and a
 * KJV usage list.
 */
private val DICTIONARY_SAMPLE_ENTRY = StrongsEntry(
    number = "G26",
    word = "ἀγάπη",
    transliteration = "agapē",
    pronunciation = "ag-ah'-pay",
    definition = "love, i.e. affection or benevolence; specially (plural) a love-feast.",
    kjvUsage = "(feast of) charity, dear, love.",
)

/** Test handle for the preview stage. */
internal const val CUSTOMIZE_STAGE_TAG = "customize_stage"
