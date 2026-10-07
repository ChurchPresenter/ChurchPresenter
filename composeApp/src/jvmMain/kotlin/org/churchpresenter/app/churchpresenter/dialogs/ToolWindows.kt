package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.height
import org.churchpresenter.lottiegen.GuidedControl
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.sharedui.utils.usableScreenArea
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import java.awt.Dialog
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.WindowConstants
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_choose_logo_title
import org.churchpresenter.strings.generated.resources.calendar_export_title
import org.churchpresenter.sharedui.filechooser.OwnedFileDialog
import org.churchpresenter.profiles.isMacOs
import org.jetbrains.compose.resources.getString
import org.churchpresenter.strings.generated.resources.converter_window_title
import org.churchpresenter.strings.generated.resources.open_calendar_manager
import org.churchpresenter.strings.generated.resources.open_song_library
import org.churchpresenter.strings.generated.resources.lottie_gen_window_title
import org.churchpresenter.strings.generated.resources.style_editor_window_title
import org.churchpresenter.app.churchpresenter.utils.AppWindowRoot
import org.churchpresenter.app.churchpresenter.ui.theme.LocalLanguage
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.sharedui.composables.ColorPickerDialog
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
import java.awt.Window as AwtWindow
import java.io.File
import java.util.Locale
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.app.churchpresenter.utils.converterEvent
import org.churchpresenter.app.churchpresenter.utils.songLibraryUsageEvent

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
        icon = painterResource(IconRes.drawable.ic_app_icon),
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
        icon = painterResource(IconRes.drawable.ic_app_icon),
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
    /** Raised by one to open the new-service sheet on the Schedule tab's rows -- see `CalendarApp`. */
    newServiceFromSchedule: Int = 0,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.CALENDAR_OPENED) }
    // Opens filling the usable part of the screen the main window is on -- the monitor less its
    // taskbar -- so the footer's Load into Schedule is never underneath it. A fixed 1280x860 was
    // taller than a 1080p screen at 125% has room for above the taskbar.
    val area = remember { usableScreenArea(mainWindow) }
    val title = stringResource(Res.string.open_calendar_manager)
    val icon = painterResource(IconRes.drawable.ic_app_icon)
    val density = LocalDensity.current
    val currentOnClose by rememberUpdatedState(onClose)
    // Owned by the main window: the system keeps it in front of that window, and in front of
    // nothing else. It was an ordinary window made always-on-top while either of the two was in
    // use, and on Windows that flag stayed on -- it covered other apps, and the save dialog and its
    // "replace the file?" question opened behind it, so Save seemed to do nothing and overwriting
    // looked like a hang (#651). An owned window needs no flag at all.
    SwingDialog(
        create = {
            ComposeDialog(mainWindow, Dialog.ModalityType.MODELESS).apply {
                this.title = title
                setIconImage(icon.toAwtImage(density, LayoutDirection.Ltr))
                isResizable = true
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) = currentOnClose()
                })
                // Opens filling the usable part of the screen the main window is on -- the monitor
                // less its taskbar -- so the footer's Load into Schedule is never underneath it. A
                // fixed 1280x860 was taller than a 1080p screen at 125% has room for above the taskbar.
                if (area != null) {
                    setBounds(area.x.px(), area.y.px(), area.width.px(), area.height.px())
                } else {
                    setSize(CALENDAR_WIDTH.px(), CALENDAR_HEIGHT.px())
                    setLocationRelativeTo(mainWindow)
                }
            }
        },
        dispose = ComposeDialog::dispose,
    ) {
        // The export and logo dialogs are owned by this window where the platform's own chooser would
        // not be -- see OwnedFileDialog.
        val ownedHost = remember(host, window) {
            if (!usesOwnedFileDialog(System.getProperty("os.name", ""))) {
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
                newServiceFromSchedule = newServiceFromSchedule,
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
        icon = painterResource(IconRes.drawable.ic_app_icon),
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        AppWindowRoot(theme = theme) {
            // embedded = true regardless of outputDir: opened from the Help menu there is no output
            // folder, but the generator is still inside the app's theme and must follow it.
            // This window's own spotlight: Wick's lower third tour rings Save Lower Third.
            GuideSpotlightHost(Modifier.fillMaxSize()) {
                LottieGenApp(
                    outputDir = outputDir,
                    onFileSaved = onFileSaved,
                    canvasWidth = canvasWidth,
                    canvasHeight = canvasHeight,
                    embedded = true,
                    fontPicker = fontPicker,
                    controlTag = { control ->
                        Modifier.guideTarget(
                            when (control) {
                                GuidedControl.NAME -> GuideTargets.LOWER_THIRD_NAME
                                GuidedControl.INFO -> GuideTargets.LOWER_THIRD_INFO
                                GuidedControl.SAVE -> GuideTargets.LOWER_THIRD_SAVE
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun StyleEditorWindow(theme: ThemeMode, onClose: () -> Unit) {
    Window(
        onCloseRequest = onClose,
        title = stringResource(Res.string.style_editor_window_title),
        icon = painterResource(IconRes.drawable.ic_app_icon),
        state = rememberWindowState(width = 1500.dp, height = 950.dp)
    ) {
        AppWindowRoot(theme = theme) {
            StyleEditorApp(standalone = false)
        }
    }
}

/** A size in dp as the AWT pixels a window is laid out in -- the same unit on desktop. */
private fun Dp.px(): Int = value.toInt()

/**
 * Whether the calendar's file dialogs are AWT's own, owned by its window, rather than the app's
 * usual chooser: on macOS FileKit's panel is app-modal with no parent and can be left off screen,
 * and on Windows it is parented to the main window, behind this one. Linux keeps the desktop
 * portal, which AWT's dialog there is no substitute for.
 */
internal fun usesOwnedFileDialog(osName: String): Boolean =
    isMacOs(osName) || osName.lowercase().startsWith("windows")

private val CALENDAR_WIDTH = 1280.dp

private val CALENDAR_HEIGHT = 860.dp
