package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.SwingDialog
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.ui.ColorPickerRequest
import org.churchpresenter.calendar.ui.SongEditRequest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.dialogs.songEditorBackgroundButton
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_app_icon
import org.churchpresenter.profiles.isMacOs
import org.churchpresenter.sharedui.composables.ColorPickerDialog
import org.churchpresenter.sharedui.filechooser.OwnedFileDialog
import org.churchpresenter.sharedui.utils.ScreenArea
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.sharedui.utils.usableScreenArea
import org.churchpresenter.songlibrary.ui.SongEditorRequest
import org.churchpresenter.songs.EditSongDialog
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.calendar_choose_logo_title
import org.churchpresenter.strings.generated.resources.calendar_export_title
import org.churchpresenter.theme.ThemeMode
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import java.awt.Dialog
import java.awt.Rectangle
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.WindowConstants
import java.awt.Window as AwtWindow

internal data class CalendarWindowSpec(val title: String, val mainWindow: AwtWindow?, val onClose: () -> Unit)

internal class CalendarFileChoosers(
    val save: suspend (title: String, suggestedName: String, folder: File?) -> File?,
    val open: suspend (title: String, extensions: Set<String>) -> File?,
)

internal typealias CalendarWindowFrame =
    @Composable (spec: CalendarWindowSpec, content: @Composable (CalendarFileChoosers) -> Unit) -> Unit

internal val appCalendarWindowFrame: CalendarWindowFrame = { spec, content -> CalendarDialogWindow(spec, content) }

@Composable
private fun CalendarDialogWindow(spec: CalendarWindowSpec, content: @Composable (CalendarFileChoosers) -> Unit) {
    // Opens filling the usable part of the screen the main window is on -- the monitor less its
    // taskbar -- so the footer's Load into Schedule is never underneath it. A fixed 1280x860 was
    // taller than a 1080p screen at 125% has room for above the taskbar.
    val bounds = remember { calendarWindowBounds(usableScreenArea(spec.mainWindow)) }
    val icon = painterResource(IconRes.drawable.ic_app_icon)
    val density = LocalDensity.current
    val currentOnClose by rememberUpdatedState(spec.onClose)
    // Owned by the main window: the system keeps it in front of that window, and in front of
    // nothing else. It was an ordinary window made always-on-top while either of the two was in
    // use, and on Windows that flag stayed on -- it covered other apps, and the save dialog and its
    // "replace the file?" question opened behind it, so Save seemed to do nothing and overwriting
    // looked like a hang (#651). An owned window needs no flag at all.
    SwingDialog(
        create = {
            ComposeDialog(spec.mainWindow, Dialog.ModalityType.MODELESS).apply {
                this.title = spec.title
                setIconImage(icon.toAwtImage(density, LayoutDirection.Ltr))
                isResizable = true
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) = currentOnClose()
                })
                if (bounds != null) {
                    setBounds(bounds)
                } else {
                    setSize(CALENDAR_WIDTH.px(), CALENDAR_HEIGHT.px())
                    setLocationRelativeTo(spec.mainWindow)
                }
            }
        },
        dispose = ComposeDialog::dispose,
    ) {
        val choosers = remember(window) {
            CalendarFileChoosers(
                save = { title, suggested, folder -> OwnedFileDialog.save(window, title, suggested, folder) },
                open = { title, extensions -> OwnedFileDialog.open(window, title, extensions) },
            )
        }
        content(choosers)
    }
}

internal fun calendarWindowBounds(area: ScreenArea?): Rectangle? =
    area?.let { Rectangle(it.x.px(), it.y.px(), it.width.px(), it.height.px()) }

internal fun calendarOwnedHost(host: CalendarHost, osName: String, choosers: CalendarFileChoosers): CalendarHost =
    if (!usesOwnedFileDialog(osName)) {
        host
    } else {
        host.copy(
            chooseExportFile = { suggested, folder ->
                choosers.save(getString(Res.string.calendar_export_title), suggested, folder)
            },
            chooseImageFile = {
                choosers.open(getString(Res.string.calendar_choose_logo_title), setOf("png", "jpg", "jpeg"))
            },
        )
    }

@Composable
internal fun CalendarColorPicker(request: ColorPickerRequest) {
    ColorPickerDialog(
        initialHex = request.initialHex,
        onDismiss = request.onDismiss,
        onColorSelected = request.onPicked,
    )
}

internal typealias SongEditorDialog =
    @Composable (theme: ThemeMode, editing: SongEditorRequest, typicalSeconds: Int?) -> Unit

internal val appSongEditorDialog: SongEditorDialog = { theme, editing, typicalSeconds ->
    AppEditSongDialog(theme, editing, typicalSeconds)
}

@Composable
private fun AppEditSongDialog(theme: ThemeMode, editing: SongEditorRequest, typicalSeconds: Int?) {
    EditSongDialog(
        backgroundButton = songEditorBackgroundButton,
        isVisible = true,
        song = editing.song,
        songbooks = editing.songbooks,
        existingSongs = editing.allSongs,
        theme = theme,
        typicalSeconds = typicalSeconds,
        onDismiss = editing.onDismiss,
        onSave = { edited, _ -> editing.onSave(edited) },
    )
}

internal fun songLibrarySongEditor(
    theme: ThemeMode,
    typicalSongSeconds: (SongItem) -> Int?,
    dialog: SongEditorDialog,
): @Composable (SongEditorRequest) -> Unit = { editing ->
    dialog(theme, editing, typicalSongSeconds(editing.song))
}

internal fun calendarSongEditor(
    theme: ThemeMode,
    typicalSongSeconds: (SongItem) -> Int?,
    dialog: SongEditorDialog,
): @Composable (SongEditRequest) -> Unit = { editing ->
    val request = SongEditorRequest(
        song = editing.song,
        songbooks = editing.songbooks,
        allSongs = editing.allSongs,
        onSave = { edited ->
            editing.onSave(edited)
            UsageEvents.record(UsageEvent.SONG_EDITED)
        },
        onDismiss = editing.onDismiss,
    )
    dialog(theme, request, typicalSongSeconds(editing.song))
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
