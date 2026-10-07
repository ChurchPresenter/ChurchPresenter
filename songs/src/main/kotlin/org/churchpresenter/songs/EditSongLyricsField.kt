package org.churchpresenter.songs

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.sharedui.composables.SectionInk
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.composables.sectionKindOf
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.songchords.ChordSheetImporter
import org.churchpresenter.songchords.ChordTransposer
import org.churchpresenter.theme.AppShape

private val LyricsFieldShape = AppShape(10.dp)

/**
 * Colours what brackets mean while they are being typed: a verse header, a chorus header, and the
 * chords sitting inside the line. The three match the legend under the editor.
 */
@Composable
internal fun rememberLyricsHighlight(): VisualTransformation {
    val verse = SectionInk.of(SongSectionKind.VERSE)
    val chorus = SectionInk.of(SongSectionKind.CHORUS)
    val chord = MaterialTheme.colorScheme.primary
    val divider = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(verse, chorus, chord, divider) {
        VisualTransformation { text ->
            val annotated = buildAnnotatedString {
                text.text.split("\n").forEachIndexed { i, line ->
                    if (i > 0) append("\n")
                    if (ChordTransposer.isSlideBreak(line) || ChordTransposer.isBackgroundDirective(line)) {
                        // Neither a section nor a chord: a slide break is a rule drawn through the
                        // words and a background directive is configuration sitting among them.
                        // Both read as what they are rather than as a line someone will sing.
                        pushStyle(SpanStyle(color = divider, fontWeight = FontWeight.Bold))
                        append(line)
                        pop()
                    } else if (ChordTransposer.isSectionHeader(line)) {
                        val ink = if (sectionKindOf(line.trim().trim('[', ']', '{', '}')) == SongSectionKind.CHORUS) {
                            chorus
                        } else {
                            verse
                        }
                        pushStyle(
                            SpanStyle(color = ink, background = ink.copy(alpha = 0.16f), fontWeight = FontWeight.Bold)
                        )
                        append(line)
                        pop()
                    } else {
                        appendChordHighlighted(line, chord)
                    }
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }
}

/** Appends [line] with each `[chord]` marker tinted, leaving the words plain. */
private fun AnnotatedString.Builder.appendChordHighlighted(line: String, ink: Color) {
    var cursor = 0
    Regex("\\[[^\\]]*\\]").findAll(line).forEach { match ->
        if (!ChordTransposer.isChord(match.value.substring(1, match.value.length - 1))) return@forEach
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        pushStyle(SpanStyle(color = ink, background = ink.copy(alpha = 0.14f), fontWeight = FontWeight.Bold))
        append(match.value)
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

/**
 * The clipboard's text, or null when it holds none.
 *
 * Read straight from AWT rather than through a Compose clipboard API: this runs inside a key
 * handler that has to decide, before the keystroke is consumed, whether the paste is a chord sheet.
 */
private fun clipboardText(): String? = SystemClipboard.paste()

/**
 * Dedicated multi-line, scrollable lyrics editor — kept separate from SettingsTextField
 * (which is tuned for compact single-line settings rows) so it can grow to fill the
 * available height with its own scrollbar and a monospaced face, which is what keeps a
 * chord over the syllable it was typed against.
 */
@Composable
internal fun LyricsTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onPasteChordSheet: (String) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            // A pasted chords-over-lyrics sheet is converted on the way in; anything else pastes
            // as it always has, so this is invisible until it is wanted.
            .onPreviewKeyEvent { event ->
                val paste = event.type == KeyEventType.KeyDown &&
                    event.key == Key.V &&
                    (event.isCtrlPressed || event.isMetaPressed)
                val sheet = if (paste) clipboardText() else null
                if (sheet != null && ChordSheetImporter.looksLikeChordSheet(sheet)) {
                    onPasteChordSheet(sheet)
                    true
                } else {
                    false
                }
            }
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, LyricsFieldShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, LyricsFieldShape)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 18.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            interactionSource = interactionSource,
            decorationBox = { innerTextField ->
                Box {
                    if (placeholder != null && value.text.isEmpty()) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        ) {
                            placeholder()
                        }
                    }
                    innerTextField()
                }
            }
        )
        VerticalScrollbar(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .padding(vertical = 4.dp, horizontal = 2.dp),
            adapter = rememberScrollbarAdapter(scrollState)
        )
    }
}
