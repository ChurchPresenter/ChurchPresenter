package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import kotlinx.coroutines.CancellationException
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.awt.KeyboardFocusManager
import java.io.File
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * FileChooser backed by FileKit native OS dialogs (Windows Explorer dialog via
 * JNA/COM, macOS NSOpenPanel/NSSavePanel). Falls back to SwingFileChooser if the
 * native dialog fails.
 */
object FileKitFileChooser : FileChooser() {

    /** Latched after the first native-dialog failure so a broken machine doesn't retry (and re-report) per dialog. */
    @Volatile
    internal var nativeDialogsBroken = false

    /** Which of the three native pickers a request calls for. */
    internal enum class PickerMode { DIRECTORY, MULTIPLE_FILES, SINGLE_FILE }

    /**
     * The picker a request maps to. No call site selects multiple directories, so a directory
     * request is always a single pick even when [multiple] is set.
     */
    internal fun pickerMode(selectDirectory: Boolean, multiple: Boolean): PickerMode = when {
        selectDirectory -> PickerMode.DIRECTORY
        multiple -> PickerMode.MULTIPLE_FILES
        else -> PickerMode.SINGLE_FILE
    }

    /** One picked file as the list the base class expects, or null if nothing usable came back. */
    internal fun singleSelection(file: File?): List<Path>? =
        file?.toPathOrNull()?.let { listOf(it) }

    /**
     * Several picked files, with anything unconvertible dropped.
     *
     * An empty result becomes null: the base class reads null as "cancelled", and an empty list
     * would be unwrapped as a selection of nothing.
     */
    internal fun multipleSelection(files: List<File>?): List<Path>? =
        files?.mapNotNull { it.toPathOrNull() }?.takeIf { it.isNotEmpty() }

    /**
     * Splits [suggestedName] into the base name and default extension FileKit wants.
     *
     * Callers pass names WITH the extension ("schedule.cps") but FileKit appends the default
     * extension itself, so a matching extension has to come off first or the dialog opens
     * offering "schedule.cps.cps". A name carrying none of [extensions] is left whole and simply
     * gains the first one.
     */
    internal fun saveNameParts(suggestedName: String, extensions: List<String>): Pair<String, String?> {
        val matched = extensions.firstOrNull { suggestedName.endsWith(".$it", ignoreCase = true) }
        val extension = matched ?: extensions.firstOrNull()
        val baseName = matched?.let { suggestedName.dropLast(it.length + 1) } ?: suggestedName
        return baseName to extension
    }

    /** Settings shared by every native dialog: the title, owned by whatever window is active. */
    internal fun dialogSettings(title: String): FileKitDialogSettings =
        FileKitDialogSettings(title = title, parentWindow = parentWindow())

    /**
     * The extensions the native saver restricts to, or null for no restriction.
     *
     * An empty set is not the same as null here: FileKit reads null as "any file" but an empty set
     * as a saver that permits nothing, which would leave the operator unable to save at all.
     */
    internal fun allowedExtensions(extensions: List<String>): Set<String>? =
        extensions.takeIf { it.isNotEmpty() }?.toSet()

    /**
     * The whole open interaction: work out which picker the request needs, hand [openNative] the
     * mode, the type and the folder, and shape whatever comes back for the base class.
     *
     * [openNative] is a parameter rather than a direct call so the sequence can be exercised
     * without a native dialog; in production it is the FileKit picker.
     */
    internal suspend fun openWith(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        selectDirectory: Boolean,
        multiple: Boolean,
        openNative: suspend (PickerMode, FileKitType, PlatformFile) -> List<File>?
    ): List<Path>? {
        val mode = pickerMode(selectDirectory, multiple)
        val picked = openNative(mode, filters.toFileKitType(), PlatformFile(path.toFile()))
        return if (mode == PickerMode.MULTIPLE_FILES) {
            multipleSelection(picked)
        } else {
            singleSelection(picked?.firstOrNull())
        }
    }

    /**
     * The whole save interaction, with [saveNative] standing in for the native saver.
     *
     * The saver is handed the name already split from its extension — see [saveNameParts] — so a
     * change to that split shows up here as the wrong name reaching the dialog.
     */
    internal suspend fun saveWith(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        saveNative: suspend (baseName: String, defaultExtension: String?, allowed: Set<String>?, directory: PlatformFile) -> File?
    ): Path? {
        val extensions = filters.allExtensions()
        val (baseName, extension) = saveNameParts(suggestedName, extensions)
        val saved = saveNative(baseName, extension, allowedExtensions(extensions), PlatformFile(location.toFile()))
        return saved?.toPathOrNull()
    }

