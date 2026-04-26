package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.churchpresenter.app.churchpresenter.data.StageMonitorSettings
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.composables.SoftwareVideoPlayer
import org.churchpresenter.app.churchpresenter.viewmodel.LocalMediaViewModel
import org.jetbrains.skia.Image as SkiaImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Full-screen stage monitor layout:
 *   ┌───────────────────┬───────────────────┐
 *   │  Top-Left         │  Top-Right        │
 *   │  Current Slide    │  Countdown Timer  │
 *   ├─────────┬─────────┴───────────────────┤
 *   │ Bot-Left│   Bot-Center  │  Bot-Right  │
 *   │ Next    │   Clock       │  Notes      │
 *   └─────────┴───────────────┴─────────────┘
 */
@Composable
fun StageMonitorScreen(
    sm: StageMonitorSettings,
    presentingMode: Presenting,
    currentLyricSection: LyricSection,
    allLyricSections: List<LyricSection>,
    songDisplaySectionIndex: Int,
    displayedVerses: List<SelectedVerse>,
    timerRemainingSeconds: Int,
    timerRunning: Boolean,
    displayedImagePath: String? = null,
    nextImagePath: String? = null,
    displayedSlide: ImageBitmap? = null,
    nextSlide: ImageBitmap? = null,
    announcementText: String = "",
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
        Presenting.ANNOUNCEMENTS -> announcementText
        else -> ""
    }

    // Derive next text (songs only)
    val nextText: String = when (presentingMode) {
        Presenting.LYRICS -> {
            val nextIdx = songDisplaySectionIndex + 1
            allLyricSections.getOrNull(nextIdx)?.lines?.joinToString("\n") ?: ""
        }
        else -> ""
    }

    // Label text (song title or song+section header)
    val currentLabel: String = when (presentingMode) {
        Presenting.LYRICS -> {
            val title = currentLyricSection.title.ifBlank { null }
            val header = currentLyricSection.header?.removePrefix("[")?.removePrefix("{")
                ?.removeSuffix("]")?.removeSuffix("}")?.trim()?.ifBlank { null }
            listOfNotNull(title, header).joinToString(" – ")
        }
        Presenting.BIBLE -> ""
        else -> ""
    }

    // Load image bitmaps for PICTURES mode
    var currentImageBitmap by remember(displayedImagePath) { mutableStateOf<ImageBitmap?>(null) }
    var nextImageBitmap by remember(nextImagePath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(displayedImagePath) {
        currentImageBitmap = loadImageBitmapFromPath(displayedImagePath)
    }
    LaunchedEffect(nextImagePath) {
        nextImageBitmap = loadImageBitmapFromPath(nextImagePath)
    }

    // Clock state — ticks every second
    var clockText by remember { mutableStateOf(formatClock(sm)) }
    LaunchedEffect(sm.clockFormat24h, sm.clockShowSeconds) {
        while (true) {
            clockText = formatClock(sm)
            delay(1000L)
        }
    }

    // Timer text
    val timerText = formatTimer(timerRemainingSeconds)

    val mediaViewModel = LocalMediaViewModel.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP ROW: current slide (left) + timer (right) ───────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // Top-Left: Current Slide
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(parseHexColor(sm.currentBgColor))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    when (presentingMode) {
                        Presenting.PICTURES -> {
                            val bmp = currentImageBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Presenting.PRESENTATION -> {
                            val bmp = displayedSlide
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = resolveColumnVerticalArrangement(sm.currentVerticalAlignment),
                                horizontalAlignment = resolveColumnHorizontalAlignment(sm.currentHorizontalAlignment)
                            ) {
                                if (sm.showSongBibleLabel && currentLabel.isNotBlank()) {
                                    Text(
                                        text = currentLabel,
                                        style = buildTextStyle(
                                            fontType = sm.labelFontType,
                                            fontSize = sm.labelFontSize,
                                            color = parseHexColor(sm.labelColor),
                                            bold = sm.labelBold,
                                            italic = sm.labelItalic
                                        ),
                                        maxLines = 1
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                                Text(
                                    text = currentText,
                                    style = buildTextStyle(
                                        fontType = sm.currentFontType,
                                        fontSize = sm.currentFontSize,
                                        color = parseHexColor(sm.currentColor),
                                        bold = sm.currentBold,
                                        italic = sm.currentItalic,
                                        underline = sm.currentUnderline,
                                        shadow = sm.currentShadow,
                                        shadowColor = parseHexColor(sm.currentShadowColor),
                                        shadowSize = sm.currentShadowSize,
                                        shadowOpacity = sm.currentShadowOpacity
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = resolveTextAlign(sm.currentHorizontalAlignment)
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = Color.DarkGray, thickness = 1.dp)

                // Top-Right: Countdown Timer
                if (sm.showTimer) {
                    Box(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                            .background(parseHexColor(sm.timerBgColor))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (timerRunning || timerRemainingSeconds > 0) timerText else "--:--",
                            style = buildTextStyle(
                                fontType = sm.timerFontType,
                                fontSize = sm.timerFontSize,
                                color = parseHexColor(sm.timerColor),
                                bold = sm.timerBold,
                                italic = sm.timerItalic,
                                underline = sm.timerUnderline,
                                shadow = sm.timerShadow,
                                shadowColor = parseHexColor(sm.timerShadowColor),
                                shadowSize = sm.timerShadowSize,
                                shadowOpacity = sm.timerShadowOpacity
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

            // ── BOTTOM ROW: next slide (left) + clock (center) + notes (right) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
            ) {
                // Bottom-Left: Next Slide
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(parseHexColor(sm.nextBgColor))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    when (presentingMode) {
                        Presenting.PICTURES -> {
                            val bmp = nextImageBitmap
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        Presenting.PRESENTATION -> {
                            val bmp = nextSlide
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = resolveColumnVerticalArrangement(sm.nextVerticalAlignment),
                                horizontalAlignment = resolveColumnHorizontalAlignment(sm.nextHorizontalAlignment)
                            ) {
                                Text(
                                    text = nextText,
                                    style = buildTextStyle(
                                        fontType = sm.nextFontType,
                                        fontSize = sm.nextFontSize,
                                        color = parseHexColor(sm.nextColor),
                                        bold = sm.nextBold,
                                        italic = sm.nextItalic,
                                        underline = sm.nextUnderline,
                                        shadow = sm.nextShadow,
                                        shadowColor = parseHexColor(sm.nextShadowColor),
                                        shadowSize = sm.nextShadowSize,
                                        shadowOpacity = sm.nextShadowOpacity
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = resolveTextAlign(sm.nextHorizontalAlignment)
                                )
                            }
                        }
                    }
                }

                VerticalDivider(color = Color.DarkGray, thickness = 1.dp)

                // Bottom-Center: Clock
                if (sm.showClock) {
                    Box(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .background(parseHexColor(sm.clockBgColor))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = clockText,
                            style = buildTextStyle(
                                fontType = sm.clockFontType,
                                fontSize = sm.clockFontSize,
                                color = parseHexColor(sm.clockColor),
                                bold = sm.clockBold,
                                italic = sm.clockItalic,
                                underline = sm.clockUnderline,
                                shadow = sm.clockShadow,
                                shadowColor = parseHexColor(sm.clockShadowColor),
                                shadowSize = sm.clockShadowSize,
                                shadowOpacity = sm.clockShadowOpacity
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                    VerticalDivider(color = Color.DarkGray, thickness = 1.dp)
                }

                // Bottom-Right: Presenter Notes
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(parseHexColor(sm.notesBgColor))
                        .padding(12.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    val scrollState = rememberScrollState()
                    Text(
                        text = sm.notesText.ifBlank { "" },
                        style = buildTextStyle(
                            fontType = sm.notesFontType,
                            fontSize = sm.notesFontSize,
                            color = parseHexColor(sm.notesColor),
                            bold = sm.notesBold,
                            italic = sm.notesItalic,
                            underline = sm.notesUnderline,
                            shadow = sm.notesShadow,
                            shadowColor = parseHexColor(sm.notesShadowColor),
                            shadowSize = sm.notesShadowSize,
                            shadowOpacity = sm.notesShadowOpacity
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    )
                }
            }
        }

        // ── MEDIA overlay: when playing media, cover the full stage monitor ──
        if (presentingMode == Presenting.MEDIA && mediaViewModel != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (mediaViewModel.isLoaded && !mediaViewModel.isAudioFile) {
                    SoftwareVideoPlayer(
                        viewModel = mediaViewModel,
                        modifier = Modifier.fillMaxSize(),
                        audioEnabled = false // audio is handled by the main output
                    )
                }
            }
        }
    }
}

private fun formatClock(sm: StageMonitorSettings): String {
    val now = LocalTime.now()
    val pattern = when {
        sm.clockFormat24h && sm.clockShowSeconds -> "HH:mm:ss"
        sm.clockFormat24h -> "HH:mm"
        sm.clockShowSeconds -> "hh:mm:ss a"
        else -> "hh:mm a"
    }
    return now.format(DateTimeFormatter.ofPattern(pattern))
}

private fun formatTimer(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
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
            offset = Offset(shadowSize / 10f, shadowSize / 10f),
            blurRadius = shadowSize / 5f
        ) else null
    )
}

private suspend fun loadImageBitmapFromPath(path: String?): ImageBitmap? {
    if (path == null) return null
    return withContext(Dispatchers.IO) {
        try {
            val bytes = File(path).readBytes()
            SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
        } catch (_: Exception) {
            null
        }
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

