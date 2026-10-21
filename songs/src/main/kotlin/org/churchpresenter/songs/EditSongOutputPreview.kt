package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.presenter.LocalTransparentBlanking
import org.churchpresenter.presenter.ScaledOutputBox
import org.churchpresenter.presenter.SongPresenter
import org.churchpresenter.presenter.textOnly
import org.churchpresenter.presenter.wholeOutputRegion
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.profileIdsInUse
import org.churchpresenter.settings.resolvedFor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.composables.PaneTab
import org.churchpresenter.sharedui.composables.PaneTabRow
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.sharedui.utils.previewOutputSize
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_chunk_line
import org.churchpresenter.strings.generated.resources.song_chunk_verse
import org.churchpresenter.strings.generated.resources.song_output_full_screen
import org.churchpresenter.strings.generated.resources.song_output_lower_third
import org.churchpresenter.strings.generated.resources.song_output_nothing
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

/** Test handles for the editor's two output previews. */
internal const val OUTPUT_PREVIEW_FULL_TAG = "song_editor_output_full"
internal const val OUTPUT_PREVIEW_BAND_TAG = "song_editor_output_band"

/** The plate a preview sits on — what an output with nothing behind its words shows. */
private val PREVIEW_PLATE = Color(0xFF08090B)
private val PreviewShape = AppShape(6.dp)

/** How much of a lower-third output the band preview shows, past the band's own height. */
private const val BAND_CROP_MARGIN = 0.12f
private const val PERCENT = 100f

/**
 * [this] with every song display mode — full screen, lower third and their look-ahead — set to
 * [mode], so a preview shows the slide one line at a time or a verse at a time whatever the
 * profile is set to.
 */
internal fun AppSettings.withSongDisplayMode(mode: String): AppSettings = copy(
    songSettings = songSettings.copy(
        fullscreenDisplayMode = mode,
        lowerThirdDisplayMode = mode,
        lookAheadDisplayMode = mode,
        lowerThirdLookAheadDisplayMode = mode,
    ),
)

/** The slide the caret is on, the slides around it, and the line of it the caret is on. */
internal data class CaretSlide(val slides: List<LyricSection>, val index: Int, val lineIndex: Int) {
    val section: LyricSection get() = slides[index]
}

/**
 * Which slide of [text] the caret at [caret] sits on, as [lyricSlidesOf] splits it, and which of
 * its lines — or null when the text has nothing to show.
 *
 * The slides are built in order, so the caret's slide is the last one the text up to the end of
 * the caret's line makes; the lines that slide has so far are the lines above the caret, which
 * gives the line.
 */
internal fun caretSlideOf(text: String, caret: Int, title: String): CaretSlide? {
    val slides = lyricSlidesOf(text.split("\n"), title, "").filter { it.lines.isNotEmpty() }
    if (slides.isEmpty()) return null
    val lineEnd = text.indexOf('\n', caret.coerceIn(0, text.length)).let { if (it < 0) text.length else it }
    val upToCaret = lyricSlidesOf(text.take(lineEnd).split("\n"), title, "").filter { it.lines.isNotEmpty() }
    val index = (upToCaret.size - 1).coerceIn(0, slides.lastIndex)
    val line = ((upToCaret.lastOrNull()?.lines?.size ?: 1) - 1).coerceIn(0, (slides[index].lines.size - 1).coerceAtLeast(0))
    return CaretSlide(slides, index, line)
}

/**
 * The profiles the previews draw with: the first one an output follows of each kind — full screen
 * and lower third — and failing that the first of each kind there is. Either may be null.
 */
internal fun previewProfilesOf(settings: AppSettings): Pair<OutputProfile?, OutputProfile?> {
    val profiles = settings.projectionSettings.outputProfiles
    val inUse = settings.projectionSettings.profileIdsInUse().mapNotNull { id -> profiles.find { it.id == id } }
    fun first(lowerThird: Boolean) = (inUse + profiles).firstOrNull { it.isLowerThird == lowerThird }
    return first(lowerThird = false) to first(lowerThird = true)
}

