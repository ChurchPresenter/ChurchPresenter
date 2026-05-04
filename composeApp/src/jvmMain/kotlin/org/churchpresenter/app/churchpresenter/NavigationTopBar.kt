package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.dark_theme
import churchpresenter.composeapp.generated.resources.light_theme
import churchpresenter.composeapp.generated.resources.language_belarusian
import churchpresenter.composeapp.generated.resources.language_czech
import churchpresenter.composeapp.generated.resources.language_english
import churchpresenter.composeapp.generated.resources.language_german
import churchpresenter.composeapp.generated.resources.language_kazakh
import churchpresenter.composeapp.generated.resources.language_polish
import churchpresenter.composeapp.generated.resources.language_russian
import churchpresenter.composeapp.generated.resources.language_ukrainian
import churchpresenter.composeapp.generated.resources.menu_about
import churchpresenter.composeapp.generated.resources.menu_add_to_schedule
import churchpresenter.composeapp.generated.resources.menu_keyboard_shortcuts
import churchpresenter.composeapp.generated.resources.menu_clear_schedule
import churchpresenter.composeapp.generated.resources.menu_close_schedule
import churchpresenter.composeapp.generated.resources.menu_edit
import churchpresenter.composeapp.generated.resources.menu_exit
import churchpresenter.composeapp.generated.resources.menu_file
import churchpresenter.composeapp.generated.resources.menu_help
import churchpresenter.composeapp.generated.resources.menu_help_item
import churchpresenter.composeapp.generated.resources.open_converter
import churchpresenter.composeapp.generated.resources.menu_check_for_updates
import churchpresenter.composeapp.generated.resources.menu_language
import churchpresenter.composeapp.generated.resources.menu_view
import churchpresenter.composeapp.generated.resources.menu_new_schedule
import churchpresenter.composeapp.generated.resources.menu_open_schedule
import churchpresenter.composeapp.generated.resources.menu_remove_from_schedule
import churchpresenter.composeapp.generated.resources.menu_save_schedule
import churchpresenter.composeapp.generated.resources.menu_save_schedule_as
import churchpresenter.composeapp.generated.resources.menu_schedule
import churchpresenter.composeapp.generated.resources.menu_settings
import churchpresenter.composeapp.generated.resources.menu_statistics
import churchpresenter.composeapp.generated.resources.system_theme
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.app.churchpresenter.ui.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource

