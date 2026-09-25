package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.theme.components.RaisedButton
import androidx.compose.material3.MaterialTheme
import org.churchpresenter.theme.components.KeyButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.window.WindowPosition
import java.awt.KeyboardFocusManager
import java.beans.PropertyChangeListener
import org.churchpresenter.app.churchpresenter.staysAboveMainWindow
import org.churchpresenter.app.churchpresenter.usableScreenArea
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberDialogState
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.launch
import org.churchpresenter.app.churchpresenter.LocalMainWindowState
import org.churchpresenter.app.churchpresenter.centeredOnMainWindow
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.calendar_choose_logo_title
import churchpresenter.composeapp.generated.resources.calendar_export_title
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.OwnedFileDialog
import org.churchpresenter.app.churchpresenter.utils.isMacOs
import org.jetbrains.compose.resources.getString
import churchpresenter.composeapp.generated.resources.about_copyright
import churchpresenter.composeapp.generated.resources.ndi_trademark
import churchpresenter.composeapp.generated.resources.about_title
import churchpresenter.composeapp.generated.resources.app_name
import churchpresenter.composeapp.generated.resources.action_ok
import churchpresenter.composeapp.generated.resources.converter_window_title
import churchpresenter.composeapp.generated.resources.open_calendar_manager
import churchpresenter.composeapp.generated.resources.open_song_library
import churchpresenter.composeapp.generated.resources.diagnostic_info_save_failed
import churchpresenter.composeapp.generated.resources.diagnostic_info_saved
import churchpresenter.composeapp.generated.resources.lottie_gen_window_title
import churchpresenter.composeapp.generated.resources.open_crash_logs
import churchpresenter.composeapp.generated.resources.report_bug
import churchpresenter.composeapp.generated.resources.save_diagnostic_info
import churchpresenter.composeapp.generated.resources.style_editor_window_title
import churchpresenter.composeapp.generated.resources.submit_feature_request
import org.churchpresenter.app.churchpresenter.BuildConfig
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.app.churchpresenter.ui.theme.LocalLanguage
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.utils.DeviceInfoReport
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import churchpresenter.composeapp.generated.resources.ic_app_icon
import org.churchpresenter.app.churchpresenter.composables.ColorPickerDialog
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.calendar.ui.CalendarApp
import org.churchpresenter.songlibrary.ui.SongLibraryApp
import org.churchpresenter.converter.ui.ConverterTab
import org.churchpresenter.converter.ui.App as ConverterApp
import org.churchpresenter.converter.ui.Strings as ConverterStrings
import org.churchpresenter.lottiegen.App as LottieGenApp
import org.churchpresenter.lottiegen.band.BandFontPicker
import org.churchpresenter.lottiegen.editor.StyleEditorApp
import java.awt.Desktop
import java.awt.Window as AwtWindow
import java.io.File
import java.util.Locale
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.writeText
import org.churchpresenter.app.churchpresenter.composables.CopyLinkIconButton
import org.churchpresenter.app.churchpresenter.utils.SystemClipboard
import org.churchpresenter.app.churchpresenter.utils.UrlOpener
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.converterEvent
import org.churchpresenter.app.churchpresenter.utils.songLibraryUsageEvent

private const val GRADIENT_DARKEN = 0.45f

@Composable
fun AboutDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    theme: ThemeMode = ThemeMode.SYSTEM
) {
    if (!isVisible) return

    val mainWindowState = LocalMainWindowState.current
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(
            position = centeredOnMainWindow(mainWindowState, ABOUT_DIALOG_WIDTH, ABOUT_DIALOG_HEIGHT),
            width = ABOUT_DIALOG_WIDTH,
            height = ABOUT_DIALOG_HEIGHT
        ),
        title = stringResource(Res.string.about_title),
        resizable = false
    ) {
        AboutDialogContent(onDismiss = onDismiss, appSettings = appSettings, theme = theme)
    }
}

/** The GitHub issue template the "Report a Bug" button opens, and its copy button copies. */
internal const val BUG_REPORT_URL =
    "https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=bug_report.md"

/** The GitHub issue template behind "Feature Request". */
internal const val FEATURE_REQUEST_URL =
    "https://github.com/ChurchPresenter/ChurchPresenter/issues/new?template=feature_request.md"

