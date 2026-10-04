package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.OutlinedText
import org.churchpresenter.sharedui.composables.backdropRoom
import org.churchpresenter.sharedui.composables.rememberTextBackdropPainter
import org.churchpresenter.settings.StageMonitorContentType
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorZoneStyle
import org.churchpresenter.dictionary.presenter.DictionaryPresenter
import org.churchpresenter.qa.presenter.QAPresenter
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.sharedui.utils.calculateAutoFitFontSize
import org.churchpresenter.sharedui.utils.calculateChordChartFontSize
import org.churchpresenter.app.churchpresenter.composables.ChordChart
import org.churchpresenter.media.composables.SoftwareVideoPlayer
import org.churchpresenter.media.viewmodel.MediaViewModel

/**
 * [lines] with every chord moved by [steps] semitones, spelled the way the key they land in is
 * written. The key is the one [ChordTransposer.detectKey] reads from the lines themselves.
 */
internal fun transposeChordLines(lines: List<String>, steps: Int): List<String> {
    if (steps == 0 || lines.isEmpty()) return lines
    val keyPitch = ChordTransposer.pitchOf(ChordTransposer.detectKey(lines.joinToString("\n"))) ?: 0
    val flats = ChordTransposer.prefersFlats(keyPitch + steps)
    return lines.map { ChordTransposer.transposeText(it, steps, flats) }
}

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
                available = IntSize(maxWidth.value.toInt(), maxHeight.value.toInt()),
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

internal fun zoneContentAlignment(style: StageMonitorZoneStyle): Alignment {
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
internal fun ZoneContent(
    sm: StageMonitorSettings,
    content: StageMonitorContentType,
    style: StageMonitorZoneStyle,
    data: ZoneRenderData,
    mediaViewModel: MediaViewModel?
) {
    when (content) {
        // Fitted, like the song beside it: a zone's configured size is a ceiling, and a verse long
        // enough to overflow it is stepped down rather than clipped at the frame edge.
        StageMonitorContentType.BIBLE ->
            ZoneTextTransition(sm, data.currentText) { FittedTextContent(style, it) }
        // A song with chords is shown as its chart; without them it is the words alone, exactly as
        // before. The Next zone follows the same rule for the section coming up.
        StageMonitorContentType.SONGS ->
            ZoneTextTransition(sm, data.currentText) {
                if (data.chordLines.isEmpty()) FittedTextContent(style, it)
                else ZoneChordChart(style, data.chordLines, data.songInfo)
            }
        StageMonitorContentType.PRESENTATION -> SlideContent(data.displayedSlide)
        StageMonitorContentType.PRESENTATION_NOTES ->
            ZoneTextTransition(sm, data.presenterNotes) { ScrollingTextContent(style, it) }
        StageMonitorContentType.PICTURES -> SlideContent(data.currentImageBitmap)
        StageMonitorContentType.MEDIA -> {
            if (mediaViewModel != null && mediaViewModel.isLoaded && !mediaViewModel.isAudioFile) {
                SoftwareVideoPlayer(
                    viewModel = mediaViewModel,
                    modifier = Modifier.fillMaxSize(),
                    audioEnabled = false, // audio is handled by the main output
                    // A mirror of the main output, so it must not report the end of the file
                    // as well -- with looping armed that would spend two repeats per play.
                    reportsPlaybackEnd = false
                )
            }
        }
        StageMonitorContentType.CLOCK -> CenteredText(data.clockText, style)
        StageMonitorContentType.ANNOUNCEMENT_TEXT -> CenteredText(data.timerText, style)
        StageMonitorContentType.CANVAS -> ScenePresenter(modifier = Modifier.fillMaxSize(), scene = data.activeScene)
        StageMonitorContentType.QA -> QAPresenter(question = data.displayedQuestion, qaSettings = data.qaSettings)
        StageMonitorContentType.DICTIONARY -> DictionaryPresenter(
            entry = data.displayedDictionaryEntry,
            dictionarySettings = data.dictionarySettings
        )
        StageMonitorContentType.NEXT ->
            ZoneTextTransition(sm, data.nextText) {
                if (data.nextChordLines.isEmpty()) FittedTextContent(style, it)
                else ZoneChordChart(style, data.nextChordLines)
            }
        // No live data is plumbed through to the stage monitor for these yet.
        StageMonitorContentType.LOWER_THIRD,
        StageMonitorContentType.WEB,
        StageMonitorContentType.STT -> {}
    }
}

/**
 * Fades a zone's text as it changes, on the monitor's own transition settings.
 *
 * Only the zones whose text is content — scripture, lyrics, the look-ahead, presenter notes. The
 * clock and the timer are deliberately left cutting: both retick every second, and a half-second
 * fade on each would never settle.
 */
@Composable
private fun ZoneTextTransition(
    sm: StageMonitorSettings,
    text: String,
    draw: @Composable (String) -> Unit,
) {
    val duration = sm.transitionDuration.toInt()
    if (sm.crossfade) {
        Crossfade(targetState = text, animationSpec = tween(duration)) { draw(it) }
        return
    }
    if (!sm.fadeIn && !sm.fadeOut) {
        draw(text)
        return
    }
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val enter = if (sm.fadeIn) fadeIn(tween(duration)) else EnterTransition.None
            val exit = if (sm.fadeOut) fadeOut(tween(duration)) else ExitTransition.None
            enter togetherWith exit
        },
        label = "stage_zone_text",
    ) { draw(it) }
}

@Composable
private fun TextContent(style: StageMonitorZoneStyle, text: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = resolveColumnVerticalArrangement(style.verticalAlignment),
        horizontalAlignment = resolveColumnHorizontalAlignment(style.horizontalAlignment)
    ) {
        val painter = rememberTextBackdropPainter(style.backdrop)
        OutlinedText(
            text = text,
            outline = style.outline,
            scaleFactor = 1f,
            color = Color.Unspecified,
            fontSize = TextUnit.Unspecified,
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
            modifier = Modifier.fillMaxWidth()
                .backdropRoom(style.backdrop)
                .then(painter.modifier),
            onTextLayout = painter::onTextLayout,
            textAlign = resolveTextAlign(style.horizontalAlignment)
        )
    }
}

@Composable
private fun ScrollingTextContent(style: StageMonitorZoneStyle, text: String) {
    val scrollState = rememberScrollState()
    OutlinedText(
        text = text,
        outline = style.outline,
        scaleFactor = 1f,
        color = Color.Unspecified,
        fontSize = TextUnit.Unspecified,
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
