package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.settings.toStyleZone
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.DictionaryPresenter
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.settings.utils.isSystemUsing24HourFormat
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.PictureDecoder
import churchpresenter.composeapp.generated.resources.song_key
import churchpresenter.composeapp.generated.resources.song_capo
import churchpresenter.composeapp.generated.resources.song_play
import churchpresenter.composeapp.generated.resources.unit_bpm
import churchpresenter.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.churchpresenter.app.churchpresenter.utils.calculateChordChartFontSize
import org.churchpresenter.app.churchpresenter.composables.ChordChart
import org.churchpresenter.app.churchpresenter.composables.songInfoOf
import org.churchpresenter.app.churchpresenter.composables.MetronomeDot
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.composables.toAlignment
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.churchpresenter.app.churchpresenter.viewmodel.MediaViewModel
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val CLOCK_TICK_MS = 1000L
private const val BOTTOM_MIDDLE_WEIGHT = 0.8f
private const val SHADOW_OFFSET_DIVISOR = 10f

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
    presentingMode: Presenting,
    // True when an announcement has been routed to this stage monitor — either because it's what's
    // actually live everywhere (presentingMode == ANNOUNCEMENTS), or because Announcements was sent
    // here specifically via its own "Send to Stage Monitor" toggle. Kept independent of
    // [presentingMode] so the Bible/Song/etc. zones below keep tracking whatever is really live on
    // the main output instead of being blanked out by an announcement overlay.
    announcementActive: Boolean = presentingMode == Presenting.ANNOUNCEMENTS,
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
    modifier: Modifier = Modifier
) {
    // Derive current text
    val currentText: String = when (presentingMode) {
        Presenting.LYRICS -> currentLyricSection.lines.joinToString("\n")
        Presenting.BIBLE -> {
            val v = displayedVerses.firstOrNull()
            if (v != null) {
                val ref = "${v.bookName} ${v.chapter}:${v.verseRange.ifEmpty { v.verseNumber.toString() }}"
                "$ref\n${v.verseText}"
            } else ""
        }
        else -> ""
    }

    // Next Bible verse (a dedicated lookahead, not the secondary language of the current verse)
    // or next song line/section.
    val nextText: String = when (presentingMode) {
        Presenting.LYRICS -> {
            val nextIdx = songDisplaySectionIndex + 1
            allLyricSections.getOrNull(nextIdx)?.lines?.joinToString("\n") ?: ""
        }
        Presenting.BIBLE -> {
            val v = nextVerses.firstOrNull()
            if (v != null) {
                val ref = "${v.bookName} ${v.chapter}:${v.verseRange.ifEmpty { v.verseNumber.toString() }}"
                "$ref\n${v.verseText}"
            } else ""
        }
        else -> ""
    }

    // Load image bitmap for PICTURES mode
    var currentImageBitmap by remember(displayedImagePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(displayedImagePath) {
        currentImageBitmap = loadImageBitmapFromPath(displayedImagePath)
    }

    // Clock state — ticks every second
    var clockText by remember { mutableStateOf(formatClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = formatClock()
            delay(CLOCK_TICK_MS)
        }
    }

    // Timer text (shared by the Duration/Countdown/Specific-Time content types) — reuses the
    // same pre-formatted string the real Announcements output shows, since that's already
    // mode-aware (duration countdown, open-ended stopwatch, or live clock display) and there's
    // no separate per-mode state to distinguish them here.
    val timerText = announcementText.ifBlank { "--:--" }

    val mediaViewModel = LocalMediaViewModel.current

    val renderData = ZoneRenderData(
        currentText = currentText,
        chordLines = if (sm.showChords) currentLyricSection.chordLines else emptyList(),
        songInfo = if (presentingMode == Presenting.LYRICS) {
            songInfoOf(
                section = currentLyricSection,
                keyLabel = stringResource(Res.string.song_key),
                capoLabel = stringResource(Res.string.song_capo),
                playLabel = stringResource(Res.string.song_play),
                bpmLabel = stringResource(Res.string.unit_bpm),
            )
        } else {
            null
        },
        nextChordLines = if (sm.showChords && presentingMode == Presenting.LYRICS) {
            allLyricSections.getOrNull(songDisplaySectionIndex + 1)?.chordLines.orEmpty()
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

    // Which content type is "active" for the current presenting mode. Clock is always available
    // as a fallback so a zone assigned both a live type and Clock shows the clock when idle.
    // The announcement zone is additive (see [announcementActive]) rather than exclusive, so it
    // can be shown alongside whatever else is actually live on the main output.
    val activeTypes: Set<StageMonitorContentType> = buildSet {
        when (presentingMode) {
            Presenting.BIBLE -> { add(StageMonitorContentType.BIBLE); add(StageMonitorContentType.NEXT) }
            Presenting.LYRICS -> { add(StageMonitorContentType.SONGS); add(StageMonitorContentType.NEXT) }
            Presenting.PRESENTATION -> { add(StageMonitorContentType.PRESENTATION); add(StageMonitorContentType.PRESENTATION_NOTES) }
            Presenting.PICTURES -> add(StageMonitorContentType.PICTURES)
            Presenting.MEDIA -> add(StageMonitorContentType.MEDIA)
            Presenting.LOWER_THIRD -> add(StageMonitorContentType.LOWER_THIRD)
            Presenting.WEBSITE -> add(StageMonitorContentType.WEB)
            Presenting.STT -> add(StageMonitorContentType.STT)
            Presenting.CANVAS -> add(StageMonitorContentType.CANVAS)
            Presenting.QA -> add(StageMonitorContentType.QA)
            Presenting.DICTIONARY -> add(StageMonitorContentType.DICTIONARY)
            Presenting.ANNOUNCEMENTS, Presenting.NONE -> {}
        }
        if (announcementActive) add(StageMonitorContentType.ANNOUNCEMENT_TEXT)
    }

    fun contentFor(zone: StageMonitorZone): StageMonitorContentType? {
        val assigned = StageMonitorContentType.entries.filter { sm.zoneFor(it) == zone }
        return assigned.firstOrNull { it in activeTypes }
            ?: StageMonitorContentType.CLOCK.takeIf { it in assigned }
    }

    val fullScreenContent = contentFor(StageMonitorZone.FULL_SCREEN)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (fullScreenContent != null) {
            val style = sm.styleFor(StageMonitorStyleZone.FULL_SCREEN)
            Box(
                modifier = Modifier.fillMaxSize().background(parseHexColor(style.bgColor)).padding(12.dp),
                contentAlignment = zoneContentAlignment(style)
            ) {
                ZoneContent(fullScreenContent, style, renderData, mediaViewModel)
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── TOP ROW (fixed 2:1 against bottom = 67%; left/right split evenly 50/50) ──
                Row(modifier = Modifier.fillMaxWidth().weight(2f)) {
                    StageZoneBox(sm, StageMonitorZone.TOP_LEFT, renderData, mediaViewModel, ::contentFor, Modifier.weight(1f))
                    VerticalDivider(color = Color.DarkGray, thickness = 1.dp)
                    StageZoneBox(sm, StageMonitorZone.TOP_RIGHT, renderData, mediaViewModel, ::contentFor, Modifier.weight(1f))
                }

                HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

                // ── BOTTOM ROW (fixed at 33% of total height, regardless of content) ──
                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    StageZoneBox(sm, StageMonitorZone.BOTTOM_LEFT, renderData, mediaViewModel, ::contentFor, Modifier.weight(1f))
                    VerticalDivider(color = Color.DarkGray, thickness = 1.dp)
                    StageZoneBox(sm, StageMonitorZone.BOTTOM_MIDDLE, renderData, mediaViewModel, ::contentFor, Modifier.weight(BOTTOM_MIDDLE_WEIGHT))
                    VerticalDivider(color = Color.DarkGray, thickness = 1.dp)
                    StageZoneBox(sm, StageMonitorZone.BOTTOM_RIGHT, renderData, mediaViewModel, ::contentFor, Modifier.weight(1f))
                }
            }
        }

        // Metronome — a silent flash dot, only while a song is actually projected.
        val metronomeAlignment = sm.metronomePosition.toAlignment()
        if (metronomeAlignment != null && presentingMode == Presenting.LYRICS && currentLyricSection.bpm > 0) {
            MetronomeDot(
                bpm = currentLyricSection.bpm,
                active = true,
                size = 36.dp,
                modifier = Modifier.align(metronomeAlignment).padding(24.dp).testTag("stage_metronome")
            )
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
            ZoneContent(content, style, data, mediaViewModel)
        }
    }
}

/** Bundles the derived per-frame state so it can be passed to whichever zone needs it. */
private data class ZoneRenderData(
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

/**
 * A chord chart drawn in a zone's own styling, so it reads as that zone's text with the chords
 * lifted above it rather than as something pasted in from elsewhere.
 */
@Composable
private fun ZoneChordChart(
    style: StageMonitorZoneStyle,
    lines: List<String>,
    songInfo: String? = null,
) {
    val ink = parseHexColor(style.color)
    val chordColor = parseHexColor(style.chordColor)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val measurer = rememberTextMeasurer()
        val baseStyle = buildTextStyle(
            fontType = style.fontType,
            fontSize = style.fontSize,
            color = ink,
            bold = style.bold,
            italic = style.italic,
        )
        val fitted = remember(lines, songInfo, style, maxWidth, maxHeight) {
            calculateChordChartFontSize(
                textMeasurer = measurer,
                lines = lines,
                baseStyle = baseStyle,
                availableWidth = maxWidth.value.toInt(),
                availableHeight = maxHeight.value.toInt(),
                maxFontSize = style.fontSize,
                hasInfoLine = !songInfo.isNullOrBlank(),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!songInfo.isNullOrBlank()) {
                Text(
                    text = songInfo,
                    color = chordColor,
                    fontSize = (fitted * 0.5f).sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            ChordChart(
                lines = lines,
                textColor = ink,
                chordColor = chordColor,
                fontSize = fitted.sp,
            )
        }
    }
}

/**
 * The largest size at or below the zone's own that fits [text] in the space given.
 *
 * The configured size is a ceiling, not a target: a zone set to 35 stays at 35 until the words stop
 * fitting, and only then steps down. Measurement is in the reference units
 * [calculateAutoFitFontSize] works in, where one sp of type occupies one dp of line box, so the
 * constraints are handed over as their dp values rather than as raw pixels.
 */
@Composable
private fun fittedFontSize(
    style: StageMonitorZoneStyle,
    text: String,
    maxWidth: Dp,
    maxHeight: Dp,
): Int {
    val measurer = rememberTextMeasurer()
    val baseStyle = buildTextStyle(
        fontType = style.fontType,
        fontSize = style.fontSize,
        color = parseHexColor(style.color),
        bold = style.bold,
        italic = style.italic,
    )
    return remember(text, style, maxWidth, maxHeight) {
        if (text.isBlank()) style.fontSize
        else calculateAutoFitFontSize(
            textMeasurer = measurer,
            text = text,
            baseStyle = baseStyle,
            availableWidth = maxWidth.value.toInt(),
            availableHeight = maxHeight.value.toInt(),
        ).coerceAtMost(style.fontSize)
    }
}

/** [TextContent], but stepped down to whatever size the words actually fit at. */
@Composable
private fun FittedTextContent(style: StageMonitorZoneStyle, text: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fitted = fittedFontSize(style, text, maxWidth, maxHeight)
        TextContent(style.copy(fontSize = fitted), text)
    }
}

private fun zoneContentAlignment(style: StageMonitorZoneStyle): Alignment {
    val vertical = when (style.verticalAlignment) {
        Constants.BOTTOM -> 1f
        Constants.MIDDLE -> 0f
        else -> -1f
    }
    val horizontal = when (style.horizontalAlignment) {
        Constants.RIGHT -> 1f
        Constants.CENTER -> 0f
        else -> -1f
    }
    return BiasAlignment(horizontal, vertical)
}

@Composable
private fun ZoneContent(
    content: StageMonitorContentType,
    style: StageMonitorZoneStyle,
    data: ZoneRenderData,
    mediaViewModel: MediaViewModel?
) {
    when (content) {
        StageMonitorContentType.BIBLE -> TextContent(style, data.currentText)
        // A song with chords is shown as its chart; without them it is the words alone, exactly as
        // before. The Next zone follows the same rule for the section coming up.
        StageMonitorContentType.SONGS ->
            if (data.chordLines.isEmpty()) FittedTextContent(style, data.currentText)
            else ZoneChordChart(style, data.chordLines, data.songInfo)
        StageMonitorContentType.PRESENTATION -> SlideContent(data.displayedSlide)
        StageMonitorContentType.PRESENTATION_NOTES -> ScrollingTextContent(style, data.presenterNotes)
        StageMonitorContentType.PICTURES -> SlideContent(data.currentImageBitmap)
        StageMonitorContentType.MEDIA -> {
            if (mediaViewModel != null && mediaViewModel.isLoaded && !mediaViewModel.isAudioFile) {
                SoftwareVideoPlayer(
                    viewModel = mediaViewModel,
                    modifier = Modifier.fillMaxSize(),
                    audioEnabled = false // audio is handled by the main output
                )
            }
        }
        StageMonitorContentType.CLOCK -> CenteredText(data.clockText, style)
        StageMonitorContentType.ANNOUNCEMENT_TEXT -> CenteredText(data.timerText, style)
        StageMonitorContentType.CANVAS -> ScenePresenter(modifier = Modifier.fillMaxSize(), scene = data.activeScene)
        StageMonitorContentType.QA -> QAPresenter(question = data.displayedQuestion, qaSettings = data.qaSettings)
        StageMonitorContentType.DICTIONARY -> DictionaryPresenter(entry = data.displayedDictionaryEntry, dictionarySettings = data.dictionarySettings)
        StageMonitorContentType.NEXT ->
            if (data.nextChordLines.isEmpty()) TextContent(style, data.nextText)
            else ZoneChordChart(style, data.nextChordLines)
        // No live data is plumbed through to the stage monitor for these yet.
        StageMonitorContentType.LOWER_THIRD,
        StageMonitorContentType.WEB,
        StageMonitorContentType.STT -> {}
    }
}

@Composable
private fun TextContent(style: StageMonitorZoneStyle, text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = resolveColumnVerticalArrangement(style.verticalAlignment),
        horizontalAlignment = resolveColumnHorizontalAlignment(style.horizontalAlignment)
    ) {
        Text(
            text = text,
            style = buildTextStyle(
                fontType = style.fontType,
                fontSize = style.fontSize,
                color = parseHexColor(style.color),
                bold = style.bold,
                italic = style.italic,
                underline = style.underline,
                shadow = style.shadow,
                shadowColor = parseHexColor(style.shadowColor),
                shadowSize = style.shadowSize,
                shadowOpacity = style.shadowOpacity
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = resolveTextAlign(style.horizontalAlignment)
        )
    }
}

@Composable
private fun ScrollingTextContent(style: StageMonitorZoneStyle, text: String) {
    val scrollState = rememberScrollState()
    Text(
        text = text,
        style = buildTextStyle(
            fontType = style.fontType,
            fontSize = style.fontSize,
            color = parseHexColor(style.color),
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            shadowColor = parseHexColor(style.shadowColor),
            shadowSize = style.shadowSize,
            shadowOpacity = style.shadowOpacity
        ),
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
        textAlign = resolveTextAlign(style.horizontalAlignment)
    )
}

@Composable
private fun SlideContent(bitmap: ImageBitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            // Tagged rather than described: it is decorative to a screen reader, but a test has no
            // other handle on "the slide is on the monitor" than a semantics node.
            modifier = Modifier.fillMaxSize().testTag("stage_slide")
        )
    }
}

