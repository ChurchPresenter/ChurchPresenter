package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.dialogs.ToolWindowFrame
import org.churchpresenter.dialogs.ToolWindowSpec
import org.churchpresenter.dialogs.appToolWindowFrame
import org.churchpresenter.lottiegen.GuidedControl
import org.churchpresenter.lottiegen.ControlTag
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.converter_window_title
import org.churchpresenter.strings.generated.resources.open_calendar_manager
import org.churchpresenter.strings.generated.resources.open_song_library
import org.churchpresenter.strings.generated.resources.lottie_gen_window_title
import org.churchpresenter.strings.generated.resources.style_editor_window_title
import org.churchpresenter.sharedui.utils.AppWindowRoot
import org.churchpresenter.sharedui.language.LocalLanguage
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.stringResource
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
import org.churchpresenter.telemetry.converterEvent
import org.churchpresenter.telemetry.songLibraryUsageEvent

@Composable
fun ConverterWindow(
    theme: ThemeMode,
    initialTab: Int = ConverterTab.BIBLES,
    initialSongSource: String? = null,
    onClose: () -> Unit,
    /** The window it opens in -- see [ToolWindowFrame]. */
    frame: ToolWindowFrame = appToolWindowFrame,
) {
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.CONVERTER_OPENED) }
    val language = LocalLanguage.current
    // The converter is a separate module with its own `ResourceBundle`, which it initialises from
    // the OS locale — so without this it answers in the machine's language and ignores the one
    // chosen in the app. Set before the window composes, so the first frame is already right;
    // keyed on the language, so changing it while the window is open redraws it.
    remember(language) { ConverterStrings.setLocale(Locale.forLanguageTag(language.code)) }
    frame(ToolWindowSpec(stringResource(Res.string.converter_window_title), DpSize(1100.dp, 800.dp), onClose)) {
        AppWindowRoot(theme = theme) {
            ConverterApp(
                initialTab = initialTab,
                initialSongSource = initialSongSource,
                onConverted = ::recordConversion,
            )
        }
    }
}

internal fun recordConversion(sourceId: String) {
    converterEvent(sourceId)?.let { UsageEvents.record(it) }
}

/**
 * The Song Library Manager, in a window of its own beside the converter.
 *
 * Its own module, like the converter: it is given the folder the app keeps songs in and edits the
 * files there directly, so what it writes is what the app reads on its next scan. [onClosed] fires
 * once the window is gone, which is where the app rescans.
 */
@Composable
internal fun SongLibraryWindow(
    theme: ThemeMode,
    songStorageDirectory: String,
    /** How long a song usually stays on screen, measured -- shown in the editor's footer. */
    typicalSongSeconds: (SongItem) -> Int? = { null },
    onClose: () -> Unit,
    /** The window it opens in -- see [ToolWindowFrame]. */
    frame: ToolWindowFrame = appToolWindowFrame,
    songEditorDialog: SongEditorDialog = appSongEditorDialog,
) {
    // No locale plumbing here: the window's strings are Compose resources now, and the app already
    // sets the JVM default locale when the language changes — which is what picks values-xx.
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.SONG_LIBRARY_OPENED) }
    frame(ToolWindowSpec(stringResource(Res.string.open_song_library), DpSize(1420.dp, 880.dp), onClose)) {
        AppWindowRoot(theme = theme) {
            SongLibraryApp(
                libraryFolder = File(songStorageDirectory),
                onClose = onClose,
                onUsage = { usage, count -> UsageEvents.record(songLibraryUsageEvent(usage), count) },
                typicalSeconds = typicalSongSeconds,
                // The row's Edit opens the app's own editor, so a song is edited in one place
                // whether it was reached from the Songs tab or from here.
                songEditor = remember(theme, typicalSongSeconds, songEditorDialog) {
                    songLibrarySongEditor(theme, typicalSongSeconds, songEditorDialog)
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
internal fun CalendarWindow(
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
    frame: CalendarWindowFrame = appCalendarWindowFrame,
    songEditorDialog: SongEditorDialog = appSongEditorDialog,
) {
    LaunchedEffect(Unit) { UsageEvents.recordOncePerRun(UsageEvent.CALENDAR_OPENED) }
    frame(CalendarWindowSpec(stringResource(Res.string.open_calendar_manager), mainWindow, onClose)) { choosers ->
        // The export and logo dialogs are owned by this window where the platform's own chooser would
        // not be -- see OwnedFileDialog.
        val ownedHost = remember(host, choosers) {
            calendarOwnedHost(host, System.getProperty("os.name", ""), choosers)
        }
        AppWindowRoot(theme = theme) {
            CalendarApp(
                newServiceFromSchedule = newServiceFromSchedule,
                storeFolder = appDataDirectory,
                songFolder = File(songStorageDirectory).takeIf { it.isDirectory },
                host = ownedHost,
                // The app's own picker, so a section's color is chosen exactly the way every other
                // color in the app is — one control, not a second one living in :calendar.
                colorPicker = { request -> CalendarColorPicker(request) },
                // The app's own Edit Song dialog, exactly as the Song Library Manager takes it —
                // one editor for a song, whether it is reached from the Songs tab, that window, or
                // a run of show being planned here. What it writes lands in the songs folder, which
                // SongsViewModel already watches.
                songEditor = remember(theme, typicalSongSeconds, songEditorDialog) {
                    calendarSongEditor(theme, typicalSongSeconds, songEditorDialog)
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
    /** The window it opens in -- see [ToolWindowFrame]. */
    frame: ToolWindowFrame = appToolWindowFrame,
) {
    frame(ToolWindowSpec(stringResource(Res.string.lottie_gen_window_title), DpSize(1200.dp, 800.dp), onClose)) {
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
                    controlTag = lowerThirdControlTag,
                )
            }
        }
    }
}

internal val lowerThirdControlTag: ControlTag = { control ->
    Modifier.guideTarget(
        when (control) {
            GuidedControl.NAME -> GuideTargets.LOWER_THIRD_NAME
            GuidedControl.INFO -> GuideTargets.LOWER_THIRD_INFO
            GuidedControl.SAVE -> GuideTargets.LOWER_THIRD_SAVE
        },
    )
}

@Composable
fun StyleEditorWindow(
    theme: ThemeMode,
    onClose: () -> Unit,
    /** The window it opens in -- see [ToolWindowFrame]. */
    frame: ToolWindowFrame = appToolWindowFrame,
) {
    frame(ToolWindowSpec(stringResource(Res.string.style_editor_window_title), DpSize(1500.dp, 950.dp), onClose)) {
        AppWindowRoot(theme = theme) {
            StyleEditorApp(standalone = false)
        }
    }
}
