package org.churchpresenter.songs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.sharedui.composables.tabMovesFocus
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_section_cue
import org.churchpresenter.strings.generated.resources.song_section_cue_placeholder
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.hosanna.composables.field.TextField

/** Test tag prefix of a section's cue field in the editor, followed by the section key. */
internal const val SECTION_CUE_FIELD_TAG = "song_editor_section_cue:"

/** One section's cue, typed under its label in the In order tab. */
@Composable
internal fun SectionCueField(key: String, cue: String, onCueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.song_section_cue)
    TextField(
        value = cue,
        onValueChange = { onCueChange(it.replace("\n", " ")) },
        modifier = modifier.fillMaxWidth().tabMovesFocus()
            .semantics { contentDescription = label }
            .testTag(SECTION_CUE_FIELD_TAG + key),
        placeholder = stringResource(Res.string.song_section_cue_placeholder),
    )
}