@Composable
private fun CenteredText(text: String, style: StageMonitorZoneStyle) {
    Text(
        text = text,
        style = buildTextStyle(
            fontType = style.fontType,
            fontSize = style.fontSize,
            color = parseHexColor(style.color),
            bold = style.bold,
            italic = style.italic,
            underline = style.underline,
            shadow = style.shadow,
            shadowColor = parseHexColor(style.shadowColor),
            shadowSize = style.shadowSize,
            shadowOpacity = style.shadowOpacity
        ),
        textAlign = TextAlign.Center
    )
}

private fun formatClock(): String {
    val pattern = if (isSystemUsing24HourFormat()) "HH:mm:ss" else "hh:mm:ss a"
    return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern))
}

private fun buildTextStyle(
    fontType: String,
    fontSize: Int,
    color: Color,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    shadow: Boolean = false,
    shadowColor: Color = Color.Black,
    shadowSize: Int = 100,
    shadowOpacity: Int = 80
): TextStyle {
    val fontFamily = systemFontFamilyOrDefault(fontType)
    return TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize.sp,
        color = color,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (underline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (shadow) Shadow(
            color = shadowColor.copy(alpha = shadowOpacity / 100f),
            offset = Offset(shadowSize / SHADOW_OFFSET_DIVISOR, shadowSize / SHADOW_OFFSET_DIVISOR),
            blurRadius = shadowSize / 5f
        ) else null
    )
}

private suspend fun loadImageBitmapFromPath(path: String?): ImageBitmap? {
    if (path == null) return null
    return withContext(Dispatchers.IO) {
        // PictureDecoder, not Skia directly — the stage monitor draws the same operator-chosen
        // files the output does, and must not blank on a format only the fallbacks read.
        PictureDecoder.decodeOrNull(File(path))?.toComposeImageBitmap()
    }
}

private fun resolveTextAlign(horizontal: String): TextAlign {
    return when (horizontal) {
        Constants.LEFT -> TextAlign.Start
        Constants.RIGHT -> TextAlign.End
        else -> TextAlign.Center
    }
}

private fun resolveColumnVerticalArrangement(vertical: String): Arrangement.Vertical {
    return when (vertical) {
        Constants.BOTTOM -> Arrangement.Bottom
        Constants.MIDDLE -> Arrangement.Center
        else -> Arrangement.Top
    }
}

private fun resolveColumnHorizontalAlignment(horizontal: String): Alignment.Horizontal {
    return when (horizontal) {
        Constants.RIGHT -> Alignment.End
        Constants.CENTER -> Alignment.CenterHorizontally
        else -> Alignment.Start
    }
}
