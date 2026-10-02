package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.settings.CAPTION_STYLE_TICKER
import org.churchpresenter.settings.CAPTION_TRANSCRIPT_BOX
import org.churchpresenter.settings.CAPTION_TRANSLATION_BOX
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.boxAt
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.utils.Utils.parseHexColor
import org.churchpresenter.sharedui.utils.Utils.systemFontFamilyOrDefault
import org.churchpresenter.app.churchpresenter.viewmodel.HighlightedWord
import org.churchpresenter.app.churchpresenter.viewmodel.STTSegment
import org.churchpresenter.sharedui.presenter.BoxedItem
import org.churchpresenter.sharedui.presenter.rectIn
import org.churchpresenter.sharedui.presenter.sttPositionToAlignment

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
                     sttSettings.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT
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
        lineHeight = lineHeightSp,
        letterSpacing = spacingEm(sttSettings.letterSpacing, sttSettings.fontSize).em,
    )
    val transcriptLook = baseTextStyle.copy(color = textColor)
    val translationLook = translationTextStyle(baseTextStyle, sttSettings).copy(color = translationColor)
    val reading = sttSettings.reading

    val boxAlignment = sttPositionToAlignment(sttSettings.position)

    // Prepare text content based on display mode
    val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
    val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

    // Drip feed and the reading-speed limit: the newest words revealed at a pace, not all at once
    val pace = revealPace(sttSettings)
    val dripTranscription = useDripFeed(segments, pace.takeIf { !sttSettings.showInProgress })
    val dripTranslation = useDripFeed(translationSegments, pace.takeIf { !sttSettings.showTranslationInProgress })

    // Only the newest [STTSettings.maxSegments] of them (0 keeps every one): how much of the
    // running transcript this output keeps is set per profile. `maxLines` still trims whatever is
    // left to what fits.
    val keptTranscription = keepNewest(dripTranscription, sttSettings.maxSegments)
    val keptTranslation = keepNewest(dripTranslation, sttSettings.maxSegments)
    val wordEm = spacingEm(sttSettings.wordSpacing, sttSettings.fontSize)
    val spaceTrackingEm = (spacingEm(sttSettings.letterSpacing, sttSettings.fontSize) + wordEm).takeIf { wordEm != 0f }
    // A ticker only ever adds words, so it never shows the ones still being rewritten
    val ticker = reading.style == CAPTION_STYLE_TICKER
    val highlights = highlightedWords.takeIf { sttSettings.showWordHighlighting }.orEmpty()
    val transcriptInk = CaptionInk(textColor, highlights, spaceTrackingEm)
    val translationInk = CaptionInk(translationColor, highlights, spaceTrackingEm)
    val transcriptionText = buildDisplayText(
        captionBody(
            keptTranscription, inProgressText.takeIf { sttSettings.showInProgress && !ticker }, reading,
            sttSettings.transcriptAllCaps,
        ),
        keptTranscription.isNotEmpty(), reading, transcriptInk,
    )
    val translationText = buildDisplayText(
        captionBody(
            keptTranslation, inProgressTranslation.takeIf { sttSettings.showTranslationInProgress && !ticker }, reading,
            sttSettings.translationAllCaps,
        ),
        keptTranslation.isNotEmpty(), reading, translationInk,
    )
    val silenceFade = rememberSilenceFade(transcriptionText.text + "\u0000" + translationText.text, reading)
    val faded = modifier.graphicsLayer {
        alpha = silenceFade.value
    }

    val isBothMode = showTranscription && showTranslation
    // A ticker is one line, so two of them always stack
    val isSideBySide = (sttSettings.layout == "side_by_side" || sttSettings.layout == "side_by_side_inverse") &&
        reading.style != CAPTION_STYLE_TICKER
    val isInverse = sttSettings.layout.endsWith("_inverse")
    val interleavedText = if (isBothMode && sttSettings.layout.startsWith(LAYOUT_INTERLEAVED)) {
        interleavedCaption(
            CaptionSide(
                keptTranscription, inProgressText.takeIf { sttSettings.showInProgress && !ticker }, transcriptInk,
                sttSettings.transcriptAllCaps,
            ),
            CaptionSide(
                keptTranslation, inProgressTranslation.takeIf { sttSettings.showTranslationInProgress && !ticker },
                translationInk, sttSettings.translationAllCaps,
            ),
            translationLook, translationFirst = isInverse, sttSettings,
        )
    } else {
        null
    }
    val maxLines = sttSettings.maxLines

    val transcriptBox = sttSettings.textBoxes.boxAt(textBoxKey(CAPTION_TRANSCRIPT_BOX, lowerThird = false))
    val translationBox = sttSettings.textBoxes.boxAt(textBoxKey(CAPTION_TRANSLATION_BOX, lowerThird = false))
    if (transcriptBox.enabled || translationBox.enabled) {
        // Each part the output shows, in its own box where it has one and in the usual card where
        // it does not. Captions are live, so a box is their room rather than a size to fit: the
        // line limit still decides how much of the running text is kept.
        val parts = buildList {
            if (showTranscription) {
                add(CaptionPart(CAPTION_TRANSCRIPT_BOX, transcriptionText, transcriptLook, transcriptBox))
            }
            if (showTranslation) {
                add(CaptionPart(CAPTION_TRANSLATION_BOX, translationText, translationLook, translationBox))
            }
        }
        BoxedCaptions(parts, cardBg, sttSettings, boxAlignment, faded)
        return
    }

    BoxWithConstraints(
        modifier = faded.fillMaxSize().captionMargins(sttSettings),
        contentAlignment = boxAlignment
    ) {
        when {
            interleavedText != null -> CaptionCard(sttSettings, cardBg) {
                CaptionLines(interleavedText, transcriptLook, sttSettings, Modifier.fillMaxWidth())
            }
            isBothMode -> BothLanguages(
                first = if (isInverse) translationText else transcriptionText,
                firstStyle = if (isInverse) translationLook else transcriptLook,
                second = if (isInverse) transcriptionText else translationText,
                secondStyle = if (isInverse) transcriptLook else translationLook,
                s = sttSettings,
                cardBg = cardBg,
                sideBySide = isSideBySide,
                alignment = boxAlignment,
            )
            transcriptionText.isNotEmpty() || translationText.isNotEmpty() -> CaptionCard(sttSettings, cardBg) {
                val showOwn = showTranscription && transcriptionText.isNotEmpty() ||
                    !(showTranslation && translationText.isNotEmpty()) && transcriptionText.isNotEmpty()
                CaptionLines(
                    text = if (showOwn) transcriptionText else translationText,
                    style = if (showOwn) transcriptLook else translationLook,
                    s = sttSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** One part of the captions -- the transcript or the translation -- with its text, colour and box. */
private class CaptionPart(val key: String, val text: AnnotatedString, val style: TextStyle, val box: TextBox)

/** [parts] each in a card of its own: boxed parts in their box, the rest where the page puts captions. */
@Composable
private fun BoxedCaptions(
    parts: List<CaptionPart>,
    cardBg: Color,
    sttSettings: STTSettings,
    alignment: Alignment,
    modifier: Modifier,
) {
    val horizontal = when {
        sttSettings.position.contains("Left") -> Constants.LEFT
        sttSettings.position.contains("Right") -> Constants.RIGHT
        else -> Constants.CENTER
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val area = Rect(0f, 0f, maxWidth.value, maxHeight.value)
        val card: @Composable (CaptionPart) -> Unit = { part ->
            Box(
                modifier = Modifier
                    .captionCard(sttSettings, cardBg, inTextBox = true),
            ) {
                CaptionLines(
                    text = part.text,
                    style = part.style,
                    s = sttSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        parts.filter { it.box.enabled && it.text.isNotEmpty() }.forEach { part ->
            BoxedItem(part.box.rectIn(area), part.box, horizontal, textBoxKey(part.key, false)) { card(part) }
        }
        val rest = parts.filter { !it.box.enabled && it.text.isNotEmpty() }
        if (rest.isNotEmpty()) {
            Box(Modifier.fillMaxSize().captionMargins(sttSettings), contentAlignment = alignment) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { rest.forEach { card(it) } }
            }
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
private fun useDripFeed(segments: List<STTSegment>, pace: RevealPace?): List<STTSegment> {
    if (pace == null) return segments

    val fullText = captionText(segments)
    val latestFullText = rememberUpdatedState(fullText)
    val latestSegments = rememberUpdatedState(segments)
    val revealed = remember { mutableIntStateOf(fullText.length) }

    LaunchedEffect(pace) {
        var previous = latestFullText.value
        snapshotFlow { latestFullText.value }.collectLatest { current ->
            if (current != previous) {
                revealed.intValue = reanchorCursor(previous, revealed.intValue, current)
                previous = current
            }
            while (revealed.intValue < current.length) {
                val speedUp = revealStep(revealed.intValue, current.length)
                if (pace.unit != RevealUnit.LETTER) {
                    // Whole words or segments, each held back for as long as its letters would take to type
                    val next = when (pace.unit) {
                        RevealUnit.SEGMENT -> nextSegmentEnd(latestSegments.value, revealed.intValue)
                        else -> nextWordEnd(current, revealed.intValue)
                    }.coerceIn(revealed.intValue + 1, current.length)
                    delay(pace.delayMs * (next - revealed.intValue) / speedUp)
                    revealed.intValue = next
                } else {
                    delay(pace.delayMs)
                    revealed.intValue = minOf(current.length, revealed.intValue + speedUp)
                }
            }
        }
    }

    if (revealed.intValue >= fullText.length) return segments
    return applyRevealBudget(segments, revealed.intValue)
}

/** The last [count] of [segments], or all of them when [count] is 0 or less. */
internal fun <T> keepNewest(segments: List<T>, count: Int): List<T> =
    if (count > 0) segments.takeLast(count) else segments
