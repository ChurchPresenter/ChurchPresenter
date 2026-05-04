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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.app.churchpresenter.data.STTSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
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
    val cardBg = if (isKey) Color.White
                 else if (sttSettings.backgroundColor == "transparent") Color.Transparent
                 else parseHexColor(sttSettings.backgroundColor)
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

    // Drip feed: reveal newest segment word-by-word
    val dripEnabled = sttSettings.dripFeedEnabled
    val dripSpeed = sttSettings.dripFeedSpeed.toLong().coerceAtLeast(10L)
    val dripTranscription = useDripFeed(segments, enabled = dripEnabled && !sttSettings.showInProgress, delayMs = dripSpeed)
    val dripTranslation = useDripFeed(translationSegments, enabled = dripEnabled && !sttSettings.showTranslationInProgress, delayMs = dripSpeed)

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
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            BottomAlignedText(text = first, style = firstStyle, maxLines = maxLines, modifier = Modifier.weight(1f))
                            BottomAlignedText(text = second, style = secondStyle, maxLines = maxLines, modifier = Modifier.weight(1f))
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            BottomAlignedText(text = first, style = firstStyle, maxLines = maxLines, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(16.dp))
                            BottomAlignedText(text = second, style = secondStyle, maxLines = maxLines, modifier = Modifier.fillMaxWidth())
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

    val density = LocalDensity.current
    val lineHeightPx = with(density) { style.lineHeight.toPx() }
    val clipHeightPx = lineHeightPx * maxLines
    val clipHeight = with(density) { clipHeightPx.toDp() }

    val clipHeightPxInt = clipHeightPx.toInt()

    androidx.compose.ui.layout.Layout(
        content = {
            Text(text = text, style = style, modifier = Modifier.fillMaxWidth())
        },
        modifier = modifier.clipToBounds()
    ) { measurables, constraints ->
        // Measure text unconstrained vertically so we get its full height
        val placeable = measurables.first().measure(
            androidx.compose.ui.unit.Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = androidx.compose.ui.unit.Constraints.Infinity
            )
        )
        // Report only clipHeight to parent so the card wraps tightly
        val reportedHeight = clipHeightPxInt.coerceAtMost(placeable.height)
        layout(constraints.maxWidth, reportedHeight) {
            // Place text so its bottom edge aligns with the bottom of reported height
            val y = reportedHeight - placeable.height
            placeable.place(0, y.coerceAtMost(0))
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
    val lines = mutableListOf<String>()
    for (seg in segments) {
        if (seg.text.isNotBlank()) {
            lines.add(seg.text.trim().replace(Regex("\\s+"), " "))
        }
    }
    if (showInProgress && inProgressText.isNotBlank()) {
        lines.add(inProgressText.trim().replace(Regex("\\s+"), " "))
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
    if (showWordHighlighting && highlightedWords.isNotEmpty()) {
        for (hw in highlightedWords) {
            if (hw.word.isBlank()) continue
            try {
                val highlightColor = parseHexColor(hw.color)
                val wb = "(?<![\\p{L}\\p{N}])"
                val we = "(?![\\p{L}\\p{N}])"
                val rawPattern = if (hw.isRegex) {
                    "$wb(?:${hw.word})$we"
                } else {
                    val escaped = Regex.escape(hw.word)
                    "$wb$escaped$we"
                }
                var flags = java.util.regex.Pattern.UNICODE_CHARACTER_CLASS
                if (!hw.caseSensitive) flags = flags or java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
                val regex = java.util.regex.Pattern.compile(rawPattern, flags).toRegex()
                regex.findAll(fullText).forEach { match ->
                    for (j in match.range) colors[j] = highlightColor
                }
            } catch (_: Exception) {}
        }
    }

    // Build AnnotatedString with contiguous color runs
    return buildAnnotatedString {
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
}

/**
 * Drip feed: reveals the newest segment word-by-word (ChatGPT-style).
 * Returns the segment list with the last segment's text partially revealed.
 */
@Composable
private fun useDripFeed(segments: List<STTSegment>, enabled: Boolean, delayMs: Long = 40L): List<STTSegment> {
    if (!enabled || segments.isEmpty()) return segments

    val lastSegment = segments.last()
    val newWords = remember(lastSegment.id, lastSegment.text) {
        lastSegment.text.trim().split(Regex("\\s+"))
    }

    var revealedForSegmentId by remember { mutableIntStateOf(lastSegment.id) }
    var revealedWordCount by remember { mutableIntStateOf(Int.MAX_VALUE) }

    // If segment changed but LaunchedEffect hasn't fired, show 0 words
    val effectiveRevealed = if (revealedForSegmentId != lastSegment.id) 0 else revealedWordCount

    LaunchedEffect(lastSegment.id, lastSegment.text) {
        revealedForSegmentId = lastSegment.id
        revealedWordCount = 0
        for (i in 1..newWords.size) {
            delay(delayMs)
            revealedWordCount = i
        }
    }

    if (effectiveRevealed >= newWords.size) return segments

    val revealedText = newWords.take(effectiveRevealed).joinToString(" ")
    return segments.dropLast(1) + lastSegment.copy(text = revealedText)
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
