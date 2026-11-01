package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_section_cue
import org.churchpresenter.strings.generated.resources.song_section_cue_placeholder
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.stringResource

/** Test tag prefix of a section's cue field in the editor, followed by the section key. */
internal const val SECTION_CUE_FIELD_TAG = "song_editor_section_cue:"

/** One section's cue, typed under its label in the In order tab. */
@Composable
internal fun SectionCueField(key: String, cue: String, onCueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.song_section_cue)
    val style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = CUE_FIELD_TINT), AppShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (cue.isEmpty()) {
            Text(
                text = stringResource(Res.string.song_section_cue_placeholder),
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        BasicTextField(
            value = cue,
            onValueChange = { onCueChange(it.replace("\n", " ")) },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().tabMovesFocus()
                .semantics { contentDescription = label }
                .testTag(SECTION_CUE_FIELD_TAG + key),
        )
    }
}

private const val CUE_FIELD_TINT = 0.08f