/**
 * The Output tab: the slide under the caret as the projector shows it, the band below it as the
 * lower third does — both in the profiles the outputs follow, with this song's own look applied —
 * and the song's own look to change.
 */
@Composable
internal fun EditSongOutputPane(state: EditSongState, appSettings: AppSettings, modifier: Modifier = Modifier) {
    val text = state.paneValue.text
    val caret = state.paneValue.selection.start
    val slide = remember(text, caret, state.title) { caretSlideOf(text, caret, state.title) }
    val profiles = remember(appSettings) { previewProfilesOf(appSettings) }
    val output = remember(appSettings) { previewOutputSize(appSettings) }
    var mode by remember { mutableStateOf(Constants.SONG_DISPLAY_MODE_VERSE) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (slide == null) {
            Text(
                text = stringResource(Res.string.song_output_nothing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PaneTabRow {
                PaneTab(stringResource(Res.string.song_chunk_verse), mode == Constants.SONG_DISPLAY_MODE_VERSE) {
                    mode = Constants.SONG_DISPLAY_MODE_VERSE
                }
                PaneTab(stringResource(Res.string.song_chunk_line), mode == Constants.SONG_DISPLAY_MODE_LINE) {
                    mode = Constants.SONG_DISPLAY_MODE_LINE
                }
            }
            // The look being edited, not the one the song was opened with.
            val shown = slide.slides.map { it.copy(textOverrides = state.textOverrides) }
            profiles.first?.let { profile ->
                PreviewCaption(stringResource(Res.string.song_output_full_screen), profile.name)
                OutputPreview(appSettings.resolvedFor(profile).withSongDisplayMode(mode), output, shown, slide, lowerThird = false)
            }
            profiles.second?.let { profile ->
                PreviewCaption(stringResource(Res.string.song_output_lower_third), profile.name)
                OutputPreview(appSettings.resolvedFor(profile).withSongDisplayMode(mode), output, shown, slide, lowerThird = true)
            }
        }
        SongLookCard(state, appSettings, profiles.first ?: profiles.second)
    }
}

@Composable
private fun PreviewCaption(kind: String, profileName: String) {
    Text(
        text = listOf(kind.uppercase(), profileName).filter { it.isNotBlank() }.joinToString(" · "),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.0.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * One output's picture of the slide. A lower third is cropped to its band — the rest of a
 * lower-third output is the picture behind it, which is not the song's to show.
 */
@Composable
private fun OutputPreview(
    settings: AppSettings,
    output: OutputSize,
    slides: List<LyricSection>,
    slide: CaretSlide,
    lowerThird: Boolean,
) {
    val region = settings.songSettings.layoutExtras.contentRegion
    val lineMode = (if (lowerThird) settings.songSettings.lowerThirdDisplayMode else settings.songSettings.fullscreenDisplayMode) ==
        Constants.SONG_DISPLAY_MODE_LINE
    val shownFraction = if (lowerThird) {
        (settings.songSettings.lowerThirdHeightPercent / PERCENT + BAND_CROP_MARGIN).coerceAtMost(1f)
    } else {
        1f
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(output.aspectRatio / shownFraction)
            .clip(PreviewShape)
            .background(PREVIEW_PLATE, PreviewShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PreviewShape)
            .testTag(if (lowerThird) OUTPUT_PREVIEW_BAND_TAG else OUTPUT_PREVIEW_FULL_TAG),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .aspectRatio(output.aspectRatio),
        ) {
            ScaledOutputBox(output) {
                // Blank shows the plate rather than painting it black, as the Profiles previews do.
                CompositionLocalProvider(LocalTransparentBlanking provides true) {
                    SongPresenter(
                        modifier = if (lowerThird) Modifier else Modifier.wholeOutputRegion(region),
                        textRegion = region.textOnly(lowerThird),
                        lyricSection = slides[slide.index],
                        appSettings = settings,
                        isLowerThird = lowerThird,
                        // Verse mode shows the whole slide; only line mode is at a line of it.
                        displayLineIndex = if (lineMode) slide.lineIndex else -1,
                        allLyricSections = slides,
                        displaySectionIndex = slide.index,
                    )
                }
            }
        }
    }
}
