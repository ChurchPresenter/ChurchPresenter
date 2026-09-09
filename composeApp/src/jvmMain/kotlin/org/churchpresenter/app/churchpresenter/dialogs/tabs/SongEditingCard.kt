package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_editing
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbar
import org.churchpresenter.app.churchpresenter.composables.SettingsScrollbarGutter
import org.churchpresenter.app.churchpresenter.composables.SettingsSection
import org.churchpresenter.settings.AppSettings
import org.jetbrains.compose.resources.stringResource

/**
 * The editing card, in whatever height is left under the preview -- scrolling inside it.
 *
 * Without the scroll the card is laid out at its natural height in a column that has already run out
 * of room, so its last rows are clipped off the bottom of the pane with nothing to say they are
 * there. Which controls go depends on the element selected, since the panel is a different length
 * for each, so the same window loses the Reset button on one element and nothing on another.
 */
@Composable
internal fun SongEditingCard(
    settings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    element: SongStyleElement,
    onElementChange: (SongStyleElement) -> Unit,
    target: SongStyleTarget,
    availableFonts: List<String>,
    modifier: Modifier = Modifier,
) {
    val style = settings.songSettings.elementStyle(element, target)
    val editingScroll = rememberScrollState()
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(editingScroll)
                .padding(end = SettingsScrollbarGutter),
        ) {
            SettingsSection(title = stringResource(Res.string.bible_editing)) {
                SongElementRow(
                    settings = settings,
                    onSettingsChange = onSettingsChange,
                    element = element,
                    onElementChange = onElementChange,
                    target = target,
                )
                // Keyed on what the panel is pointed at: the controls below are one set standing for
                // ten stored profiles, and without this Compose keeps the subtree across a switch and
                // hands each control the state of whichever control held its slot before.
                key(element, target) {
                    SongTypographyPanel(
                        element = element,
                        style = style,
                        onStyleChange = { edited ->
                            onSettingsChange { s ->
                                s.copy(songSettings = s.songSettings.withElementStyle(element, target, edited))
                            }
                        },
                        onReset = {
                            onSettingsChange { s ->
                                s.copy(
                                    songSettings = s.songSettings.withElementStyle(
                                        element,
                                        target,
                                        defaultSongElementStyle(element, target),
                                    ),
                                )
                            }
                        },
                        availableFonts = availableFonts,
                    )
                }
            }
        }
        SettingsScrollbar(editingScroll)
    }
}