/** One GitHub issue template: a full-width button that opens it, and a button that copies it. */
@Composable
private fun IssueLinkRow(
    label: String,
    url: String,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KeyButton(
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f),
            onClick = { onOpen(url) }
        ) {
            Text(label, maxLines = 2, textAlign = TextAlign.Center)
        }
        CopyLinkIconButton(url = url, onCopy = onCopy)
    }
}

@Composable
internal fun AboutDialogContent(
    onDismiss: () -> Unit,
    appSettings: AppSettings,
    theme: ThemeMode,
    /**
     * The version line, as a parameter only so the screenshot of this dialog can pin it.
     *
     * [BuildConfig.VERSION_DISPLAY] carries the build's git hash, so it changes with every commit —
     * and a committed image of this dialog would therefore be stale the moment it was recorded, and
     * would fail `verifyRoborazziJvm` for ever after. Nothing but the test passes anything here.
     */
    versionDisplay: String = BuildConfig.VERSION_DISPLAY,
    /** How an issue template is opened. A parameter so a test does not launch a real browser. */
    openUrl: (String) -> Unit = { UrlOpener.open(it) },
    /** How an issue-template address is copied, for when the browser opens on the wrong screen. */
    copyText: (String) -> Unit = { SystemClipboard.copy(it) },
) {
    AppWindowRoot(theme = theme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App icon on a gradient tile
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    lerp(MaterialTheme.colorScheme.primary, Color.Black, GRADIENT_DARKEN),
                                    MaterialTheme.colorScheme.primary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Res.drawable.ic_app_icon),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = versionDisplay,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.about_copyright, "2026"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Required by NDI's licence terms wherever the app offers NDI, alongside the same
                // line on the Projection settings card. Not optional, and not conditional on a
                // runtime being installed: the app offers the feature either way.
                Text(
                    text = stringResource(Res.string.ndi_trademark),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                // One link per row rather than two side by side. Side by side, the copy buttons
                // took enough width that "Feature Request" wrapped to two lines and its button
                // grew taller than the one beside it; a row each matches the full-width buttons
                // below and leaves every label on one line.
                IssueLinkRow(
                    label = stringResource(Res.string.report_bug),
                    url = BUG_REPORT_URL,
                    onOpen = openUrl,
                    onCopy = copyText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                IssueLinkRow(
                    label = stringResource(Res.string.submit_feature_request),
                    url = FEATURE_REQUEST_URL,
                    onOpen = openUrl,
                    onCopy = copyText,
                )
                Spacer(modifier = Modifier.height(8.dp))
                KeyButton(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val crashDir = File(System.getProperty("user.home"), ".churchpresenter/crash-reports")
                        crashDir.mkdirs()
                        Desktop.getDesktop().open(crashDir)
                    }
                ) {
                    Text(stringResource(Res.string.open_crash_logs), maxLines = 1, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(8.dp))
                val saveTitle = stringResource(Res.string.save_diagnostic_info)
                val savedMsg = stringResource(Res.string.diagnostic_info_saved)
                val saveFailedMsg = stringResource(Res.string.diagnostic_info_save_failed)
                val saveCoroutineScope = rememberCoroutineScope()
                KeyButton(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        saveCoroutineScope.launch {
                            var path = FileChooser.platformInstance.save(
                                location = null,
                                suggestedName = "churchpresenter-diagnostic-info.txt",
                                filters = listOf(FileNameExtensionFilter("Text (*.txt)", "txt")),
                                title = saveTitle
                            )
                            if (path != null) {
                                try {
                                    if (path.extension != "txt") {
                                        path = path.resolveSibling("${path.nameWithoutExtension}.txt")
                                    }
                                    path.writeText(DeviceInfoReport.generate(appSettings))
                                    JOptionPane.showMessageDialog(
                                        AwtWindow.getWindows().firstOrNull { it.isActive },
                                        savedMsg,
                                        saveTitle,
                                        JOptionPane.INFORMATION_MESSAGE
                                    )
                                } catch (_: Exception) {
                                    JOptionPane.showMessageDialog(
                                        AwtWindow.getWindows().firstOrNull { it.isActive },
                                        saveFailedMsg,
                                        saveTitle,
                                        JOptionPane.ERROR_MESSAGE
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text(saveTitle, maxLines = 1, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(8.dp))
                RaisedButton(
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                ) {
                    Text(stringResource(Res.string.action_ok))
                }
            }
        }
    }
}

@Composable
fun ConverterWindow(theme: ThemeMode, initialTab: Int = ConverterTab.BIBLES, onClose: () -> Unit) {
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.CONVERTER_OPENED) }
    val language = LocalLanguage.current
    // The converter is a separate module with its own `ResourceBundle`, which it initialises from
    // the OS locale — so without this it answers in the machine's language and ignores the one
    // chosen in the app. Set before the window composes, so the first frame is already right;
    // keyed on the language, so changing it while the window is open redraws it.
    remember(language) { ConverterStrings.setLocale(Locale.forLanguageTag(language.code)) }
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.converter_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1100.dp, height = 800.dp)
    ) {
        AppWindowRoot(theme = theme) {
            ConverterApp(
                initialTab = initialTab,
                onConverted = { sourceId -> converterEvent(sourceId)?.let { UsageEvents.record(it) } },
            )
        }
    }
}

