package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.keyboard_shortcuts_title
import churchpresenter.composeapp.generated.resources.ok
import churchpresenter.composeapp.generated.resources.symbol_ok
import churchpresenter.composeapp.generated.resources.shortcut_category_bible
import churchpresenter.composeapp.generated.resources.shortcut_category_global
import churchpresenter.composeapp.generated.resources.shortcut_category_media
import churchpresenter.composeapp.generated.resources.shortcut_category_pictures
import churchpresenter.composeapp.generated.resources.shortcut_category_presentation
import churchpresenter.composeapp.generated.resources.shortcut_category_songs
import churchpresenter.composeapp.generated.resources.shortcut_category_tab_switching
import churchpresenter.composeapp.generated.resources.shortcut_description_add_to_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_close_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_escape
import churchpresenter.composeapp.generated.resources.shortcut_description_exit
import churchpresenter.composeapp.generated.resources.shortcut_description_f10_media
import churchpresenter.composeapp.generated.resources.shortcut_description_f11_lower_third
import churchpresenter.composeapp.generated.resources.shortcut_description_f12_announcements
import churchpresenter.composeapp.generated.resources.shortcut_description_f1_keyboard_shortcuts
import churchpresenter.composeapp.generated.resources.shortcut_description_f6_bible
import churchpresenter.composeapp.generated.resources.shortcut_description_f7_songs
import churchpresenter.composeapp.generated.resources.shortcut_description_f8_pictures
import churchpresenter.composeapp.generated.resources.shortcut_description_f9_presentation
import churchpresenter.composeapp.generated.resources.shortcut_description_go_live
import churchpresenter.composeapp.generated.resources.shortcut_description_media_mute
import churchpresenter.composeapp.generated.resources.shortcut_description_new_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_next_chapter
import churchpresenter.composeapp.generated.resources.shortcut_description_next_image
import churchpresenter.composeapp.generated.resources.shortcut_description_next_section
import churchpresenter.composeapp.generated.resources.shortcut_description_next_slide
import churchpresenter.composeapp.generated.resources.shortcut_description_next_song
import churchpresenter.composeapp.generated.resources.shortcut_description_next_verse
import churchpresenter.composeapp.generated.resources.shortcut_description_open_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_play_pause
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_chapter
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_image
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_section
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_slide
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_song
import churchpresenter.composeapp.generated.resources.shortcut_description_prev_verse
import churchpresenter.composeapp.generated.resources.shortcut_description_remove_from_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_save_schedule
import churchpresenter.composeapp.generated.resources.shortcut_description_settings
import churchpresenter.composeapp.generated.resources.shortcut_description_spacebar
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_o
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_q
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_s
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_shift_n
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_t
import churchpresenter.composeapp.generated.resources.shortcut_key_ctrl_w
import churchpresenter.composeapp.generated.resources.shortcut_key_delete
import churchpresenter.composeapp.generated.resources.shortcut_key_double_click
import churchpresenter.composeapp.generated.resources.shortcut_key_down
import churchpresenter.composeapp.generated.resources.shortcut_key_down_right
import churchpresenter.composeapp.generated.resources.shortcut_key_down_right_song
import churchpresenter.composeapp.generated.resources.shortcut_key_escape
import churchpresenter.composeapp.generated.resources.shortcut_key_f1
import churchpresenter.composeapp.generated.resources.shortcut_key_f10
import churchpresenter.composeapp.generated.resources.shortcut_key_f11
import churchpresenter.composeapp.generated.resources.shortcut_key_f12
import churchpresenter.composeapp.generated.resources.shortcut_key_f2
import churchpresenter.composeapp.generated.resources.shortcut_key_f6
import churchpresenter.composeapp.generated.resources.shortcut_key_f7
import churchpresenter.composeapp.generated.resources.shortcut_key_f8
import churchpresenter.composeapp.generated.resources.shortcut_key_f9
import churchpresenter.composeapp.generated.resources.shortcut_key_left
import churchpresenter.composeapp.generated.resources.shortcut_key_m
import churchpresenter.composeapp.generated.resources.shortcut_key_right
import churchpresenter.composeapp.generated.resources.shortcut_key_space
import churchpresenter.composeapp.generated.resources.shortcut_key_up
import churchpresenter.composeapp.generated.resources.shortcut_key_up_left
import churchpresenter.composeapp.generated.resources.shortcut_key_up_left_song
import org.jetbrains.compose.resources.stringResource

