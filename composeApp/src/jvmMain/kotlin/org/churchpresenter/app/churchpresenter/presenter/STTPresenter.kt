package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.Utils.parseHexColor
import org.churchpresenter.app.churchpresenter.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.viewmodel.HighlightedWord
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment

@Composable
fun STTPresenter(
    modifier: Modifier = Modifier,
    segments: List<STTSegment>,
    inProgressText: String,
    translationSegments: List<STTSegment>,
    inProgressTranslation: String,
    highlightedWords: List<HighlightedWord>,
    sttSettings: STTSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
) {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val textColor = if (isKey) Color.White else parseHexColor(sttSettings.textColor)
    val translationColor = if (isKey) Color.White else parseHexColor(sttSettings.translationTextColor)
    val bgOpacity = (sttSettings.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val cardBg = if (isKey) Color.White
                 else parseHexColor(if (
                     sttSettings.backgroundColor == "transparent"
                 ) "#1E1E2E" else sttSettings.backgroundColor).copy(alpha = bgOpacity)
    val fontFamily = systemFontFamilyOrDefault(sttSettings.fontType)

    val shadowColorBase = parseHexColor(sttSettings.shadowColor)
    val shadowSizeMul = sttSettings.shadowSize / 100f
    val shadowAlpha = (sttSettings.shadowOpacity / 100f).coerceIn(0f, 1f)
    val sttShadow = Shadow(
        color = shadowColorBase.copy(alpha = shadowAlpha),
        offset = Offset(4f * shadowSizeMul, 4f * shadowSizeMul),
        blurRadius = 8f * shadowSizeMul
    )

    val lineHeightSp = (sttSettings.fontSize * sttSettings.lineSpacing / 100f).sp

    val baseTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = if (sttSettings.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (sttSettings.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (sttSettings.underline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (sttSettings.shadow) sttShadow else null,
        textAlign = when {
            sttSettings.position.contains("Left") -> TextAlign.Left
            sttSettings.position.contains("Right") -> TextAlign.Right
            else -> TextAlign.Center
        },
        fontSize = sttSettings.fontSize.sp,
        lineHeight = lineHeightSp
    )

    val boxAlignment = sttPositionToAlignment(sttSettings.position)

    // Prepare text content based on display mode
    val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
    val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

    // Drip feed: reveal newest segment letter-by-letter
    val dripEnabled = sttSettings.dripFeedEnabled
    val dripSpeed = sttSettings.dripFeedSpeed.toLong().coerceAtLeast(1L)
    val dripTranscription = useDripFeed(
        segments,
        enabled = dripEnabled && !sttSettings.showInProgress,
        delayMs = dripSpeed
    )
    val dripTranslation = useDripFeed(
        translationSegments,
        enabled = dripEnabled && !sttSettings.showTranslationInProgress,
        delayMs = dripSpeed
    )

    // Build text — pass ALL segments, no filtering by maxSegments
    val transcriptionText = buildDisplayText(
        dripTranscription, inProgressText, sttSettings.showInProgress,
        highlightedWords, sttSettings.showWordHighlighting, textColor
    )
    val translationText = buildDisplayText(
        dripTranslation, inProgressTranslation, sttSettings.showTranslationInProgress,
        highlightedWords, sttSettings.showWordHighlighting, translationColor
    )

    val isBothMode = showTranscription && showTranslation
    val isSideBySide = sttSettings.layout == "side_by_side" || sttSettings.layout == "side_by_side_inverse"
    val isInverse = sttSettings.layout == "stacked_inverse" || sttSettings.layout == "side_by_side_inverse"
    val maxLines = sttSettings.maxLines

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = boxAlignment
    ) {
        if (transcriptionText.isNotEmpty() || translationText.isNotEmpty() || isBothMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBg)
                    .padding(24.dp)
            ) {
                if (isBothMode) {
                    val first = if (isInverse) translationText else transcriptionText
                    val firstStyle = baseTextStyle.copy(color = if (isInverse) translationColor else textColor)
                    val second = if (isInverse) transcriptionText else translationText
                    val secondStyle = baseTextStyle.copy(color = if (isInverse) textColor else translationColor)

                    if (isSideBySide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            BottomAlignedText(
                                text = first,
                                style = firstStyle,
                                maxLines = maxLines,
                                modifier = Modifier.weight(1f)
                            )
                            BottomAlignedText(
                                text = second,
                                style = secondStyle,
                                maxLines = maxLines,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = boxAlignment) {
                                BottomAlignedText(
                                    text = first,
                                    style = firstStyle,
                                    maxLines = maxLines,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = boxAlignment) {
                                BottomAlignedText(
                                    text = second,
                                    style = secondStyle,
                                    maxLines = maxLines,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                } else {
                    val displayText = when {
                        showTranscription && transcriptionText.isNotEmpty() -> transcriptionText
                        showTranslation && translationText.isNotEmpty() -> translationText
                        transcriptionText.isNotEmpty() -> transcriptionText
                        else -> translationText
                    }
                    val displayColor = when {
                        showTranscription && transcriptionText.isNotEmpty() -> textColor
                        showTranslation && translationText.isNotEmpty() -> translationColor
                        else -> textColor
                    }
                    BottomAlignedText(
                        text = displayText,
                        style = baseTextStyle.copy(color = displayColor),
                        maxLines = maxLines,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * Shows text clipped to the last N lines. Content is bottom-aligned —
 * when text exceeds maxLines, old lines are clipped off the top.
 * The text is shifted upward so the last line sits at the bottom of the clip area.
 */
@Composable
private fun BottomAlignedText(
    text: AnnotatedString,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier
) {
    if (maxLines <= 0) {
        Text(text = text, style = style, modifier = modifier.fillMaxWidth())
        return
    }

    // Reference text with exactly maxLines lines — measured to get precise pixel height
    val referenceText = remember(maxLines) { "\n".repeat(maxLines - 1).ifEmpty { " " } }

    Layout(
        content = {
            // Invisible reference: measures exact height of maxLines lines
            Text(
                text = referenceText,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines
            )
            // Actual content: measured unconstrained
            Text(text = text, style = style, modifier = Modifier.fillMaxWidth())
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        val unconstrainedConstraints = Constraints(
            minWidth = constraints.minWidth,
            maxWidth = constraints.maxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )
        // Measure reference to get exact N-line height
        val refPlaceable = measurables[0].measure(unconstrainedConstraints)
        val clipHeightPx = refPlaceable.height

        // Measure actual text at full height
        val textPlaceable = measurables[1].measure(unconstrainedConstraints)

        val reportedHeight = clipHeightPx.coerceAtMost(textPlaceable.height)
        layout(constraints.maxWidth, reportedHeight) {
            // Place text bottom-aligned: shift up so last lines are visible
            val y = reportedHeight - textPlaceable.height
            textPlaceable.place(0, y.coerceAtMost(0))
            // Don't place reference — it's just for measurement
        }
    }
}

private fun buildDisplayText(
    segments: List<STTSegment>,
    inProgressText: String,
    showInProgress: Boolean,
    highlightedWords: List<HighlightedWord>,
    showWordHighlighting: Boolean,
    baseColor: Color
): AnnotatedString {
    // Same shaping the drip-feed cursor counts against — see captionText in SttDripFeed.kt.
    val lines = mutableListOf<String>()
    captionText(segments).takeIf { it.isNotEmpty() }?.let { lines.add(it) }
    if (showInProgress && inProgressText.isNotBlank()) {
        lines.add(normalizeSegmentText(inProgressText))
    }

    if (lines.isEmpty()) return AnnotatedString("")

    val fullText = lines.joinToString(" ")

    // Build per-character color array then construct contiguous runs
    val colors = Array(fullText.length) { baseColor }

    // Dim in-progress text
    if (showInProgress && inProgressText.isNotBlank() && segments.isNotEmpty()) {
        val inProgressStart = fullText.length - inProgressText.trim().length
        if (inProgressStart >= 0) {
            for (j in inProgressStart until fullText.length) colors[j] = baseColor.copy(alpha = 0.6f)
        }
    }

    // Apply word highlighting with Unicode word boundaries
    if (showWordHighlighting) {
        highlightedWords.forEach { applyHighlight(it, fullText, colors) }
    }

    return runsOf(fullText, colors)
}

/** Paints every match of one highlighted word into [colors]. A pattern that won't compile is skipped. */
private fun applyHighlight(hw: HighlightedWord, fullText: String, colors: Array<Color>) {
    if (hw.word.isBlank()) return
    try {
        val highlightColor = parseHexColor(hw.color)
        val wb = "(?<![\\p{L}\\p{N}])"
        val we = "(?![\\p{L}\\p{N}])"
        val rawPattern = if (hw.isRegex) "$wb(?:${hw.word})$we" else "$wb${Regex.escape(hw.word)}$we"
        var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
        if (!hw.caseSensitive) {
            flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
        }
        java.util.regex.Pattern.compile(rawPattern, flags).toRegex().findAll(fullText).forEach { match ->
            for (j in match.range) colors[j] = highlightColor
        }
    } catch (_: Exception) {}
}

/** The per-character colours collapsed into contiguous styled runs. */
private fun runsOf(fullText: String, colors: Array<Color>): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < fullText.length) {
        val color = colors[i]
        val start = i
        while (i < fullText.length && colors[i] == color) i++
        withStyle(SpanStyle(color = color)) {
            append(fullText.substring(start, i))
        }
    }
}

/**
 * Drip feed: reveals the caption letter-by-letter (ChatGPT-style) at [delayMs] per character.
 *
 * ONE cursor runs over the whole caption rather than over the newest segment, so a segment arriving
 * mid-reveal extends the text to type out instead of snapping its predecessor to full. The cursor
 * starts at the end of whatever is already on screen — an output opened mid-service shows the
 * backlog it inherits, it does not re-type it. Speed changes apply to the reveal in flight, since
 * the effect is keyed on [delayMs].
 *
 * The character arithmetic lives in `SttDripFeed.kt`.
 */
@Composable
private fun useDripFeed(segments: List<STTSegment>, enabled: Boolean, delayMs: Long): List<STTSegment> {
    if (!enabled) return segments

    val fullText = captionText(segments)
    val latestFullText = rememberUpdatedState(fullText)
    val revealed = remember { mutableIntStateOf(fullText.length) }

    LaunchedEffect(delayMs) {
        var previous = latestFullText.value
        snapshotFlow { latestFullText.value }.collectLatest { current ->
            if (current != previous) {
                revealed.intValue = reanchorCursor(previous, revealed.intValue, current)
                previous = current
            }
            while (revealed.intValue < current.length) {
                delay(delayMs)
                revealed.intValue = minOf(
                    current.length,
                    revealed.intValue + revealStep(revealed.intValue, current.length)
                )
            }
        }
    }

    if (revealed.intValue >= fullText.length) return segments
    return applyRevealBudget(segments, revealed.intValue)
}

private fun sttPositionToAlignment(position: String): Alignment = when (position) {
    Constants.TOP_LEFT -> Alignment.TopStart
    Constants.TOP_CENTER -> Alignment.TopCenter
    Constants.TOP_RIGHT -> Alignment.TopEnd
    Constants.CENTER_LEFT -> Alignment.CenterStart
    Constants.CENTER -> Alignment.Center
    Constants.CENTER_RIGHT -> Alignment.CenterEnd
    Constants.BOTTOM_LEFT -> Alignment.BottomStart
    Constants.BOTTOM_CENTER -> Alignment.BottomCenter
    Constants.BOTTOM_RIGHT -> Alignment.BottomEnd
    Constants.BOTTOM -> Alignment.BottomCenter
    Constants.TOP -> Alignment.TopCenter
    Constants.MIDDLE -> Alignment.Center
    else -> Alignment.BottomCenter
}
