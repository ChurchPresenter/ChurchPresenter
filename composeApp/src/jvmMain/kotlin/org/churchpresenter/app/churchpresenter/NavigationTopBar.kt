package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.MenuBarScope
import org.churchpresenter.resources.generated.resources.Res
import org.churchpresenter.resources.generated.resources.dark_theme
import org.churchpresenter.resources.generated.resources.forest_theme
import org.churchpresenter.resources.generated.resources.light_theme
import org.churchpresenter.resources.generated.resources.midnight_theme
import org.churchpresenter.resources.generated.resources.mocha_theme
import org.churchpresenter.resources.generated.resources.studio_theme
import org.churchpresenter.resources.generated.resources.ocean_theme
import org.churchpresenter.resources.generated.resources.rose_theme
import org.churchpresenter.resources.generated.resources.warm_theme
import org.churchpresenter.resources.generated.resources.language_arabic
import org.churchpresenter.resources.generated.resources.language_belarusian
import org.churchpresenter.resources.generated.resources.language_chinese
import org.churchpresenter.resources.generated.resources.language_croatian
import org.churchpresenter.resources.generated.resources.language_czech
import org.churchpresenter.resources.generated.resources.language_dutch
import org.churchpresenter.resources.generated.resources.language_english
import org.churchpresenter.resources.generated.resources.language_estonian
import org.churchpresenter.resources.generated.resources.language_finnish
import org.churchpresenter.resources.generated.resources.language_french
import org.churchpresenter.resources.generated.resources.language_german
import org.churchpresenter.resources.generated.resources.language_hindi
import org.churchpresenter.resources.generated.resources.language_indonesian
import org.churchpresenter.resources.generated.resources.language_japanese
import org.churchpresenter.resources.generated.resources.language_kazakh
import org.churchpresenter.resources.generated.resources.language_lao
import org.churchpresenter.resources.generated.resources.language_latvian
import org.churchpresenter.resources.generated.resources.language_malay
import org.churchpresenter.resources.generated.resources.language_nepali
import org.churchpresenter.resources.generated.resources.language_norwegian
import org.churchpresenter.resources.generated.resources.language_persian
import org.churchpresenter.resources.generated.resources.language_polish
import org.churchpresenter.resources.generated.resources.language_portuguese
import org.churchpresenter.resources.generated.resources.language_romanian
import org.churchpresenter.resources.generated.resources.language_russian
import org.churchpresenter.resources.generated.resources.language_slovak
import org.churchpresenter.resources.generated.resources.language_spanish
import org.churchpresenter.resources.generated.resources.language_swahili
import org.churchpresenter.resources.generated.resources.language_swedish
import org.churchpresenter.resources.generated.resources.language_tagalog
import org.churchpresenter.resources.generated.resources.language_tamil
import org.churchpresenter.resources.generated.resources.language_thai
import org.churchpresenter.resources.generated.resources.language_turkish
import org.churchpresenter.resources.generated.resources.language_ukrainian
import org.churchpresenter.resources.generated.resources.language_uzbek
import org.churchpresenter.resources.generated.resources.menu_about
import org.churchpresenter.resources.generated.resources.menu_getting_started
import org.churchpresenter.resources.generated.resources.menu_add_to_schedule
import org.churchpresenter.resources.generated.resources.menu_keyboard_shortcuts
import org.churchpresenter.resources.generated.resources.menu_clear_schedule
import org.churchpresenter.resources.generated.resources.menu_close_schedule
import org.churchpresenter.resources.generated.resources.menu_connect
import org.churchpresenter.resources.generated.resources.menu_connect_to_instance
import org.churchpresenter.resources.generated.resources.menu_developer
import org.churchpresenter.resources.generated.resources.menu_developer_always_on_top
import org.churchpresenter.resources.generated.resources.menu_developer_display
import org.churchpresenter.resources.generated.resources.menu_developer_show_window
import org.churchpresenter.resources.generated.resources.menu_developer_style_editor
import org.churchpresenter.resources.generated.resources.menu_developer_memory_monitor
import org.churchpresenter.resources.generated.resources.menu_developer_story_prompt
import org.churchpresenter.resources.generated.resources.menu_disconnect
import org.churchpresenter.resources.generated.resources.menu_edit
import org.churchpresenter.resources.generated.resources.menu_exit
import org.churchpresenter.resources.generated.resources.menu_file
import org.churchpresenter.resources.generated.resources.menu_help
import org.churchpresenter.resources.generated.resources.menu_help_item
import org.churchpresenter.resources.generated.resources.menu_how_to_blog
import org.churchpresenter.resources.generated.resources.open_converter
import org.churchpresenter.resources.generated.resources.open_song_library
import org.churchpresenter.resources.generated.resources.menu_check_for_updates
import org.churchpresenter.resources.generated.resources.menu_contact_us
import org.churchpresenter.resources.generated.resources.menu_language
import org.churchpresenter.resources.generated.resources.menu_view
import org.churchpresenter.resources.generated.resources.menu_new_schedule
import org.churchpresenter.resources.generated.resources.menu_open_schedule
import org.churchpresenter.resources.generated.resources.menu_remove_from_schedule
import org.churchpresenter.resources.generated.resources.menu_save_schedule
import org.churchpresenter.resources.generated.resources.menu_save_schedule_as
import org.churchpresenter.resources.generated.resources.menu_schedule
import org.churchpresenter.resources.generated.resources.menu_settings
import org.churchpresenter.resources.generated.resources.menu_statistics
import org.churchpresenter.resources.generated.resources.system_theme
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.shortcuts.ShortcutAction
import org.churchpresenter.shortcuts.LocalShortcuts
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.StringResource
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
    onSongLibrary: () -> Unit = {},
    onKeyboardShortcuts: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    onContactUs: () -> Unit = {},
    showDeveloperMenu: Boolean = false,
    isPresenterWindowVisible: Boolean = true,
    onSetPresenterWindowVisible: (Boolean) -> Unit = {},
    isDevWindowAlwaysOnTop: Boolean = false,
    onSetDevWindowAlwaysOnTop: (Boolean) -> Unit = {},
    onOpenStyleEditor: () -> Unit = {},
    onOpenMemoryMonitor: () -> Unit = {},
    onOpenStoryPrompt: () -> Unit = {}
) {

    val fileLabel = stringResource(Res.string.menu_file)
    val fileMnemonic = fileLabel.firstOrNull() ?: 'F'

    val scheduleLabel = stringResource(Res.string.menu_schedule)
    val scheduleMnemonic = scheduleLabel.firstOrNull() ?: 'S'

    val editLabel = stringResource(Res.string.menu_edit)
    val editMnemonic = editLabel.firstOrNull() ?: 'E'

    val helpLabel = stringResource(Res.string.menu_help)
    val helpMnemonic = helpLabel.firstOrNull() ?: 'H'

    // Menu accelerators come from the same registry the key handlers use, so a rebind shows up on
    // the menu item too. Only the first chord can be an accelerator — Compose's `Item` takes one —
    // and null means the user unbound the action, which simply drops the accelerator label.
    val shortcuts = LocalShortcuts.current
    fun accelerator(action: ShortcutAction): KeyShortcut? =
        shortcuts.chordsFor(action).firstOrNull()?.toKeyShortcut()

    MenuBar {
        FileMenu(fileLabel, fileMnemonic, ::accelerator, onNewSchedule, onOpenSchedule, onSaveSchedule,
            onSaveScheduleAs, onCloseSchedule, onExit)
        ScheduleMenu(scheduleLabel, scheduleMnemonic, ::accelerator, onAddToSchedule, onRemoveFromSchedule,
            onClearSchedule)
        EditMenu(editLabel, editMnemonic, ::accelerator, onSettings, onStatistics)
        ConnectMenu(onConnectToInstance, onDisconnectInstance, isInstanceLinkConnected)
        ViewMenu(theme, currentTheme)
        LanguageMenu(onLanguageChange)
        HelpMenu(helpLabel, helpMnemonic, ::accelerator, onGettingStarted, onKeyboardShortcuts, onHowToBlog,
            onConverter, onSongLibrary, onAbout, onHelp, onContactUs, onCheckForUpdates)
        if (showDeveloperMenu) {
            DeveloperMenu(isPresenterWindowVisible, onSetPresenterWindowVisible, isDevWindowAlwaysOnTop,
                onSetDevWindowAlwaysOnTop, onOpenStyleEditor, onOpenMemoryMonitor, onOpenStoryPrompt)
        }
    }
}

