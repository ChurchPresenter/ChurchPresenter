package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.dark_theme
import churchpresenter.composeapp.generated.resources.forest_theme
import churchpresenter.composeapp.generated.resources.light_theme
import churchpresenter.composeapp.generated.resources.midnight_theme
import churchpresenter.composeapp.generated.resources.mocha_theme
import churchpresenter.composeapp.generated.resources.studio_theme
import churchpresenter.composeapp.generated.resources.ocean_theme
import churchpresenter.composeapp.generated.resources.rose_theme
import churchpresenter.composeapp.generated.resources.warm_theme
import churchpresenter.composeapp.generated.resources.language_belarusian
import churchpresenter.composeapp.generated.resources.language_czech
import churchpresenter.composeapp.generated.resources.language_dutch
import churchpresenter.composeapp.generated.resources.language_english
import churchpresenter.composeapp.generated.resources.language_estonian
import churchpresenter.composeapp.generated.resources.language_french
import churchpresenter.composeapp.generated.resources.language_german
import churchpresenter.composeapp.generated.resources.language_kazakh
import churchpresenter.composeapp.generated.resources.language_polish
import churchpresenter.composeapp.generated.resources.language_portuguese
import churchpresenter.composeapp.generated.resources.language_romanian
import churchpresenter.composeapp.generated.resources.language_russian
import churchpresenter.composeapp.generated.resources.language_slovak
import churchpresenter.composeapp.generated.resources.language_spanish
import churchpresenter.composeapp.generated.resources.language_ukrainian
import churchpresenter.composeapp.generated.resources.menu_about
import churchpresenter.composeapp.generated.resources.menu_getting_started
import churchpresenter.composeapp.generated.resources.menu_add_to_schedule
import churchpresenter.composeapp.generated.resources.menu_keyboard_shortcuts
import churchpresenter.composeapp.generated.resources.menu_clear_schedule
import churchpresenter.composeapp.generated.resources.menu_close_schedule
import churchpresenter.composeapp.generated.resources.menu_connect
import churchpresenter.composeapp.generated.resources.menu_connect_to_instance
import churchpresenter.composeapp.generated.resources.menu_developer
import churchpresenter.composeapp.generated.resources.menu_developer_always_on_top
import churchpresenter.composeapp.generated.resources.menu_developer_display
import churchpresenter.composeapp.generated.resources.menu_developer_show_window
import churchpresenter.composeapp.generated.resources.menu_developer_style_editor
import churchpresenter.composeapp.generated.resources.menu_disconnect
import churchpresenter.composeapp.generated.resources.menu_edit
import churchpresenter.composeapp.generated.resources.menu_exit
import churchpresenter.composeapp.generated.resources.menu_file
import churchpresenter.composeapp.generated.resources.menu_help
import churchpresenter.composeapp.generated.resources.menu_help_item
import churchpresenter.composeapp.generated.resources.menu_how_to_blog
import churchpresenter.composeapp.generated.resources.open_converter
import churchpresenter.composeapp.generated.resources.menu_check_for_updates
import churchpresenter.composeapp.generated.resources.menu_contact_us
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
    currentTheme: ThemeMode = ThemeMode.SYSTEM,
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
    onConnectToInstance: () -> Unit = {},
    onDisconnectInstance: () -> Unit = {},
    isInstanceLinkConnected: Boolean = false,
    onAbout: () -> Unit = {},
    onHelp: () -> Unit = {},
    onHowToBlog: () -> Unit = {},
    onGettingStarted: () -> Unit = {},
    onConverter: () -> Unit = {},
    onKeyboardShortcuts: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onContactUs: () -> Unit = {},
    showDeveloperMenu: Boolean = false,
    isPresenterWindowVisible: Boolean = true,
    onSetPresenterWindowVisible: (Boolean) -> Unit = {},
    isDevWindowAlwaysOnTop: Boolean = false,
    onSetDevWindowAlwaysOnTop: (Boolean) -> Unit = {},
    onOpenStyleEditor: () -> Unit = {}
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

        Menu(stringResource(Res.string.menu_connect), mnemonic = 'C') {
            Item(
                stringResource(Res.string.menu_connect_to_instance),
                onClick = onConnectToInstance
            )
            Item(
                stringResource(Res.string.menu_disconnect),
                onClick = onDisconnectInstance,
                enabled = isInstanceLinkConnected
            )
        }

        Menu(stringResource(Res.string.menu_view), mnemonic = 'V') {
            RadioButtonItem(
                text = stringResource(Res.string.light_theme),
                selected = currentTheme == ThemeMode.LIGHT,
                onClick = { theme.invoke(ThemeMode.LIGHT) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.dark_theme),
                selected = currentTheme == ThemeMode.DARK,
                onClick = { theme.invoke(ThemeMode.DARK) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.system_theme),
                selected = currentTheme == ThemeMode.SYSTEM,
                onClick = { theme.invoke(ThemeMode.SYSTEM) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.warm_theme),
                selected = currentTheme == ThemeMode.WARM,
                onClick = { theme.invoke(ThemeMode.WARM) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.ocean_theme),
                selected = currentTheme == ThemeMode.OCEAN,
                onClick = { theme.invoke(ThemeMode.OCEAN) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.rose_theme),
                selected = currentTheme == ThemeMode.ROSE,
                onClick = { theme.invoke(ThemeMode.ROSE) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.midnight_theme),
                selected = currentTheme == ThemeMode.MIDNIGHT,
                onClick = { theme.invoke(ThemeMode.MIDNIGHT) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.forest_theme),
                selected = currentTheme == ThemeMode.FOREST,
                onClick = { theme.invoke(ThemeMode.FOREST) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.mocha_theme),
                selected = currentTheme == ThemeMode.MOCHA,
                onClick = { theme.invoke(ThemeMode.MOCHA) }
            )
            RadioButtonItem(
                text = stringResource(Res.string.studio_theme),
                selected = currentTheme == ThemeMode.STUDIO,
                onClick = { theme.invoke(ThemeMode.STUDIO) }
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
            Item(
                text = "🇪🇸 ${stringResource(Res.string.language_spanish)}",
                onClick = { onLanguageChange(Language.SPANISH) }
            )
            Item(
                text = "🇫🇷 ${stringResource(Res.string.language_french)}",
                onClick = { onLanguageChange(Language.FRENCH) }
            )
            Item(
                text = "🇳🇱 ${stringResource(Res.string.language_dutch)}",
                onClick = { onLanguageChange(Language.DUTCH) }
            )
            Item(
                text = "🇵🇹 ${stringResource(Res.string.language_portuguese)}",
                onClick = { onLanguageChange(Language.PORTUGUESE) }
            )
            Item(
                text = "🇷🇴 ${stringResource(Res.string.language_romanian)}",
                onClick = { onLanguageChange(Language.ROMANIAN) }
            )
            Item(
                text = "🇸🇰 ${stringResource(Res.string.language_slovak)}",
                onClick = { onLanguageChange(Language.SLOVAK) }
            )
            Item(
                text = "🇪🇪 ${stringResource(Res.string.language_estonian)}",
                onClick = { onLanguageChange(Language.ESTONIAN) }
            )
        }

        Menu(helpLabel, mnemonic = helpMnemonic) {
            Item(stringResource(Res.string.menu_getting_started), onClick = onGettingStarted)
            Item(stringResource(Res.string.menu_keyboard_shortcuts), onClick = onKeyboardShortcuts, shortcut = KeyShortcut(key = Key.F1))
            Item(stringResource(Res.string.menu_how_to_blog), onClick = onHowToBlog)
            Item(stringResource(Res.string.open_converter), onClick = onConverter)
            Item(stringResource(Res.string.menu_about), onClick = onAbout)
            Item(stringResource(Res.string.menu_help_item), onClick = onHelp)
            Item(stringResource(Res.string.menu_contact_us), onClick = onContactUs)
            Item(stringResource(Res.string.menu_check_for_updates), onClick = onCheckForUpdates)
        }

        if (showDeveloperMenu) {
            Menu(stringResource(Res.string.menu_developer), mnemonic = 'D') {
                Menu(stringResource(Res.string.menu_developer_display), mnemonic = 'S') {
                    CheckboxItem(
                        text = stringResource(Res.string.menu_developer_show_window),
                        checked = isPresenterWindowVisible,
                        onCheckedChange = onSetPresenterWindowVisible
                    )
                    CheckboxItem(
                        text = stringResource(Res.string.menu_developer_always_on_top),
                        checked = isDevWindowAlwaysOnTop,
                        onCheckedChange = onSetDevWindowAlwaysOnTop
                    )
                }
                Item(stringResource(Res.string.menu_developer_style_editor), onClick = onOpenStyleEditor)
            }
        }
    }
}