package org.churchpresenter.stt.presenter

import org.churchpresenter.settings.CaptionReading
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.churchpresenter.sharedui.presenter.ReferenceScaledBox
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
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.utils.spacingEm
import org.churchpresenter.settings.CAPTION_STYLE_RSVP
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
import org.churchpresenter.core.models.stt.HighlightedWord
import org.churchpresenter.core.models.stt.STTSegment
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
    // Bumped by the STT client when a list starts afresh (`STTManager.transcriptEpoch`)
    transcriptEpoch: Int = 0,
    translationEpoch: Int = 0,
) = ReferenceScaledBox(modifier) {
    STTPresenterContent(
        modifier = Modifier,
        segments = segments,
        inProgressText = inProgressText,
        translationSegments = translationSegments,
        inProgressTranslation = inProgressTranslation,
        highlightedWords = highlightedWords,
        sttSettings = sttSettings,
        outputRole = outputRole,
        transcriptEpoch = transcriptEpoch,
        translationEpoch = translationEpoch,
    )
}

/** [STTPresenter] as a 1920x1080-family output draws it; [ReferenceScaledBox] fits it to the real one. */
@Composable
private fun STTPresenterContent(
    modifier: Modifier = Modifier,
    segments: List<STTSegment>,
    inProgressText: String,
    translationSegments: List<STTSegment>,
    inProgressTranslation: String,
    highlightedWords: List<HighlightedWord>,
    sttSettings: STTSettings,
    outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
    transcriptEpoch: Int = 0,
    translationEpoch: Int = 0,
) {
    val look = remember(sttSettings, outputRole) { captionLook(sttSettings, outputRole) }
    val textColor = look.textColor
    val translationColor = look.translationColor
    val cardBg = look.cardBg
    val transcriptLook = look.transcript
    val translationLook = look.translation
    val reading = sttSettings.reading

    val boxAlignment = sttPositionToAlignment(sttSettings.position)

    // Prepare text content based on display mode
    val showTranscription = sttSettings.displayMode == "transcribe" || sttSettings.displayMode == "both"
    val showTranslation = sttSettings.displayMode == "translate" || sttSettings.displayMode == "both"

    // Drip feed and the reading-speed limit: the newest words revealed at a pace, not all at once.
    // Matching the speaker, each track is paced by its own segments' timings -- a translation has
    // more or fewer letters for the same seconds of speech, and has to finish when the speaker does.
    val transcriptPace = revealPace(sttSettings, rememberSpeakerPace(sttSettings, segments))
    val translationPace = revealPace(sttSettings, rememberSpeakerPace(sttSettings, translationSegments))
    // RSVP leaves the words still being spoken out, as the ticker does, so it is paced either way
    val rsvp = reading.style == CAPTION_STYLE_RSVP
    val transcriptShownPace = transcriptPace.takeIf { rsvp || !sttSettings.showInProgress }
    val translationShownPace = translationPace.takeIf { rsvp || !sttSettings.showTranslationInProgress }
    val transcript = rememberRevealed(segments, transcriptEpoch, transcriptShownPace)
    val translation = rememberRevealed(translationSegments, translationEpoch, translationShownPace)
    val dripTranscription = transcript.drip
    val dripTranslation = translation.drip

    // Only the newest [STTSettings.maxSegments] of them (0 keeps every one): how much of the
    // running transcript this output keeps is set per profile. A ticker, an RSVP flash and the
    // interleaved languages take them cut down so; a paragraph keeps them by where they start
    // instead (see paragraphCaption), so cutting the oldest out never re-wraps the lines after it.
    val keptTranscription = keepNewest(dripTranscription.segments, sttSettings.maxSegments)
    val keptTranslation = keepNewest(dripTranslation.segments, sttSettings.maxSegments)
    val wordEm = spacingEm(sttSettings.wordSpacing, sttSettings.fontSize)
    val spaceTrackingEm = (spacingEm(sttSettings.letterSpacing, sttSettings.fontSize) + wordEm).takeIf { wordEm != 0f }
    // A ticker only ever adds words, so it never shows the ones still being rewritten; nor does RSVP,
    // which flashes each word once
    val ticker = reading.style == CAPTION_STYLE_TICKER || rsvp
    val highlights = highlightedWords.takeIf { sttSettings.showWordHighlighting }.orEmpty()
    val transcriptInk = CaptionInk(textColor, highlights, spaceTrackingEm)
    val translationInk = CaptionInk(translationColor, highlights, spaceTrackingEm)
    val transcriptSide = CaptionSide(
        keptTranscription, inProgressText.takeIf { sttSettings.showInProgress && !ticker }, transcriptInk,
        sttSettings.transcriptAllCaps,
    )
    val translationSide = CaptionSide(
        keptTranslation, inProgressTranslation.takeIf { sttSettings.showTranslationInProgress && !ticker },
        translationInk, sttSettings.translationAllCaps,
    )
    val transcriptionText = shownCaption(transcript, transcriptSide, reading, sttSettings.maxSegments, ticker)
    val translationText = shownCaption(translation, translationSide, reading, sttSettings.maxSegments, ticker)
    val silenceFade = rememberSilenceFade(transcriptionText.text.text + "\u0000" + translationText.text.text, reading)
    val faded = modifier.graphicsLayer {
        alpha = silenceFade.value
    }

    val isBothMode = showTranscription && showTranslation
    // A ticker is one line, so two of them always stack
    val isSideBySide = (sttSettings.layout == "side_by_side" || sttSettings.layout == "side_by_side_inverse") &&
        reading.style != CAPTION_STYLE_TICKER
    val isInverse = sttSettings.layout.endsWith("_inverse")
    // RSVP flashes each side on its own, so an interleaved layout stacks them instead
    val interleavedText = if (isBothMode && sttSettings.layout.startsWith(LAYOUT_INTERLEAVED) && !rsvp) {
        interleavedCaption(transcriptSide, translationSide, translationLook, translationFirst = isInverse, sttSettings)
            .let { CaptionShown(withReadingAids(it, flashWords = 0, reading)) }
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
private class CaptionPart(val key: String, val text: CaptionShown, val style: TextStyle, val box: TextBox)

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
                    // A text box is placed by the operator, not held by the page's bottom edge
                    inTextBox = true,
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
 * the effect is keyed on [pace]. The caption the cursor was last anchored to is kept outside the
 * effect, so a restart -- once the speed follows the speaker it moves with every segment -- still
 * re-anchors the cursor when the server's window has dropped text off the front meanwhile.
 *
 * The character arithmetic lives in `SttDripFeed.kt`.
 */
/** The speaker's pace over [segments] while [s] matches it, else null -- see [speakerMsPerChar]. */
@Composable
private fun rememberSpeakerPace(s: STTSettings, segments: List<STTSegment>): Long? =
    remember(s.matchSpeakerPace, segments) { if (s.matchSpeakerPace) speakerMsPerChar(segments) else null }

@Composable
private fun useDripFeed(segments: List<STTSegment>, pace: RevealPace?, generation: Int): RevealedCaption {
    if (pace == null) return RevealedCaption(segments, segments)

    val fullText = captionText(segments)
    val latestFullText = rememberUpdatedState(fullText)
    val latestSegments = rememberUpdatedState(segments)
    val latestGeneration = rememberUpdatedState(generation)
    val revealed = remember { mutableIntStateOf(fullText.length) }
    // The segments the cursor was last anchored to, and the history they were part of
    val anchoredTo = remember { mutableStateOf(segments) }
    val anchoredGeneration = remember { mutableIntStateOf(generation) }
    // The words in the RSVP flash the reveal stepped to last -- a phrase flash varies in size
    val flashWords = remember { mutableIntStateOf(lastFlashWords(fullText, pace.wordsPerStep)) }
    // When the RSVP flash on screen has been up long enough, on the frame clock. Kept outside the
    // effect so a segment landing mid-hold -- which restarts the collection -- cannot cut it short.
    val flashHeldUntil = remember { mutableLongStateOf(0L) }

    LaunchedEffect(pace) {
        snapshotFlow { latestFullText.value to latestGeneration.value }.collectLatest { (current, gen) ->
            val latest = latestSegments.value
            if (gen != anchoredGeneration.intValue) {
                // Started afresh: what it opens on is a backlog, shown whole as a newly opened output's
                revealed.intValue = current.length
                anchoredGeneration.intValue = gen
                anchoredTo.value = latest
            } else if (latest != anchoredTo.value) {
                revealed.intValue = reanchorCursor(anchoredTo.value, revealed.intValue, latest)
                anchoredTo.value = latest
            }
            while (revealed.intValue < current.length) {
                val speedUp = revealStep(revealed.intValue, current.length)
                if (pace.unit == RevealUnit.FLASH) {
                    // An RSVP flash goes up as soon as the one before has had its time -- at once after a
                    // pause -- and is then held for its own. Never sped up to catch up, so it falls
                    // behind a speaker past its ceiling.
                    val now = withFrameMillis { it }
                    val wait = (flashHeldUntil.longValue - now).coerceAtLeast(0)
                    if (wait > 0) delay(wait)
                    val next = flashEnd(current, revealed.intValue, pace.wordsPerStep)
                        .coerceIn(revealed.intValue + 1, current.length)
                    flashHeldUntil.longValue = now + wait + flashDelayMs(pace, current, revealed.intValue, next)
                    flashWords.intValue = wordsBetween(current, revealed.intValue, next).coerceAtLeast(1)
                    revealed.intValue = next
                } else if (pace.unit != RevealUnit.LETTER) {
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

    // Re-anchored here too: the effect does it a frame later, and until then the old cursor would
    // count into text that has changed
    val cursor = when {
        generation != anchoredGeneration.intValue -> fullText.length
        segments != anchoredTo.value -> reanchorCursor(anchoredTo.value, revealed.intValue, segments)
        else -> revealed.intValue
    }
    val flash = flashWords.intValue.takeIf { pace.unit == RevealUnit.FLASH } ?: 0
    if (cursor >= fullText.length) return RevealedCaption(segments, segments, flashWords = flash)
    return RevealedCaption(
        segments = applyRevealBudget(segments, cursor),
        through = throughCursor(segments, cursor),
        revealed = cursor,
        flashWords = flash,
    )
}

/**
 * What a reveal has put on screen: its [segments] cut to the cursor; the segments [through] the one
 * being typed, each whole, and the [revealed] characters of them -- null once all are out; and the
 * words in its RSVP flash, 0 when it has none.
 */
internal class RevealedCaption(
    val segments: List<STTSegment>,
    val through: List<STTSegment>,
    val revealed: Int? = null,
    val flashWords: Int = 0,
)

/** What has been said on one side, as this output keeps it -- see [CaptionHistory] -- revealed at [pace]. */
@Composable
private fun rememberRevealed(window: List<STTSegment>, epoch: Int, pace: RevealPace?): RevealedSide {
    val history = rememberCaptionHistory(window, epoch)
    return RevealedSide(history, useDripFeed(history.segments, pace, history.generation))
}

/** The last [count] of [segments], or all of them when [count] is 0 or less. */
internal fun <T> keepNewest(segments: List<T>, count: Int): List<T> =
    if (count > 0) segments.takeLast(count) else segments

/** The colours, card and type captions are drawn in. */
private data class CaptionColors(
    val textColor: Color,
    val translationColor: Color,
    val cardBg: Color,
    val transcript: TextStyle,
    val translation: TextStyle,
)

/** How [s] draws captions on an output of [outputRole]: a key output draws everything white. */
private fun captionLook(s: STTSettings, outputRole: String): CaptionColors {
    val isKey = outputRole == Constants.OUTPUT_ROLE_KEY
    val textColor = if (isKey) Color.White else parseHexColor(s.textColor)
    val translationColor = if (isKey) Color.White else parseHexColor(s.translationTextColor)
    val bgOpacity = (s.backgroundOpacity / 100f).coerceIn(0f, 1f)
    val cardBg = if (isKey) Color.White
                 else parseHexColor(if (
                     s.backgroundColor == Constants.COLOR_VALUE_TRANSPARENT
                 ) "#1E1E2E" else s.backgroundColor).copy(alpha = bgOpacity)
    val fontFamily = systemFontFamilyOrDefault(s.fontType)

    val shadowColorBase = parseHexColor(s.shadowColor)
    val shadowSizeMul = s.shadowSize / 100f
    val shadowAlpha = (s.shadowOpacity / 100f).coerceIn(0f, 1f)
    val sttShadow = Shadow(
        color = shadowColorBase.copy(alpha = shadowAlpha),
        offset = Offset(4f * shadowSizeMul, 4f * shadowSizeMul),
        blurRadius = 8f * shadowSizeMul
    )

    val lineHeightSp = (s.fontSize * s.lineSpacing / 100f).sp

    val baseTextStyle = TextStyle(
        fontFamily = fontFamily,
        fontWeight = if (s.bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (s.italic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = if (s.underline) TextDecoration.Underline else TextDecoration.None,
        shadow = if (s.shadow) sttShadow else null,
        textAlign = when {
            s.position.contains("Left") -> TextAlign.Left
            s.position.contains("Right") -> TextAlign.Right
            else -> TextAlign.Center
        },
        fontSize = s.fontSize.sp,
        lineHeight = lineHeightSp,
        // Every line the same height: trimmed, the first and last lines are shorter than the rest,
        // so a line moving into first place moves by less than the lines under it
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Proportional, LineHeightStyle.Trim.None),
        letterSpacing = spacingEm(s.letterSpacing, s.fontSize).em,
    )
    val transcriptLook = baseTextStyle.copy(color = textColor)
    val translationLook = translationTextStyle(baseTextStyle, s).copy(color = translationColor)
    return CaptionColors(textColor, translationColor, cardBg, transcriptLook, translationLook)
}

/** One language's caption: its kept segments and the words still being spoken, inked and read-aided. */
internal fun captionText(side: CaptionSide, flashWords: Int, reading: CaptionReading): AnnotatedString =
    withReadingAids(
        buildDisplayText(
            captionBody(side.segments, side.inProgress, reading, side.allCaps),
            side.segments.isNotEmpty(), reading, side.ink,
        ),
        flashWords, reading,
    )
