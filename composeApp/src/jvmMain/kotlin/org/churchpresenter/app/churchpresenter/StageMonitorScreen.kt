package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.churchpresenter.dictionary.data.StrongsEntry
import org.churchpresenter.sharedui.presenter.BoxedItem
import org.churchpresenter.sharedui.presenter.rectIn
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.toStyleZone
import org.churchpresenter.settings.toZone
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.strings.generated.resources.song_key
import org.churchpresenter.strings.generated.resources.song_capo
import org.churchpresenter.strings.generated.resources.song_play
import org.churchpresenter.strings.generated.resources.unit_bpm
import org.churchpresenter.strings.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.composables.songInfoOf
import org.churchpresenter.profiles.MetronomeDot
import org.churchpresenter.profiles.toAlignment
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import java.time.LocalTime
import org.churchpresenter.settings.layoutSizes

private const val CLOCK_TICK_MS = 1000L
/** "Book chapter:verse" over the verse itself — the form both the live and the next zone show. */
private fun SelectedVerse.asZoneText(): String =
    "$bookName $chapter:${verseRange.ifEmpty { verseNumber.toString() }}\n$verseText"

/** The words the live zone shows: the song section on screen, or the verse being presented. */
internal fun stageCurrentText(
    slideContent: Presenting,
    currentLyricSection: LyricSection,
    displayedVerses: List<SelectedVerse>,
): String = when (slideContent) {
    Presenting.LYRICS -> currentLyricSection.lines.joinToString("\n")
    Presenting.BIBLE -> displayedVerses.firstOrNull()?.asZoneText().orEmpty()
    else -> ""
}

/**
 * The words the look-ahead zone shows: the next song section, or the next Bible verse — a dedicated
 * lookahead, not the secondary language of the current verse.
 */
internal fun stageNextText(
    slideContent: Presenting,
    allLyricSections: List<LyricSection>,
    songDisplaySectionIndex: Int,
    nextVerses: List<SelectedVerse>,
): String = when (slideContent) {
    Presenting.LYRICS ->
        allLyricSections.getOrNull(songDisplaySectionIndex + 1)?.lines?.joinToString("\n").orEmpty()
    Presenting.BIBLE -> nextVerses.firstOrNull()?.asZoneText().orEmpty()
    else -> ""
}

/**
 * Which content types are "active" for the current presenting mode — the ones a zone may draw.
 *
 * Clock is deliberately absent: it is the fallback a zone falls back *to* when nothing it was
 * assigned is live. The announcement type is additive rather than exclusive, so it can be shown
 * alongside whatever else is really live on the main output.
 */
internal fun activeStageTypes(
    slideContent: Presenting,
    announcementActive: Boolean,
): Set<StageMonitorContentType> = buildSet {
    when (slideContent) {
        Presenting.BIBLE -> { add(StageMonitorContentType.BIBLE); add(StageMonitorContentType.NEXT) }
        Presenting.LYRICS -> { add(StageMonitorContentType.SONGS); add(StageMonitorContentType.NEXT) }
        Presenting.PRESENTATION ->
            { add(StageMonitorContentType.PRESENTATION); add(StageMonitorContentType.PRESENTATION_NOTES) }
        Presenting.PICTURES -> add(StageMonitorContentType.PICTURES)
        Presenting.MEDIA -> add(StageMonitorContentType.MEDIA)
        Presenting.LOWER_THIRD -> add(StageMonitorContentType.LOWER_THIRD)
        Presenting.WEBSITE -> add(StageMonitorContentType.WEB)
        Presenting.STT -> add(StageMonitorContentType.STT)
        Presenting.CANVAS -> add(StageMonitorContentType.CANVAS)
        Presenting.QA -> add(StageMonitorContentType.QA)
        Presenting.DICTIONARY -> add(StageMonitorContentType.DICTIONARY)
        Presenting.ANNOUNCEMENTS, Presenting.MESSAGE, Presenting.PROPS, Presenting.NONE -> {}
    }
    if (announcementActive) add(StageMonitorContentType.ANNOUNCEMENT_TEXT)
}

/**
 * Full-screen stage monitor layout — 5 quadrant zones plus a full-screen zone, whose content is
 * routed per content type via settings (sm.contentZones), rather than hardcoded to a specific zone:
 *   ┌───────────────────┬───────────────────┐
 *   │  Top-Left         │  Top-Right        │
 *   ├─────────┬─────────┴───────────────────┤
 *   │ Bot-Left│   Bot-Middle  │  Bot-Right  │
 *   └─────────┴───────────────┴─────────────┘
 * If a content type is routed to Full Screen, it takes over the entire monitor instead.
 */
