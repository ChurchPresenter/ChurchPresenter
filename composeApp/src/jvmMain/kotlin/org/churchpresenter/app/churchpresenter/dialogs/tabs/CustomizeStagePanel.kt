package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import org.churchpresenter.app.churchpresenter.presenter.BibleLottieStillFrame
import org.churchpresenter.app.churchpresenter.presenter.resolveAboveBand
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputStyleScope
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.bibleTranslationPositions
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
    profile: OutputProfile,
    /** This output's own size -- see the caller's note on why it must not be re-derived here. */
    output: PreviewOutputSize,
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
            CustomizePane.BIBLE -> BibleStage(settings, profile, output, lowerThird, slot)
            CustomizePane.SONGS -> SongStage(settings, profile, output, lowerThird, slot, element)
            CustomizePane.BACKGROUND -> BackgroundStage(settings, output, element, lowerThird)
            // The stage monitor's own tab already draws its zone layout at full size; a second,
            // smaller copy of it beside the controls would say nothing the tab does not.
            CustomizePane.STAGE_MONITOR -> NoStage()
        }
    }
}

/** The sample verse, in the translations *this profile* draws, at [slot]'s length. */
@Composable
private fun BibleStage(
    settings: AppSettings,
    profile: OutputProfile,
    output: PreviewOutputSize,
    lowerThird: Boolean,
    slot: PreviewSampleSlot,
) {
    // The profile's own subset, not the whole stack -- see [bibleTranslationPositions]. Narrowing
    // an output to one translation used to leave the preview drawing all of them, so the picture
    // beside the controls disagreed with the screen it stood for.
    val stack = settings.bibleSettings.translationList()
    val chosen = profile.bibleTranslationPositions(stack.size).mapNotNull(stack::getOrNull)
    // A shelf with no translations configured yet still has a Bible *style*, so the preview draws
    // the sample against stock settings rather than reporting "no translations" — the operator is
    // here to see type, and the type is set whether or not a module is installed. The same applies
    // to a selection that has gone stale: something has to carry the type.
    val translations = chosen.ifEmpty { listOf(BibleTranslationSettings()) }
    BiblePreviewPanel(
        settings = settings,
        target = if (lowerThird) BibleStyleTarget.LOWER_THIRD else BibleStyleTarget.FULL_SCREEN,
        output = output,
        // No module text: the preview reads no `.spb` here, so every translation falls back to the
        // English sample. What is being previewed is the styling, and the styling does not care
        // which words carry it.
        selectedVerses = bibleSampleVerses(
            translations = translations,
            verses = emptyMap(),
            slot = slot,
            moduleTitles = emptyMap(),
        ),
        // This profile's own shape, not whether some other output happens to be portrait.
        vertical = profile.isLowerThirdVertical,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * A verse and the chorus behind it, with the look-ahead this output is set up for -- or, under the
 * Title Slide chip, the title slide that opens the song.
 */
@Composable
private fun SongStage(
    settings: AppSettings,
    profile: OutputProfile,
    output: PreviewOutputSize,
    lowerThird: Boolean,
    slot: PreviewSampleSlot,
    element: CustomizeElement?,
) {
    val titleSlide = element == CustomizeElement.SONG_TITLE_SLIDE
    val lyricSections = songSampleSections(slot)
    SongPreviewPanel(
        settings = settings,
        target = if (lowerThird) SongStyleTarget.LOWER_THIRD else SongStyleTarget.FULL_SCREEN,
        output = output,
        // Taken from this output's own profile rather than from a switch above the preview: the
        // global tab asks "what should this picture contain", but here the screen has already
        // answered whether it carries a look-ahead line. A title slide has none.
        showLookAhead = profile.songLookAhead && !titleSlide,
        // Never a chord chart. The chart is for whoever is playing, so it is drawn on the stage
        // monitor and nowhere the congregation can see -- `ProjectionSettingsTab` shows the Show
        // Chords column only for a stage monitor, and `PresenterOutputContent` passes `showChords`
        // only into `StageMonitorScreen`. This pane is reached only by a full screen or a lower
        // third, neither of which draws one, and `ScreenAssignment.showChords` defaults to `true`
        // -- so reading it here drew a chart on every song preview that the screen never shows.
        // `SongSettingsTab`'s own preview passes `false` for the same reason.
        showChords = false,
        sections = if (titleSlide) {
            listOf(titleSlideSample(settings.songSettings, slot)) + lyricSections
        } else {
            lyricSections
        },
        // The languages this profile shows, decided exactly as the screen decides them. Without
        // these the preview fell back to the song-level setting and drew every language the sample
        // carries, so narrowing an output to one changed the screen and not the picture of it.
        //
        // A profile with songs switched off keeps the song-level fallback rather than previewing
        // nothing: the pane is still where its type is set, and type cannot be judged on a blank.
        languageOverride = profile.songMode.takeIf { it != Constants.SONG_LANG_OFF },
        languageSelection = profile.songTranslations,
        // This profile's own shape, not whether some other output happens to be portrait.
        vertical = profile.isLowerThirdVertical,
        titleSlide = titleSlide,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The surface itself, as far as a still tile can draw it.
 *
 * [BackgroundConfigFill] is the same tile the global Background tab paints its rail and its stage
 * with — colour, gradient or picture, dimmed and faded the way the presenter draws it. A video
 * shows as black there and here alike: spinning up VLC for a few hundred dp is not worth what it
 * costs, and the alternative is two different answers to "what does this background look like".
 *
 * **A lower-third surface is drawn as the band it actually is**, with its wash above it. This tile
 * used to fill the whole stage whatever the output's shape, which told an operator setting a band
 * colour that their whole screen was about to turn that colour — a look the output never produces
 * — and left the wash above the band with nowhere to appear at all. The global Background tab's
 * preview has always split it this way; this is the same split, against this output's own band
 * height rather than the taller of the two.
 */
@Composable
private fun BackgroundStage(
    settings: AppSettings,
    output: PreviewOutputSize,
    element: CustomizeElement?,
    lowerThird: Boolean,
) {
    val scope = (element ?: CustomizeElement.BACKGROUND_DEFAULT).backgroundScope(lowerThird)
    val config = settings.backgroundSettings.configFor(scope)
    StageFrame(output) {
        if (!scope.lowerThird) {
            BackgroundConfigFill(config, Modifier.fillMaxSize())
            return@StageFrame
        }
        val band = settings.bandFractionFor(scope)
        val above = resolveAboveBand(settings.backgroundSettings, config).fill
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f - band)
                    .then(if (above != null) Modifier.background(above) else Modifier)
            )
            // Clipped for the reason the presenter clips its band: a blurred fill is overscanned,
            // and without this it spills up over the band line.
            Box(Modifier.fillMaxWidth().weight(band).clipToBounds()) {
                if (config.backgroundType == Constants.BACKGROUND_LOTTIE) {
                    // The template at rest, sample text and all: it is the band, not a fill.
                    BibleLottieStillFrame(config.backgroundLottie, Modifier.fillMaxSize())
                } else {
                    BackgroundConfigFill(config, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** The plate the two hand-built stages sit on, matching the two presenter-backed panels' frame. */
@Composable
private fun StageFrame(output: PreviewOutputSize, content: @Composable () -> Unit) {
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

/** Test handle for the preview stage. */
internal const val CUSTOMIZE_STAGE_TAG = "customize_stage"