/**
 * The Song Library Manager, in a window of its own beside the converter.
 *
 * Its own module, like the converter: it is given the folder the app keeps songs in and edits the
 * files there directly, so what it writes is what the app reads on its next scan. [onClosed] fires
 * once the window is gone, which is where the app rescans.
 */
@Composable
fun SongLibraryWindow(
    theme: ThemeMode,
    songStorageDirectory: String,
    /** How long a song usually stays on screen, measured -- shown in the editor's footer. */
    typicalSongSeconds: (SongItem) -> Int? = { null },
    onClose: () -> Unit,
) {
    // No locale plumbing here: the window's strings are Compose resources now, and the app already
    // sets the JVM default locale when the language changes — which is what picks values-xx.
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.SONG_LIBRARY_OPENED) }
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.open_song_library),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1420.dp, height = 880.dp)
    ) {
        AppWindowRoot(theme = theme) {
            SongLibraryApp(
                libraryFolder = File(songStorageDirectory),
                onClose = onClose,
                onUsage = { usage, count -> UsageEvents.record(songLibraryUsageEvent(usage), count) },
                typicalSeconds = typicalSongSeconds,
                // The row's Edit opens the app's own editor, so a song is edited in one place
                // whether it was reached from the Songs tab or from here.
                songEditor = { editing ->
                    EditSongDialog(
                        isVisible = true,
                        song = editing.song,
                        songbooks = editing.songbooks,
                        existingSongs = editing.allSongs,
                        theme = theme,
                        typicalSeconds = typicalSongSeconds(editing.song),
                        onDismiss = editing.onDismiss,
                        onSave = { edited, _ -> editing.onSave(edited) },
                    )
                },
            )
        }
    }
}

/**
 * The Calendar Manager, in a window of its own beside the Song Library Manager.
 *
 * Its own module, and given only two things: the folder to keep `calendar.json` in — the same
 * `~/.churchpresenter` the rest of what the app persists lives in — and the song folder its
 * add-item picker reads. Everything it cannot do on its own goes through [CalendarHost]: putting a
 * planned run of show into the Schedule tab, and seeing what is in it.
 *
 * A planned run of show is a `List<ScheduleItem>`, which is what the Schedule tab already holds, so
 * loading one is a copy rather than a conversion.
 */