@Composable
fun StageMonitorScreen(
    sm: StageMonitorSettings,
    slideContent: Presenting,
    showChords: Boolean = true,
    // Semitones this output moves the chords it draws: a musician's transpose, on this output
    // alone. The song and every other output keep the key it is written in.
    transposeSteps: Int = 0,
    // True when an announcement has been routed to this stage monitor — either because it's what's
    // actually live everywhere (slideContent == ANNOUNCEMENTS), or because Announcements was sent
    // here specifically via its own "Send to Stage Monitor" toggle. Kept independent of
    // [slideContent] so the Bible/Song/etc. zones below keep tracking whatever is really live on
    // the main output instead of being blanked out by an announcement overlay.
    announcementActive: Boolean = slideContent == Presenting.ANNOUNCEMENTS,
    currentLyricSection: LyricSection,
    allLyricSections: List<LyricSection> = emptyList(),
    songDisplaySectionIndex: Int = 0,
    displayedVerses: List<SelectedVerse>,
    nextVerses: List<SelectedVerse> = emptyList(),
    announcementText: String = "",
    displayedImagePath: String? = null,
    displayedSlide: ImageBitmap? = null,
    presenterNotes: String = "",
    activeScene: Scene? = null,
    displayedQuestion: Question? = null,
    qaSettings: QASettings = QASettings(),
    displayedDictionaryEntry: StrongsEntry? = null,
    dictionarySettings: DictionarySettings = DictionarySettings(),
    /**
     * What the clock zone reads, and whether it reads it as 24-hour.
     *
     * Parameters only so the screenshot of this screen can pin them -- the same reason
     * [AboutDialogContent][org.churchpresenter.app.churchpresenter.dialogs.AboutDialogContent]
     * takes its version line. A live wall clock and the host's locale are both values from outside
     * the composition, and a committed image of a screen that draws them is stale the second it is
     * recorded: 22 of them changed on every run. Nothing but the test passes anything here.
     */
    now: () -> LocalTime = { LocalTime.now() },
    use24Hour: Boolean = isSystemUsing24HourFormat(),
    modifier: Modifier = Modifier
) {
    val currentText = stageCurrentText(slideContent, currentLyricSection, displayedVerses)
    val nextText = stageNextText(slideContent, allLyricSections, songDisplaySectionIndex, nextVerses)

    // Load image bitmap for PICTURES mode
    var currentImageBitmap by remember(displayedImagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(displayedImagePath) {
        currentImageBitmap = loadImageBitmapFromPath(displayedImagePath)
    }

    // Clock state — ticks every second
    var clockText by remember { mutableStateOf(formatClock(now(), use24Hour)) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatClock(now(), use24Hour)
            delay(CLOCK_TICK_MS)
        }
    }

    // Timer text (shared by the Duration/Countdown/Specific-Time content types) — reuses the
    // same pre-formatted string the real Announcements output shows, since that's already
    // mode-aware (duration countdown, open-ended stopwatch, or live clock display) and there's
    // no separate per-mode state to distinguish them here.
    val timerText = announcementText.ifBlank { "--:--" }

    val mediaViewModel = LocalMediaViewModel.current

    val currentChordLines = remember(currentLyricSection, transposeSteps) {
        transposeChordLines(currentLyricSection.chordLines, transposeSteps)
    }
    val renderData = ZoneRenderData(
        currentText = currentText,
        chordLines = if (showChords) currentChordLines else emptyList(),
        songInfo = if (slideContent == Presenting.LYRICS) {
            songInfoOf(
                // The key it reports is the key the chords are drawn in, so it moves with them.
                section = currentLyricSection.copy(chordLines = currentChordLines),
                keyLabel = stringResource(Res.string.song_key),
                capoLabel = stringResource(Res.string.song_capo),
                playLabel = stringResource(Res.string.song_play),
                bpmLabel = stringResource(Res.string.unit_bpm),
            )
        } else {
            null
        },
        nextChordLines = if (showChords && slideContent == Presenting.LYRICS) {
            transposeChordLines(
                allLyricSections.getOrNull(songDisplaySectionIndex + 1)?.chordLines.orEmpty(),
                transposeSteps,
            )
        } else {
            emptyList()
        },
        nextText = nextText,
        currentImageBitmap = currentImageBitmap,
        displayedSlide = displayedSlide,
        clockText = clockText,
        timerText = timerText,
        presenterNotes = presenterNotes,
        activeScene = activeScene,
        displayedQuestion = displayedQuestion,
        qaSettings = qaSettings,
        displayedDictionaryEntry = displayedDictionaryEntry,
        dictionarySettings = dictionarySettings
    )

    val activeTypes = activeStageTypes(slideContent, announcementActive)

    fun contentFor(zone: StageMonitorZone): StageMonitorContentType? {
        val assigned = StageMonitorContentType.entries.filter { sm.zoneFor(it) == zone }
        return assigned.firstOrNull { it in activeTypes }
            ?: StageMonitorContentType.CLOCK.takeIf { it in assigned }
    }

    val fullScreenContent = contentFor(StageMonitorZone.FULL_SCREEN)
    // A boxed zone is drawn in its box over the layout, and leaves its cell empty.
    fun zoneBox(zone: StageMonitorStyleZone) = sm.textBoxes.boxAt(textBoxKey(zone.name, lowerThird = false))
    fun inCell(zone: StageMonitorZone): StageMonitorContentType? =
        contentFor(zone).takeUnless { zone.toStyleZone()?.let(::zoneBox)?.enabled == true }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val area = Rect(0f, 0f, maxWidth.value, maxHeight.value)
        val fullScreenBox = zoneBox(StageMonitorStyleZone.FULL_SCREEN)
        if (fullScreenContent != null) {
            val box = fullScreenBox.takeIf { it.enabled }
            StageFullScreenZone(sm, fullScreenContent, renderData, mediaViewModel, box, area)
        } else {
            // The grid the chosen layout describes: rows down the screen, cells across each row,
            // both weighted. The classic arrangement is one entry in that catalog, not a special
            // case. The weights are percentages, the layout's own until someone resizes a zone.
            val sizes = sm.layoutSizes()
            Column(modifier = Modifier.fillMaxSize()) {
                sm.layout.rows.forEachIndexed { rowIndex, layoutRow ->
                    if (rowIndex > 0) HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
                    Row(modifier = Modifier.fillMaxWidth().weight(sizes.rowHeights[rowIndex])) {
                        layoutRow.cells.forEachIndexed { cellIndex, cell ->
                            if (cellIndex > 0) VerticalDivider(color = Color.DarkGray, thickness = 1.dp)
                            StageZoneBox(
                                sm, cell.slot.toZone(), renderData, mediaViewModel, ::inCell,
                                Modifier.weight(sizes.rowCellWidths[rowIndex][cellIndex])
                            )
                        }
                    }
                }
            }
            StageBoxedZones(sm, area, renderData, mediaViewModel, ::contentFor)
        }

        // Metronome — a silent flash dot, only while a song is actually projected.
        val metronomeAlignment = sm.metronomePosition.toAlignment()
        if (metronomeAlignment != null && slideContent == Presenting.LYRICS && currentLyricSection.bpm > 0) {
            MetronomeDot(
                bpm = currentLyricSection.bpm,
                active = true,
                size = 36.dp,
                modifier = Modifier.align(metronomeAlignment).padding(24.dp).testTag("stage_metronome")
            )
        }
    }
}

