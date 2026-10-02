package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import org.churchpresenter.stt.STTSegment
import org.churchpresenter.settings.CAPTION_BREAK_NONE
import org.churchpresenter.settings.STTSettings

/** The layout that puts each line's translation straight under it, or over it when inverse. */
internal const val LAYOUT_INTERLEAVED = "interleaved"

/** One side of an interleaved caption: its segments, its in-progress words, its paint and its caps. */
internal class CaptionSide(
    val segments: List<STTSegment>,
    val inProgress: String?,
    val ink: CaptionInk,
    val allCaps: Boolean,
)

/**
 * Both languages as one caption, each segment followed by its translation -- or preceded by it when
 * [translationFirst] -- the pair matched by segment id. A segment whose translation has not come yet
 * stands alone until it does. Older pairs dim as one, and the in-progress words of each side close
 * the caption. The translation carries [translationLook]'s size, weight and slant as a span, since
 * the two share one block of text.
 */
internal fun interleavedCaption(
    transcript: CaptionSide,
    translation: CaptionSide,
    translationLook: TextStyle,
    translationFirst: Boolean,
    s: STTSettings,
): AnnotatedString {
    val translations = translation.segments.associateBy { it.id }
    val pairs = transcript.segments.map { it to translations[it.id] }
    // Lines within a pair never break as separate units; the pair is the unit the dimming counts
    val reading = s.reading.copy(lineBreaks = CAPTION_BREAK_NONE, dimOlderLines = false)
    val alphas = unitAlphas(pairs.size, s.reading)
    val separator = if (s.reading.blankLineBetween) "\n\n" else "\n"
    val translationSpan = SpanStyle(
        fontSize = translationLook.fontSize,
        fontWeight = translationLook.fontWeight,
        fontStyle = translationLook.fontStyle,
    )
    fun side(side: CaptionSide, segments: List<STTSegment>, inProgress: String?, alpha: Float): AnnotatedString {
        val ink = CaptionInk(
            side.ink.baseColor.copy(alpha = side.ink.baseColor.alpha * alpha),
            side.ink.highlights,
            side.ink.spaceTrackingEm,
        )
        val body = captionBody(segments, inProgress, reading, side.allCaps)
        return buildDisplayText(body, segments.isNotEmpty(), reading, ink)
    }
    fun pair(own: CaptionLine?, other: CaptionLine?): List<CaptionLine> =
        (if (translationFirst) listOfNotNull(other, own) else listOfNotNull(own, other)).filter { it.text.isNotEmpty() }
    val blocks = pairs.mapIndexed { i, (spoken, translated) ->
        pair(
            CaptionLine(side(transcript, listOf(spoken), null, alphas[i]), isTranslation = false),
            translated?.let { CaptionLine(side(translation, listOf(it), null, alphas[i]), isTranslation = true) },
        )
    } + listOf(
        pair(
            transcript.inProgress?.takeIf { it.isNotBlank() }
                ?.let { CaptionLine(side(transcript, emptyList(), it, 1f), isTranslation = false) },
            translation.inProgress?.takeIf { it.isNotBlank() }
                ?.let { CaptionLine(side(translation, emptyList(), it, 1f), isTranslation = true) },
        ),
    )
    return buildAnnotatedString {
        blocks.filter { it.isNotEmpty() }.forEachIndexed { b, lines ->
            if (b > 0) append(separator)
            lines.forEachIndexed { l, line ->
                if (l > 0) append("\n")
                if (line.isTranslation) withStyle(translationSpan) { append(line.text) } else append(line.text)
            }
        }
    }
}

/** One line of an interleaved caption, and whether it is the translation's. */
private class CaptionLine(val text: AnnotatedString, val isTranslation: Boolean)
