package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * FileChooser backed by FileKit native OS dialogs (Windows Explorer dialog via
 * JNA/COM, macOS NSOpenPanel/NSSavePanel). Falls back to SwingFileChooser if the
 * native dialog fails.
 */
object FileKitFileChooser : FileChooser() {

    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = try {
        val directory = PlatformFile(path.toFile())
        val settings = FileKitDialogSettings(title = title)
        when {
            // No call site selects multiple directories, so a single pick is sufficient
            selectDirectory -> FileKit.openDirectoryPicker(directory = directory, dialogSettings = settings)
                ?.let { listOf(it.file.toPath()) }
            multiple -> FileKit.openFilePicker(
                type = filters.toFileKitType(),
                mode = FileKitMode.Multiple(),
                directory = directory,
                dialogSettings = settings
            )?.map { it.file.toPath() }
            else -> FileKit.openFilePicker(
                type = filters.toFileKitType(),
                mode = FileKitMode.Single,
                directory = directory,
                dialogSettings = settings
            )?.let { listOf(it.file.toPath()) }
        }
    } catch (t: Throwable) {
        CrashReporter.reportException(t, context = "FileKitFileChooser.chooseImpl")
        SwingFileChooser.fallbackChoose(path, filters, title, selectDirectory, multiple)
    }

    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = try {
        val extensions = filters.flatMap { it.extensions.toList() }.distinct()
        // Callers pass names WITH extension ("schedule.cps"); FileKit appends the
        // default extension itself, so strip it to avoid "schedule.cps.cps"
        val extension = extensions.firstOrNull { suggestedName.endsWith(".$it", ignoreCase = true) }
            ?: extensions.firstOrNull()
        val baseName = if (extension != null && suggestedName.endsWith(".$extension", ignoreCase = true)) {
            suggestedName.dropLast(extension.length + 1)
        } else {
            suggestedName
        }
        FileKit.openFileSaver(
            suggestedName = baseName,
            defaultExtension = extension,
            allowedExtensions = extensions.takeIf { it.isNotEmpty() }?.toSet(),
            directory = PlatformFile(location.toFile()),
            dialogSettings = FileKitDialogSettings(title = title)
        )?.file?.toPath()
    } catch (t: Throwable) {
        CrashReporter.reportException(t, context = "FileKitFileChooser.saveImpl")
        SwingFileChooser.fallbackSave(location, suggestedName, filters, title)
    }

    /** Flattens all filters into one extension set (native dialogs get a single combined filter). */
    private fun List<FileNameExtensionFilter>.toFileKitType(): FileKitType =
        flatMap { it.extensions.toList() }.distinct()
            .takeIf { it.isNotEmpty() }
            ?.let { FileKitType.File(it) }
            ?: FileKitType.File()
}
