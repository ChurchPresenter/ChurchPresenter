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
    private var nativeDialogsBroken = false

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = try {
        if (nativeDialogsBroken) return SwingFileChooser.fallbackChoose(path, filters, title, selectDirectory, multiple)
        val directory = PlatformFile(path.toFile())
        val settings = FileKitDialogSettings(title = title, parentWindow = parentWindow())
        when {
            // No call site selects multiple directories, so a single pick is sufficient
            selectDirectory -> FileKit.openDirectoryPicker(directory = directory, dialogSettings = settings)
                ?.file?.toPathOrNull()?.let { listOf(it) }
            multiple -> FileKit.openFilePicker(
                type = filters.toFileKitType(),
                mode = FileKitMode.Multiple(),
                directory = directory,
                dialogSettings = settings
            )?.mapNotNull { it.file.toPathOrNull() }?.takeIf { it.isNotEmpty() }
            else -> FileKit.openFilePicker(
                type = filters.toFileKitType(),
                mode = FileKitMode.Single,
                directory = directory,
                dialogSettings = settings
            )?.file?.toPathOrNull()?.let { listOf(it) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        CrashReporter.reportException(t, context = "FileKitFileChooser.chooseImpl")
        nativeDialogsBroken = true
        SwingFileChooser.fallbackChoose(path, filters, title, selectDirectory, multiple)
    }

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = try {
        if (nativeDialogsBroken) return SwingFileChooser.fallbackSave(location, suggestedName, filters, title)
        val extensions = filters.allExtensions()
        // Callers pass names WITH extension ("schedule.cps"); FileKit appends the
        // default extension itself, so strip it to avoid "schedule.cps.cps"
        val matched = extensions.firstOrNull { suggestedName.endsWith(".$it", ignoreCase = true) }
        val extension = matched ?: extensions.firstOrNull()
        val baseName = matched?.let { suggestedName.dropLast(it.length + 1) } ?: suggestedName
        FileKit.openFileSaver(
            suggestedName = baseName,
            defaultExtension = extension,
            allowedExtensions = extensions.takeIf { it.isNotEmpty() }?.toSet(),
            directory = PlatformFile(location.toFile()),
            dialogSettings = FileKitDialogSettings(title = title, parentWindow = parentWindow())
        )?.file?.toPathOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        CrashReporter.reportException(t, context = "FileKitFileChooser.saveImpl")
        nativeDialogsBroken = true
        SwingFileChooser.fallbackSave(location, suggestedName, filters, title)
    }

    /**
     * Owner for the native dialog so it is modal to the window that opened it.
     * Must be resolved on the EDT: KeyboardFocusManager and Window.getWindows()
     * are AppContext-filtered and can return null/hidden helper windows (JCEF,
     * JavaFX) when queried from an IO thread.
     */
    private fun parentWindow(): java.awt.Window? {
        if (SwingUtilities.isEventDispatchThread()) return resolveParentWindow()
        var window: java.awt.Window? = null
        try {
            SwingUtilities.invokeAndWait { window = resolveParentWindow() }
        } catch (_: Exception) {}
        return window
    }

    private fun resolveParentWindow(): java.awt.Window? =
        KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: java.awt.Window.getWindows().firstOrNull { it.isShowing && it.isFocused }
            ?: java.awt.Window.getWindows().filter { it.isShowing && it.width > 0 && it.height > 0 }
                .maxByOrNull { it.width.toLong() * it.height }

    /** Flattens all filters into one extension list (native dialogs get a single combined filter). */
    private fun List<FileNameExtensionFilter>.allExtensions(): List<String> =
        flatMap { it.extensions.toList() }.distinct()

    private fun List<FileNameExtensionFilter>.toFileKitType(): FileKitType =
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
    private fun File.toPathOrNull(): Path? = try {
        toPath()
    } catch (_: InvalidPathException) {
        null
    }
}