@Composable
fun KeyboardShortcutsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, 680.dp, 600.dp),
            width = 680.dp,
            height = 600.dp
        ),
        title = stringResource(Res.string.keyboard_shortcuts_title),
        resizable = true
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShortcutsCategory(stringResource(Res.string.shortcut_category_global)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_shift_n), stringResource(Res.string.shortcut_description_new_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_o),       stringResource(Res.string.shortcut_description_open_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_s),       stringResource(Res.string.shortcut_description_save_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_w),       stringResource(Res.string.shortcut_description_close_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_q),       stringResource(Res.string.shortcut_description_exit))
                        ShortcutRow(stringResource(Res.string.shortcut_key_ctrl_t),       stringResource(Res.string.shortcut_description_settings))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f1),           stringResource(Res.string.shortcut_description_f1_keyboard_shortcuts))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f2),           stringResource(Res.string.shortcut_description_add_to_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_delete),       stringResource(Res.string.shortcut_description_remove_from_schedule))
                        ShortcutRow(stringResource(Res.string.shortcut_key_escape),       stringResource(Res.string.shortcut_description_escape))
                        ShortcutRow(stringResource(Res.string.shortcut_key_double_click), stringResource(Res.string.shortcut_description_go_live))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_tab_switching)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_f6),  stringResource(Res.string.shortcut_description_f6_bible))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f7),  stringResource(Res.string.shortcut_description_f7_songs))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f8),  stringResource(Res.string.shortcut_description_f8_pictures))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f9),  stringResource(Res.string.shortcut_description_f9_presentation))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f10), stringResource(Res.string.shortcut_description_f10_media))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f11), stringResource(Res.string.shortcut_description_f11_lower_third))
                        ShortcutRow(stringResource(Res.string.shortcut_key_f12), stringResource(Res.string.shortcut_description_f12_announcements))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_bible)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_up),    stringResource(Res.string.shortcut_description_prev_verse))
                        ShortcutRow(stringResource(Res.string.shortcut_key_down),  stringResource(Res.string.shortcut_description_next_verse))
                        ShortcutRow(stringResource(Res.string.shortcut_key_left),  stringResource(Res.string.shortcut_description_prev_chapter))
                        ShortcutRow(stringResource(Res.string.shortcut_key_right), stringResource(Res.string.shortcut_description_next_chapter))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_songs)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_up),              stringResource(Res.string.shortcut_description_prev_section))
                        ShortcutRow(stringResource(Res.string.shortcut_key_down),            stringResource(Res.string.shortcut_description_next_section))
                        ShortcutRow(stringResource(Res.string.shortcut_key_up_left_song),    stringResource(Res.string.shortcut_description_prev_song))
                        ShortcutRow(stringResource(Res.string.shortcut_key_down_right_song), stringResource(Res.string.shortcut_description_next_song))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_pictures)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_up_left),    stringResource(Res.string.shortcut_description_prev_image))
                        ShortcutRow(stringResource(Res.string.shortcut_key_down_right), stringResource(Res.string.shortcut_description_next_image))
                        ShortcutRow(stringResource(Res.string.shortcut_key_space),      stringResource(Res.string.shortcut_description_play_pause))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_presentation)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_up_left),    stringResource(Res.string.shortcut_description_prev_slide))
                        ShortcutRow(stringResource(Res.string.shortcut_key_down_right), stringResource(Res.string.shortcut_description_next_slide))
                        ShortcutRow(stringResource(Res.string.shortcut_key_space),      stringResource(Res.string.shortcut_description_play_pause))
                    }

                    ShortcutsCategory(stringResource(Res.string.shortcut_category_media)) {
                        ShortcutRow(stringResource(Res.string.shortcut_key_space), stringResource(Res.string.shortcut_description_spacebar))
                        ShortcutRow(stringResource(Res.string.shortcut_key_m),     stringResource(Res.string.shortcut_description_media_mute))
                    }
                }

                // Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(onClick = onDismiss) {
                        Text("${stringResource(Res.string.symbol_ok)} ${stringResource(Res.string.ok)}")
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(4.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun ShortcutRow(keys: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = keys,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .widthIn(min = 80.dp)
        )
    }
}
