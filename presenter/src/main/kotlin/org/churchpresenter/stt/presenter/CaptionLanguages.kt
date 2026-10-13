package org.churchpresenter.stt.presenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.STTSettings

private val COLUMN_GAP = 24.dp
private val ROW_GAP = 16.dp

/** [content] in the captions' card or band, in [background]. */
@Composable
internal fun CaptionCard(
    s: STTSettings,
    background: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.captionCard(s, background), content = content)
}

/**
 * Both languages, [first] then [second], side by side or one above the other: sharing one card, or
 * each in a card of its own when [STTSettings.separateLanguageBoxes].
 */
@Composable
internal fun BothLanguages(
    first: AnnotatedString,
    firstStyle: TextStyle,
    second: AnnotatedString,
    secondStyle: TextStyle,
    s: STTSettings,
    cardBg: Color,
    sideBySide: Boolean,
    alignment: Alignment,
) {
    val separate = s.separateLanguageBoxes
    val part: @Composable (AnnotatedString, TextStyle, Modifier) -> Unit = { text, style, modifier ->
        if (separate) {
            CaptionCard(s, cardBg, modifier) { CaptionLines(text, style, s, Modifier.fillMaxWidth()) }
        } else {
            CaptionLines(text, style, s, modifier)
        }
    }
    val layout: @Composable () -> Unit = {
        if (sideBySide) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (separate) ROW_GAP else COLUMN_GAP),
                verticalAlignment = Alignment.Bottom,
            ) {
                part(first, firstStyle, Modifier.weight(1f))
                part(second, secondStyle, Modifier.weight(1f))
            }
        } else if (separate) {
            Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
                part(first, firstStyle, Modifier.fillMaxWidth())
                part(second, secondStyle, Modifier.fillMaxWidth())
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = alignment) {
                    part(first, firstStyle, Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(ROW_GAP))
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = alignment) {
                    part(second, secondStyle, Modifier.fillMaxWidth())
                }
            }
        }
    }
    if (separate) layout() else CaptionCard(s, cardBg) { layout() }
}