@Composable
fun FrameWindowScope.NavigationTopBar(
    theme: (ThemeMode) -> Unit,
    onLanguageChange: (Language) -> Unit = {},
    onNewSchedule: () -> Unit = {},
    onOpenSchedule: () -> Unit = {},
    onSaveSchedule: () -> Unit = {},
    onSaveScheduleAs: () -> Unit = {},
    onCloseSchedule: () -> Unit = {},
    onExit: () -> Unit = {},
    onAddToSchedule: () -> Unit = {},
    onRemoveFromSchedule: () -> Unit = {},
    onClearSchedule: () -> Unit = {},
    onSettings: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onAbout: () -> Unit = {},
    onHelp: () -> Unit = {},
    onConverter: () -> Unit = {},
    onKeyboardShortcuts: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {}
) {

    val fileLabel = stringResource(Res.string.menu_file)
    val fileMnemonic = fileLabel.firstOrNull() ?: 'F'

    val scheduleLabel = stringResource(Res.string.menu_schedule)
    val scheduleMnemonic = scheduleLabel.firstOrNull() ?: 'S'

    val editLabel = stringResource(Res.string.menu_edit)
    val editMnemonic = editLabel.firstOrNull() ?: 'E'

    val helpLabel = stringResource(Res.string.menu_help)
    val helpMnemonic = helpLabel.firstOrNull() ?: 'H'

    MenuBar {
        Menu(fileLabel, mnemonic = fileMnemonic) {
            Item(
                stringResource(Res.string.menu_new_schedule),
                onClick = onNewSchedule,
                shortcut = KeyShortcut(ctrl = true, shift = true, key = Key.N)
            )
            Item(
                stringResource(Res.string.menu_open_schedule),
                onClick = onOpenSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.O)
            )
            Item(
                stringResource(Res.string.menu_save_schedule),
                onClick = onSaveSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.S)
            )
            Item(
                stringResource(Res.string.menu_save_schedule_as),
                onClick = onSaveScheduleAs
            )
            Item(
                stringResource(Res.string.menu_close_schedule),
                onClick = onCloseSchedule,
                shortcut = KeyShortcut(ctrl = true, key = Key.W)
            )
            Item(
                stringResource(Res.string.menu_exit),
                onClick = onExit,
                shortcut = KeyShortcut(ctrl = true, key = Key.Q)
            )
        }

        Menu(scheduleLabel, mnemonic = scheduleMnemonic) {
            Item(
                stringResource(Res.string.menu_add_to_schedule),
                onClick = onAddToSchedule,
                shortcut = KeyShortcut(key = Key.F2)
            )
            Item(
                stringResource(Res.string.menu_remove_from_schedule),
                onClick = onRemoveFromSchedule,
                shortcut = KeyShortcut(key = Key.Delete)
            )
            Item(stringResource(Res.string.menu_clear_schedule), onClick = onClearSchedule)
        }

        Menu(editLabel, mnemonic = editMnemonic) {
            Item(
                stringResource(Res.string.menu_settings),
                onClick = onSettings,
                shortcut = KeyShortcut(ctrl = true, key = Key.T)
            )
            Item(
                stringResource(Res.string.menu_statistics),
                onClick = onStatistics
            )
        }

        Menu(stringResource(Res.string.menu_view), mnemonic = 'V') {
            Item(
                text = stringResource(Res.string.light_theme),
                onClick = {
                    theme.invoke(ThemeMode.LIGHT)
                }
            )
            Item(
                text = stringResource(Res.string.dark_theme),
                onClick = {
                    theme.invoke(ThemeMode.DARK)
                }
            )
            Item(
                text = stringResource(Res.string.system_theme),
                onClick = {
                    theme.invoke(ThemeMode.SYSTEM)
                }
            )
        }

        Menu(stringResource(Res.string.menu_language), mnemonic = 'L') {
            Item(
                text = "🇷🇺 ${stringResource(Res.string.language_russian)}",
                onClick = { onLanguageChange(Language.RUSSIAN) }
            )
            Item(
                text = "🇺🇸 ${stringResource(Res.string.language_english)}",
                onClick = { onLanguageChange(Language.ENGLISH) }
            )
            Item(
                text = "🇺🇦 ${stringResource(Res.string.language_ukrainian)}",
                onClick = { onLanguageChange(Language.UKRAINIAN) }
            )
            Item(
                text = "🇰🇿 ${stringResource(Res.string.language_kazakh)}",
                onClick = { onLanguageChange(Language.KAZAKH) }
            )
            Item(
                text = "🇩🇪 ${stringResource(Res.string.language_german)}",
                onClick = { onLanguageChange(Language.GERMAN) }
            )
            Item(
                text = "🇵🇱 ${stringResource(Res.string.language_polish)}",
                onClick = { onLanguageChange(Language.POLISH) }
            )
            Item(
                text = "🇧🇾 ${stringResource(Res.string.language_belarusian)}",
                onClick = { onLanguageChange(Language.BELARUSIAN) }
            )
            Item(
                text = "🇨🇿 ${stringResource(Res.string.language_czech)}",
                onClick = { onLanguageChange(Language.CZECH) }
            )
        }

        Menu(helpLabel, mnemonic = helpMnemonic) {
            Item(stringResource(Res.string.menu_keyboard_shortcuts), onClick = onKeyboardShortcuts, shortcut = KeyShortcut(key = Key.F1))
            Item(stringResource(Res.string.open_converter), onClick = onConverter)
            Item(stringResource(Res.string.menu_about), onClick = onAbout)
            Item(stringResource(Res.string.menu_help_item), onClick = onHelp)
            Item(stringResource(Res.string.menu_check_for_updates), onClick = onCheckForUpdates)
        }
    }
}