/**
 * The menus, one composable each.
 *
 * They were the body of [NavigationTopBar] until the bar outgrew what anyone can read at once: the
 * language menu alone lists 34 entries. Each takes the actions it fires and the accelerator lookup,
 * which comes from the same registry the key handlers use so a rebind shows on the item too.
 */
@Composable
private fun MenuBarScope.FileMenu(
    label: String,
    mnemonic: Char,
    accel: (ShortcutAction) -> KeyShortcut?,
    onNewSchedule: () -> Unit,
    onOpenSchedule: () -> Unit,
    onSaveSchedule: () -> Unit,
    onSaveScheduleAs: () -> Unit,
    onCloseSchedule: () -> Unit,
    onExit: () -> Unit,
) {
    Menu(label, mnemonic = mnemonic) {
        Item(
            stringResource(Res.string.menu_new_schedule),
            onClick = onNewSchedule,
            shortcut = accel(ShortcutAction.NEW_SCHEDULE)
        )
        Item(
            stringResource(Res.string.menu_open_schedule),
            onClick = onOpenSchedule,
            shortcut = accel(ShortcutAction.OPEN_SCHEDULE)
        )
        Item(
            stringResource(Res.string.menu_save_schedule),
            onClick = onSaveSchedule,
            shortcut = accel(ShortcutAction.SAVE_SCHEDULE)
        )
        Item(
            stringResource(Res.string.menu_save_schedule_as),
            onClick = onSaveScheduleAs,
            shortcut = accel(ShortcutAction.SAVE_SCHEDULE_AS)
        )
        Item(
            stringResource(Res.string.menu_close_schedule),
            onClick = onCloseSchedule,
            shortcut = accel(ShortcutAction.CLOSE_SCHEDULE)
        )
        Item(
            stringResource(Res.string.menu_exit),
            onClick = onExit,
            shortcut = accel(ShortcutAction.EXIT)
        )
    }
}

