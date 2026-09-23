package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.enabled
import churchpresenter.composeapp.generated.resources.song_title_slide
import churchpresenter.composeapp.generated.resources.vertical_alignment
import org.churchpresenter.app.churchpresenter.composables.LabeledCheckbox
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.app.churchpresenter.composables.VerticalAlignmentButtons
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource

/**
 * Whether a song opens with a title slide, and where on the screen that slide's text sits.
 *
 * The Song settings tab's own rail once held three siblings of this (lyrics layout, transitions,
 * margins) as well; those, and every other control the tab carried, moved to the Profiles tab once
 * an output stopped being able to customize anything of its own -- see `SongSettingsTab.kt`'s own
 * note. This one stayed here rather than following them because it is shared: the Profiles tab's
 * Song pane calls it too, through `SongTitleSlideEnabledRow` in `CustomizeSongPane.kt`.
 */
@Composable
internal fun SongTitleSlideSection(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    /**
     * Off for an output that draws a band, which keeps the block at its own bottom whatever this
     * says -- so the row would be a control that does nothing.
     *
     * Only the Profiles tab's Song pane ever passes false: a full-screen profile styles both shapes
     * (this row and its lower-third one) and cannot know which one an assigned output actually is.
     */
    showVerticalAlignment: Boolean = true,
) {
    SettingsSection(title = stringResource(Res.string.song_title_slide)) {
        LabeledCheckbox(
            checked = settings.songSettings.titleSlideEnabled,
            onCheckedChange = { on ->
                onSettingsChange { s -> s.copy(songSettings = s.songSettings.copy(titleSlideEnabled = on)) }
            },
            controlModifier = Modifier.size(24.dp),
            label = stringResource(Res.string.enabled),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("song_titleSlideEnabled"),
            style = MaterialTheme.typography.bodyMedium,
        )
        // The whole block -- number, title and credits -- moves as one; the lower third keeps it
        // at the bottom of the band regardless, which is what [showVerticalAlignment] is for.
        if (showVerticalAlignment) Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (settings.songSettings.titleSlideEnabled) 1f else DISABLED_ALPHA),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.vertical_alignment).removeSuffix(":"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.testTag("song_titleSlideVerticalAlignment")) {
                VerticalAlignmentButtons(
                    selectedAlignment = settings.songSettings.titleSlideVerticalAlignment,
                    onAlignmentChange = { value ->
                        if (settings.songSettings.titleSlideEnabled) {
                            onSettingsChange { s ->
                                s.copy(songSettings = s.songSettings.copy(titleSlideVerticalAlignment = value))
                            }
                        }
                    },
                    topValue = Constants.TOP,
                    middleValue = Constants.MIDDLE,
                    bottomValue = Constants.BOTTOM,
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