    /**
     * Runs [attempt] (the native dialog) and falls back to [fallback] (the Swing dialog) when the
     * native one is unavailable.
     *
     * Once a native dialog has failed on this machine [nativeDialogsBroken] latches, so every later
     * dialog goes straight to the fallback rather than failing — and re-reporting the same fault —
     * on each open. A [CancellationException] is the operator dismissing the dialog, not a fault,
     * so it is re-thrown untouched and must not latch the native path off. [context] labels the
     * crash report the fault path files.
     */
    internal suspend fun <T> withNativeDialog(
        context: String,
        attempt: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        if (nativeDialogsBroken) return fallback()
        return try {
            attempt()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            CrashReporter.reportException(t, context = context)
            nativeDialogsBroken = true
            fallback()
        }
    }

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = withNativeDialog(
        context = "FileKitFileChooser.chooseImpl",
        attempt = {
            val settings = dialogSettings(title)
            openWith(path, filters, selectDirectory, multiple) { mode, type, directory ->
                when (mode) {
                    PickerMode.DIRECTORY -> FileKit
                        .openDirectoryPicker(directory = directory, dialogSettings = settings)
                        ?.file?.let { listOf(it) }
                    PickerMode.MULTIPLE_FILES -> FileKit.openFilePicker(
                        type = type,
                        mode = FileKitMode.Multiple(),
                        directory = directory,
                        dialogSettings = settings
                    )?.map { it.file }
                    PickerMode.SINGLE_FILE -> FileKit.openFilePicker(
                        type = type,
                        mode = FileKitMode.Single,
                        directory = directory,
                        dialogSettings = settings
                    )?.file?.let { listOf(it) }
                }
            }
        },
        fallback = { SwingFileChooser.fallbackChoose(path, filters, title, selectDirectory, multiple) }
    )

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = withNativeDialog(
        context = "FileKitFileChooser.saveImpl",
        attempt = {
            val settings = dialogSettings(title)
            saveWith(location, suggestedName, filters) { baseName, extension, allowed, directory ->
                FileKit.openFileSaver(
                    suggestedName = baseName,
                    defaultExtension = extension,
                    allowedExtensions = allowed,
                    directory = directory,
                    dialogSettings = settings
                )?.file
            }
        },
        fallback = { SwingFileChooser.fallbackSave(location, suggestedName, filters, title) }
    )

    /**
     * Owner for the native dialog so it is modal to the window that opened it.
     * Must be resolved on the EDT: KeyboardFocusManager and Window.getWindows()
     * are AppContext-filtered and can return null/hidden helper windows (JCEF,
     * JavaFX) when queried from an IO thread.
     */
    internal fun parentWindow(): java.awt.Window? {
        if (SwingUtilities.isEventDispatchThread()) return resolveParentWindow()
        var window: java.awt.Window? = null
        try {
            SwingUtilities.invokeAndWait { window = resolveParentWindow() }
        } catch (_: Exception) {}
        return window
    }

    internal fun resolveParentWindow(): java.awt.Window? =
        chooseParentWindow(
            KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow,
            java.awt.Window.getWindows()
        )

    /**
     * Which window a native dialog should hang off, given the [activeWindow] the toolkit reports
     * (if any) and every [window] in the JVM.
     *
     * Order matters: the active window wins outright; failing that, a window that is both showing
     * and focused; failing that, the largest one still visible. The size tiebreak exists so a
     * hidden helper window — JCEF's, JavaFX's — is never chosen over the real app window, which
     * would put the dialog behind it with no icon and the wrong modality. Null means no owner, and
     * the dialog is centred on the screen instead.
     */
    internal fun chooseParentWindow(
        activeWindow: java.awt.Window?,
        windows: Array<java.awt.Window>
    ): java.awt.Window? =
        activeWindow
            ?: windows.firstOrNull { it.isShowing && it.isFocused }
            ?: windows.filter { it.isShowing && it.width > 0 && it.height > 0 }
                .maxByOrNull { it.width.toLong() * it.height }

    /** Flattens all filters into one extension list (native dialogs get a single combined filter). */
    internal fun List<FileNameExtensionFilter>.allExtensions(): List<String> =
        flatMap { it.extensions.toList() }.distinct()

    internal fun List<FileNameExtensionFilter>.toFileKitType(): FileKitType =
        allExtensions()
            .takeIf { it.isNotEmpty() }
            ?.let { FileKitType.File(it) }
            ?: FileKitType.File()

    /**
     * Windows can hand back a virtual shell item (e.g. the "This PC" node,
     * `::{20D04FE0-3AEA-1069-A2D8-08002B30309D}`) when a user selects a special
     * folder in the native picker's navigation pane. Such paths have no real
     * filesystem representation, so [File.toPath] throws; treat that as no
     * selection rather than letting it bubble up as a "native dialogs broken" crash.
     */
    internal fun File.toPathOrNull(): Path? = try {
        toPath()
    } catch (_: InvalidPathException) {
        null
    }
}