@Composable
private fun MenuBarScope.ScheduleMenu(
    label: String,
    mnemonic: Char,
    accel: (ShortcutAction) -> KeyShortcut?,
    onAddToSchedule: () -> Unit,
    onRemoveFromSchedule: () -> Unit,
    onClearSchedule: () -> Unit,
) {
    Menu(label, mnemonic = mnemonic) {
        Item(
            stringResource(Res.string.menu_add_to_schedule),
            onClick = onAddToSchedule,
            shortcut = accel(ShortcutAction.ADD_TO_SCHEDULE)
        )
        Item(
            stringResource(Res.string.menu_remove_from_schedule),
            onClick = onRemoveFromSchedule,
            shortcut = accel(ShortcutAction.REMOVE_FROM_SCHEDULE)
        )
        Item(stringResource(Res.string.menu_clear_schedule), onClick = onClearSchedule)
    }
}

@Composable
private fun MenuBarScope.EditMenu(
    label: String,
    mnemonic: Char,
    accel: (ShortcutAction) -> KeyShortcut?,
    onSettings: () -> Unit,
    onStatistics: () -> Unit,
) {
    Menu(label, mnemonic = mnemonic) {
        Item(
            stringResource(Res.string.menu_settings),
            onClick = onSettings,
            shortcut = accel(ShortcutAction.OPEN_SETTINGS)
        )
        Item(
            stringResource(Res.string.menu_statistics),
            onClick = onStatistics
        )
    }
}

@Composable
private fun MenuBarScope.ConnectMenu(
    onConnectToInstance: () -> Unit,
    onDisconnectInstance: () -> Unit,
    isInstanceLinkConnected: Boolean,
) {
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
}

@Composable
private fun MenuBarScope.ViewMenu(theme: (ThemeMode) -> Unit, currentTheme: ThemeMode) {
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
}

@Composable
private fun MenuBarScope.LanguageMenu(onLanguageChange: (Language) -> Unit) {
    Menu(stringResource(Res.string.menu_language), mnemonic = 'L') {
        LANGUAGES.forEach { entry ->
            Item(
                text = "${entry.flag} ${stringResource(entry.label)}",
                onClick = { onLanguageChange(entry.language) }
            )
        }
    }
}

/** One entry of the language menu: the flag shown beside the name, and what picking it selects. */
private data class LanguageEntry(val flag: String, val label: StringResource, val language: Language)

/**
 * The languages the app offers, in the order the menu lists them.
 *
 * A table rather than 34 near-identical `Item` blocks: adding a language is a line here, and the
 * menu cannot drift out of step with itself.
 */