/** The full-screen zone, filling the screen -- or [box], where it has been given one. */
@Composable
private fun StageFullScreenZone(
    sm: StageMonitorSettings,
    content: StageMonitorContentType,
    data: ZoneRenderData,
    mediaViewModel: MediaViewModel?,
    box: TextBox?,
    area: Rect,
) {
    val style = sm.styleFor(StageMonitorStyleZone.FULL_SCREEN)
    val zone: @Composable (Modifier) -> Unit = { placed ->
        Box(
            modifier = placed.background(parseHexColor(style.bgColor)).padding(12.dp),
            contentAlignment = zoneContentAlignment(style)
        ) {
            ZoneContent(sm, content, style, data, mediaViewModel)
        }
    }
    if (box == null) {
        zone(Modifier.fillMaxSize())
    } else {
        BoxedItem(box.rectIn(area), box, Constants.CENTER, textBoxKey(StageMonitorStyleZone.FULL_SCREEN.name, false)) {
            zone(Modifier.fillMaxSize())
        }
    }
}

/** Every zone of the layout given a box, drawn in it over the layout. */
@Composable
private fun StageBoxedZones(
    sm: StageMonitorSettings,
    area: Rect,
    data: ZoneRenderData,
    mediaViewModel: MediaViewModel?,
    contentFor: (StageMonitorZone) -> StageMonitorContentType?,
) {
    StageMonitorStyleZone.entries.filter { it != StageMonitorStyleZone.FULL_SCREEN }.forEach { zone ->
        val box = sm.textBoxes.boxAt(textBoxKey(zone.name, lowerThird = false))
        if (box.enabled) {
            BoxedItem(box.rectIn(area), box, Constants.CENTER, textBoxKey(zone.name, false)) {
                StageZoneBox(sm, zone.toZone(), data, mediaViewModel, contentFor, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StageZoneBox(
    sm: StageMonitorSettings,
    zone: StageMonitorZone,
    data: ZoneRenderData,
    mediaViewModel: MediaViewModel?,
    contentFor: (StageMonitorZone) -> StageMonitorContentType?,
    modifier: Modifier
) {
    val styleZone = zone.toStyleZone() ?: return
    val style = sm.styleFor(styleZone)
    val content = contentFor(zone)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(parseHexColor(style.bgColor))
            .padding(12.dp),
        contentAlignment = zoneContentAlignment(style)
    ) {
        if (content != null) {
            ZoneContent(sm, content, style, data, mediaViewModel)
        }
    }
}

/** Bundles the derived per-frame state so it can be passed to whichever zone needs it. */
internal data class ZoneRenderData(
    val currentText: String,
    val chordLines: List<String>,
    val nextChordLines: List<String>,
    val songInfo: String?,
    val nextText: String,
    val currentImageBitmap: ImageBitmap?,
    val displayedSlide: ImageBitmap?,
    val clockText: String,
    val timerText: String,
    val presenterNotes: String,
    val activeScene: Scene?,
    val displayedQuestion: Question?,
    val qaSettings: QASettings,
    val displayedDictionaryEntry: StrongsEntry?,
    val dictionarySettings: DictionarySettings
)