@Composable
fun CalendarWindow(
    theme: ThemeMode,
    appDataDirectory: File,
    songStorageDirectory: String,
    host: CalendarHost,
    /** How long a song usually stays on screen, measured -- shown in the editor's footer. */
    typicalSongSeconds: (SongItem) -> Int? = { null },
    /**
     * Dialogs the app opens on the calendar's behalf -- the phone-invite QR. Composed inside this
     * window so it owns them: one composed in the main window's scope is owned by the main window,
     * and opening it brings the main window forward over the calendar.
     */
    dialogs: @Composable () -> Unit = {},
    mainWindow: AwtWindow? = null,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.CALENDAR_OPENED) }
    // Opens filling the usable part of the screen the main window is on -- the monitor less its
    // taskbar -- so the footer's Load into Schedule is never underneath it. A fixed 1280x860 was
    // taller than a 1080p screen at 125% has room for above the taskbar.
    val area = remember { usableScreenArea(mainWindow) }
    val windowState = if (area != null) {
        rememberWindowState(position = WindowPosition(area.x, area.y), size = DpSize(area.width, area.height))
    } else {
        rememberWindowState(size = DpSize(CALENDAR_WIDTH, CALENDAR_HEIGHT))
    }
    // Above the main window while either of the two is the one in use, and an ordinary window the
    // rest of the time: always on top outright would also cover other apps, and the app's own
    // dialogs and file choosers, which are active windows of their own while they are open.
    var aboveMainWindow by remember { mutableStateOf(false) }
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.open_calendar_manager),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = windowState,
        alwaysOnTop = aboveMainWindow,
    ) {
        DisposableEffect(window, mainWindow) {
            val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val listener = PropertyChangeListener {
                aboveMainWindow = staysAboveMainWindow(focus.activeWindow, mainWindow, window)
            }
            focus.addPropertyChangeListener(ACTIVE_WINDOW_PROPERTY, listener)
            onDispose { focus.removePropertyChangeListener(ACTIVE_WINDOW_PROPERTY, listener) }
        }
        // On macOS the export and logo dialogs are owned by this window -- see OwnedFileDialog.
        val ownedHost = remember(host, window) {
            if (!isMacOs(System.getProperty("os.name", ""))) {
                host
            } else {
                host.copy(
                    chooseExportFile = { suggested, folder ->
                        OwnedFileDialog.save(window, getString(Res.string.calendar_export_title), suggested, folder)
                    },
                    chooseImageFile = {
                        OwnedFileDialog.open(
                            window,
                            getString(Res.string.calendar_choose_logo_title),
                            setOf("png", "jpg", "jpeg"),
                        )
                    },
                )
            }
        }
        AppWindowRoot(theme = theme) {
            CalendarApp(
                storeFolder = appDataDirectory,
                songFolder = File(songStorageDirectory).takeIf { it.isDirectory },
                host = ownedHost,
                // The app's own picker, so a section's color is chosen exactly the way every other
                // color in the app is — one control, not a second one living in :calendar.
                colorPicker = { request ->
                    ColorPickerDialog(
                        initialHex = request.initialHex,
                        onDismiss = request.onDismiss,
                        onColorSelected = request.onPicked,
                    )
                },
                // The app's own Edit Song dialog, exactly as the Song Library Manager takes it —
                // one editor for a song, whether it is reached from the Songs tab, that window, or
                // a run of show being planned here. What it writes lands in the songs folder, which
                // SongsViewModel already watches.
                songEditor = { editing ->
                    EditSongDialog(
                        isVisible = true,
                        song = editing.song,
                        songbooks = editing.songbooks,
                        existingSongs = editing.allSongs,
                        theme = theme,
                        typicalSeconds = typicalSongSeconds(editing.song),
                        onDismiss = editing.onDismiss,
                        onSave = { edited, _ ->
                            editing.onSave(edited)
                            UsageEvents.record(UsageEvent.SONG_EDITED)
                        },
                    )
                },
                onClose = onClose,
            )
            dialogs()
        }
    }
}

@Composable
fun LottieGenWindow(
    theme: ThemeMode,
    outputDir: File?,
    onClose: () -> Unit,
    onFileSaved: (() -> Unit)? = null,
    canvasWidth: Int? = null,
    canvasHeight: Int? = null,
    fontPicker: BandFontPicker? = null,
) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.lottie_gen_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        AppWindowRoot(theme = theme) {
            // embedded = true regardless of outputDir: opened from the Help menu there is no output
            // folder, but the generator is still inside the app's theme and must follow it.
            LottieGenApp(
                outputDir = outputDir,
                onFileSaved = onFileSaved,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                embedded = true,
                fontPicker = fontPicker,
            )
        }
    }
}

@Composable
fun StyleEditorWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.style_editor_window_title),
        icon = painterResource(Res.drawable.ic_app_icon),
        state = rememberWindowState(width = 1500.dp, height = 950.dp)
    ) {
        AppWindowRoot(theme = theme) {
            StyleEditorApp(standalone = false)
        }
    }
}

private val CALENDAR_WIDTH = 1280.dp
private val CALENDAR_HEIGHT = 860.dp
private const val ACTIVE_WINDOW_PROPERTY = "activeWindow"