private val LANGUAGES = listOf(
    LanguageEntry("🇷🇺", Res.string.language_russian, Language.RUSSIAN),
    LanguageEntry("🇺🇸", Res.string.language_english, Language.ENGLISH),
    LanguageEntry("🇺🇦", Res.string.language_ukrainian, Language.UKRAINIAN),
    LanguageEntry("🇰🇿", Res.string.language_kazakh, Language.KAZAKH),
    LanguageEntry("🇩🇪", Res.string.language_german, Language.GERMAN),
    LanguageEntry("🇵🇱", Res.string.language_polish, Language.POLISH),
    LanguageEntry("🇧🇾", Res.string.language_belarusian, Language.BELARUSIAN),
    LanguageEntry("🇨🇿", Res.string.language_czech, Language.CZECH),
    LanguageEntry("🇪🇸", Res.string.language_spanish, Language.SPANISH),
    LanguageEntry("🇫🇷", Res.string.language_french, Language.FRENCH),
    LanguageEntry("🇳🇱", Res.string.language_dutch, Language.DUTCH),
    LanguageEntry("🇵🇹", Res.string.language_portuguese, Language.PORTUGUESE),
    LanguageEntry("🇷🇴", Res.string.language_romanian, Language.ROMANIAN),
    LanguageEntry("🇸🇰", Res.string.language_slovak, Language.SLOVAK),
    LanguageEntry("🇪🇪", Res.string.language_estonian, Language.ESTONIAN),
    LanguageEntry("🇱🇻", Res.string.language_latvian, Language.LATVIAN),
    LanguageEntry("🇭🇷", Res.string.language_croatian, Language.CROATIAN),
    LanguageEntry("🇸🇪", Res.string.language_swedish, Language.SWEDISH),
    LanguageEntry("🇳🇴", Res.string.language_norwegian, Language.NORWEGIAN),
    LanguageEntry("🇫🇮", Res.string.language_finnish, Language.FINNISH),
    LanguageEntry("🇹🇷", Res.string.language_turkish, Language.TURKISH),
    LanguageEntry("🇺🇿", Res.string.language_uzbek, Language.UZBEK),
    LanguageEntry("🇸🇦", Res.string.language_arabic, Language.ARABIC),
    LanguageEntry("🇮🇷", Res.string.language_persian, Language.PERSIAN),
    LanguageEntry("🇮🇳", Res.string.language_hindi, Language.HINDI),
    LanguageEntry("🇳🇵", Res.string.language_nepali, Language.NEPALI),
    LanguageEntry("🇹🇭", Res.string.language_thai, Language.THAI),
    LanguageEntry("🇱🇦", Res.string.language_lao, Language.LAO),
    LanguageEntry("🇯🇵", Res.string.language_japanese, Language.JAPANESE),
    LanguageEntry("🇨🇳", Res.string.language_chinese, Language.CHINESE),
    LanguageEntry("🇮🇩", Res.string.language_indonesian, Language.INDONESIAN),
    LanguageEntry("🇲🇾", Res.string.language_malay, Language.MALAY),
    LanguageEntry("🇱🇰", Res.string.language_tamil, Language.TAMIL),
    LanguageEntry("🇵🇭", Res.string.language_tagalog, Language.TAGALOG),
    LanguageEntry("🇹🇿", Res.string.language_swahili, Language.SWAHILI),
)

@Composable
private fun MenuBarScope.HelpMenu(
    label: String,
    mnemonic: Char,
    accel: (ShortcutAction) -> KeyShortcut?,
    onGettingStarted: () -> Unit,
    onKeyboardShortcuts: () -> Unit,
    onHowToBlog: () -> Unit,
    onConverter: () -> Unit,
    onSongLibrary: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onContactUs: () -> Unit,
    onCheckForUpdates: () -> Unit,
) {
    Menu(label, mnemonic = mnemonic) {
        Item(stringResource(Res.string.menu_getting_started), onClick = onGettingStarted)
        Item(stringResource(Res.string.menu_keyboard_shortcuts), onClick = onKeyboardShortcuts, shortcut = accel(ShortcutAction.KEYBOARD_SHORTCUTS))
        Item(stringResource(Res.string.menu_how_to_blog), onClick = onHowToBlog)
        Item(stringResource(Res.string.open_converter), onClick = onConverter)
        Item(stringResource(Res.string.open_song_library), onClick = onSongLibrary)
        Item(stringResource(Res.string.menu_about), onClick = onAbout)
        Item(stringResource(Res.string.menu_help_item), onClick = onHelp)
        Item(stringResource(Res.string.menu_contact_us), onClick = onContactUs)
        Item(stringResource(Res.string.menu_check_for_updates), onClick = onCheckForUpdates)
    }
}

@Composable
private fun MenuBarScope.DeveloperMenu(
    isPresenterWindowVisible: Boolean,
    onSetPresenterWindowVisible: (Boolean) -> Unit,
    isDevWindowAlwaysOnTop: Boolean,
    onSetDevWindowAlwaysOnTop: (Boolean) -> Unit,
    onOpenStyleEditor: () -> Unit,
    onOpenMemoryMonitor: () -> Unit,
    onOpenStoryPrompt: () -> Unit,
) {
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
        Item(stringResource(Res.string.menu_developer_memory_monitor), onClick = onOpenMemoryMonitor)
        Item(stringResource(Res.string.menu_developer_story_prompt), onClick = onOpenStoryPrompt)
    }
}
