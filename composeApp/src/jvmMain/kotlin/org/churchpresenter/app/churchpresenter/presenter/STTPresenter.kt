package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
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
        fontSize = sttSettings.fontSize.sp
    )

    val boxAlignment = sttPositionToAlignment(sttSettings.position)

    // Prepare text content based on display mode
    val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
    val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

    // Filter to last N segments
    val maxSeg = sttSettings.maxSegments
    val filteredSegments = if (maxSeg > 0) segments.takeLast(maxSeg) else segments
    val filteredTranslationSegments = if (maxSeg > 0) translationSegments.takeLast(maxSeg) else translationSegments

    // Drip feed: when in-progress is off, reveal newest segment word-by-word
    val dripEnabled = sttSettings.dripFeedEnabled
    val dripSpeed = sttSettings.dripFeedSpeed.toLong().coerceAtLeast(10L)
    val dripResultTranscription = useDripFeed(filteredSegments, enabled = dripEnabled && !sttSettings.showInProgress, delayMs = dripSpeed)
    val dripResultTranslation = useDripFeed(filteredTranslationSegments, enabled = dripEnabled && !sttSettings.showTranslationInProgress, delayMs = dripSpeed)

    // Build transcription text
    val transcriptionText = buildDisplayText(
        dripResultTranscription.segments, inProgressText, sttSettings.showInProgress,
        highlightedWords, sttSettings.showWordHighlighting, textColor,
        outgoingText = dripResultTranscription.outgoingText,
        outgoingFadedWords = dripResultTranscription.outgoingFadedWords
    )
    // Build translation text
    val translationText = buildDisplayText(
        dripResultTranslation.segments, inProgressTranslation, sttSettings.showTranslationInProgress,
        highlightedWords, sttSettings.showWordHighlighting, translationColor,
        outgoingText = dripResultTranslation.outgoingText,
        outgoingFadedWords = dripResultTranslation.outgoingFadedWords
    )

    val isBothMode = showTranscription && showTranslation
    val isSideBySide = sttSettings.layout == "side_by_side" || sttSettings.layout == "side_by_side_inverse"
    val isInverse = sttSettings.layout == "stacked_inverse" || sttSettings.layout == "side_by_side_inverse"

    // Auto-scroll to bottom so newest text is always visible
    val scrollState = rememberScrollState()
    LaunchedEffect(transcriptionText, translationText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = boxAlignment
    ) {
        if (transcriptionText.isNotEmpty() || translationText.isNotEmpty() || isBothMode) {
            val maxCardHeight = with(LocalDensity.current) {
                (constraints.maxHeight.toDp() - 64.dp)
            }

            Box(
                modifier = Modifier
                    .padding(32.dp)
                    .heightIn(max = maxCardHeight)
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
                        // Side by side: 50/50 horizontal split
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            val scrollState1 = rememberScrollState()
                            val scrollState2 = rememberScrollState()
                            LaunchedEffect(first) { scrollState1.animateScrollTo(scrollState1.maxValue) }
                            LaunchedEffect(second) { scrollState2.animateScrollTo(scrollState2.maxValue) }

                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState1),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(text = first, style = firstStyle, modifier = Modifier.fillMaxWidth())
                            }
                            Column(
                                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(scrollState2),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(text = second, style = secondStyle, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        // Stacked: 50/50 vertical split
                        Column(modifier = Modifier.fillMaxSize()) {
                            val scrollState1 = rememberScrollState()
                            val scrollState2 = rememberScrollState()
                            LaunchedEffect(first) { scrollState1.animateScrollTo(scrollState1.maxValue) }
                            LaunchedEffect(second) { scrollState2.animateScrollTo(scrollState2.maxValue) }

                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState1),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(text = first, style = firstStyle, modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState2),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(text = second, style = secondStyle, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                } else {
                    // Single mode
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState),
                        verticalArrangement = Arrangement.Bottom
                    ) {
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
                        Text(
                            text = displayText,
                            style = baseTextStyle.copy(color = displayColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

private fun buildDisplayText(
    segments: List<STTSegment>,
    inProgressText: String,
    showInProgress: Boolean,
    highlightedWords: List<HighlightedWord>,
    showWordHighlighting: Boolean,
    baseColor: Color,
    outgoingText: String = "",
    outgoingFadedWords: Int = 0
): AnnotatedString {
    val lines = mutableListOf<String>()
    for (seg in segments) {
        if (seg.text.isNotBlank()) {
            // Normalize whitespace: collapse multiple spaces/tabs into single space
            lines.add(seg.text.trim().replace(Regex("\\s+"), " "))
        }
    }
    if (showInProgress && inProgressText.isNotBlank()) {
        lines.add(inProgressText.trim().replace(Regex("\\s+"), " "))
    }

    if (lines.isEmpty()) return AnnotatedString("")

    val fullText = lines.joinToString(" ")

    // Build a per-character color array, then construct AnnotatedString in contiguous runs.
    // This avoids overlapping SpanStyle spans which cause spacing artifacts in Compose.
    val colors = Array(fullText.length) { baseColor }

    // Dim in-progress text
    if (showInProgress && inProgressText.isNotBlank() && segments.isNotEmpty()) {
        val inProgressStart = fullText.length - inProgressText.trim().length
        if (inProgressStart >= 0) {
            for (j in inProgressStart until fullText.length) colors[j] = baseColor.copy(alpha = 0.6f)
        }
    }

    // Apply word highlighting — use word boundaries for non-regex patterns
    if (showWordHighlighting && highlightedWords.isNotEmpty()) {
        for (hw in highlightedWords) {
            if (hw.word.isBlank()) continue
            try {
                val highlightColor = parseHexColor(hw.color)
                val regex = if (hw.isRegex) {
                    if (hw.caseSensitive) Regex(hw.word) else Regex(hw.word, RegexOption.IGNORE_CASE)
                } else {
                    val escaped = Regex.escape(hw.word)
                    val pattern = "\\b$escaped\\b"
                    if (hw.caseSensitive) Regex(pattern) else Regex(pattern, RegexOption.IGNORE_CASE)
                }
                regex.findAll(fullText).forEach { match ->
                    for (j in match.range) colors[j] = highlightColor
                }
            } catch (_: Exception) {}
        }
    }

    // Make faded outgoing words transparent (they still occupy space)
    if (outgoingText.isNotBlank() && outgoingFadedWords > 0) {
        val outgoingWords = outgoingText.trim().split(Regex("\\s+"))
        var charPos = 0
        for (i in 0 until outgoingFadedWords.coerceAtMost(outgoingWords.size)) {
            val word = outgoingWords[i]
            val wordStart = fullText.indexOf(word, charPos)
            if (wordStart >= 0) {
                for (j in wordStart until wordStart + word.length) colors[j] = Color.Transparent
                charPos = wordStart + word.length
                if (charPos < fullText.length && fullText[charPos] == ' ') {
                    colors[charPos] = Color.Transparent
                    charPos++
                }
            }
        }
    }

    // Build AnnotatedString with contiguous color runs (no overlapping spans)
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
 * Result of drip feed: segments to display plus info about fading words.
 * @param segments The segments to render
 * @param outgoingText Full text of the outgoing segment (fading out)
 * @param outgoingFadedWords How many words from the start of outgoingText are now transparent
 * @param outgoingComplete True when all words are faded and the segment should be removed
 */
private data class DripFeedResult(
    val segments: List<STTSegment>,
    val outgoingText: String = "",
    val outgoingFadedWords: Int = 0,
    val outgoingComplete: Boolean = true,
)

/**
 * Drip feed effect: reveals the newest segment word-by-word (ChatGPT-style).
 * When a segment is pushed out (due to maxSegments), its words fade to transparent
 * one by one from the start. The segment is only removed once all words are transparent.
 */
@Composable
private fun useDripFeed(segments: List<STTSegment>, enabled: Boolean, delayMs: Long = 40L): DripFeedResult {
    if (!enabled || segments.isEmpty()) return DripFeedResult(segments)

    // Track outgoing segments — keep multiple fully-transparent segments to prevent reflow.
    val maxRetained = (segments.size * 4).coerceAtLeast(8)

    // All outgoing texts (fully transparent, just holding layout space)
    var retainedOutgoing by remember { mutableStateOf<List<String>>(emptyList()) }

    // Currently-animating outgoing segment
    var outgoingFullText by remember { mutableStateOf("") }
    var outgoingWordCount by remember { mutableIntStateOf(0) }
    var outgoingFadedWords by remember { mutableIntStateOf(0) }

    val lastSegment = segments.last()
    val newWords = remember(lastSegment.id, lastSegment.text) {
        lastSegment.text.trim().split(Regex("\\s+"))
    }

    // Track which segment revealedWordCount belongs to
    var revealedForSegmentId by remember { mutableIntStateOf(lastSegment.id) }
    var revealedWordCount by remember { mutableIntStateOf(Int.MAX_VALUE) }

    // Synchronously detect segment changes during composition (no frame delay).
    // This uses remember with keys — when keys change, the block re-runs immediately.
    val currentFirstId = segments.first().id
    val currentLastId = lastSegment.id
    var prevFirstId by remember { mutableIntStateOf(currentFirstId) }
    var prevFirstText by remember { mutableStateOf(segments.first().text) }
    var prevLastId by remember { mutableIntStateOf(currentLastId) }

    // Detect pushed-out segment SYNCHRONOUSLY during composition
    if (currentLastId != prevLastId) {
        // New segment arrived — capture outgoing immediately (no LaunchedEffect delay)
        if (prevFirstId != currentFirstId && prevFirstText.isNotBlank()) {
            // Move any existing outgoing to retained
            if (outgoingFullText.isNotBlank()) {
                retainedOutgoing = (retainedOutgoing + outgoingFullText).takeLast(maxRetained)
            }
            outgoingFullText = prevFirstText
            outgoingWordCount = prevFirstText.trim().split(Regex("\\s+")).size
            outgoingFadedWords = 0
        }
        prevFirstId = currentFirstId
        prevFirstText = segments.first().text
        prevLastId = currentLastId
    } else if (currentFirstId != prevFirstId) {
        // First segment changed without last changing (shouldn't normally happen, but handle it)
        prevFirstId = currentFirstId
        prevFirstText = segments.first().text
    }

    // If the segment changed but animation hasn't started yet, show 0 words
    val effectiveRevealed = if (revealedForSegmentId != lastSegment.id) 0 else revealedWordCount

    // Animation coroutine — only handles the timing/counting
    LaunchedEffect(lastSegment.id, lastSegment.text) {
        revealedForSegmentId = lastSegment.id
        revealedWordCount = 0
        val totalNew = newWords.size
        val totalOut = outgoingWordCount
        val maxSteps = maxOf(totalNew, totalOut)

        for (i in 1..maxSteps) {
            delay(delayMs)
            if (i <= totalNew) revealedWordCount = i
            if (i <= totalOut) outgoingFadedWords = i
        }
    }

    // Build result segments
    val result = mutableListOf<STTSegment>()

    // Add retained (fully transparent) segments — these keep layout stable
    retainedOutgoing.forEach { text ->
        result.add(STTSegment(id = -2, timestamp = "", text = text, start = 0.0, end = 0.0, completed = true))
    }

    // Add currently-animating outgoing segment
    if (outgoingFullText.isNotBlank()) {
        result.add(STTSegment(id = -1, timestamp = "", text = outgoingFullText, start = 0.0, end = 0.0, completed = true))
    }

    // Add current segments (except last which we'll modify)
    if (segments.size > 1) {
        result.addAll(segments.dropLast(1))
    }

    // Add newest segment (partially revealed or fully revealed)
    if (effectiveRevealed >= newWords.size) {
        result.add(lastSegment)
    } else {
        val revealedText = newWords.take(effectiveRevealed).joinToString(" ")
        result.add(lastSegment.copy(text = revealedText))
    }

    // Combine all outgoing text (retained + current) for fade styling
    val allOutgoingText = buildString {
        retainedOutgoing.forEach { text ->
            if (isNotEmpty()) append(" ")
            append(text.trim())
        }
        if (outgoingFullText.isNotBlank()) {
            if (isNotEmpty()) append(" ")
            append(outgoingFullText.trim())
        }
    }
    // All retained words are fully faded; current outgoing has partial fade
    val retainedWordTotal = retainedOutgoing.sumOf { it.trim().split(Regex("\\s+")).size }
    val totalFaded = retainedWordTotal + outgoingFadedWords

    return DripFeedResult(
        segments = result,
        outgoingText = allOutgoingText,
        outgoingFadedWords = totalFaded,
        outgoingComplete = allOutgoingText.isBlank()
    )
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
