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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.churchpresenter.sharedui.composables.SectionInk
import org.churchpresenter.sharedui.composables.SongSectionKind
import org.churchpresenter.sharedui.utils.SystemClipboard
import org.churchpresenter.songchords.ChordSheetImporter
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.semantic

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
    val voice = MaterialTheme.colorScheme.tertiary
    val thirdVoice = MaterialTheme.semantic.success
    return remember(verse, chorus, chord, divider, voice, thirdVoice) {
        LyricsHighlighter(
            LyricsInks(
                verse = verse,
                chorus = chorus,
                chord = chord,
                divider = divider,
                voice = voice,
                thirdVoice = thirdVoice,
            ),
        )
    }
}

/**
 * [value] with [text] pasted over its selection and the cursor after it, as the field's own paste
 * would leave it.
 */
internal fun pastedInto(value: TextFieldValue, text: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.substring(0, start) + text + value.text.substring(end)
    return TextFieldValue(newText, TextRange(start + text.length))
}

/**
 * What a Ctrl+V in the lyrics does with the clipboard's [text]: a chords-over-lyrics sheet goes to
 * [onPasteChordSheet] to be converted, anything else is pasted over the selection of [current].
 */
internal fun pasteLyrics(
    text: String?,
    current: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onPasteChordSheet: (String) -> Unit,
) {
    when {
        text.isNullOrEmpty() -> Unit
        ChordSheetImporter.looksLikeChordSheet(text) -> onPasteChordSheet(text)
        else -> onValueChange(pastedInto(current, text))
    }
}

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
    val scope = rememberCoroutineScope()
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnPasteChordSheet by rememberUpdatedState(onPasteChordSheet)

    Box(
        modifier = modifier
            // A pasted chords-over-lyrics sheet is converted on the way in; anything else pastes
            // as it always has. Deciding which needs the clipboard's text, and reading it waits on
            // whichever application holds the clipboard -- seconds on Windows -- so the keystroke
            // is taken here and the clipboard read off the event thread.
            .onPreviewKeyEvent { event ->
                val paste = event.type == KeyEventType.KeyDown &&
                    event.key == Key.V &&
                    (event.isCtrlPressed || event.isMetaPressed)
                if (paste) {
                    scope.launch {
                        val text = withContext(Dispatchers.IO) { SystemClipboard.paste() }
                        pasteLyrics(text, currentValue, currentOnValueChange, currentOnPasteChordSheet)
                    }
                }
                paste
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
