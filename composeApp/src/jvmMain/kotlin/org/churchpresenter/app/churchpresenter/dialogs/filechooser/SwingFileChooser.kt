package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter


object SwingFileChooser : FileChooser() {

    /** Bridges allowing FileKitFileChooser to fall back to the Swing dialog if the native one fails. */
    internal suspend fun fallbackChoose(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = chooseImpl(path, filters, title, selectDirectory, multiple)

    internal suspend fun fallbackSave(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = saveImpl(location, suggestedName, filters, title)

    /**
     * Hidden owner frame that provides the app icon to file chooser dialogs.
     *
     * Stays private: widening it would not make it reachable from a test, because building a
     * `JFrame` at all needs a display. [configureOwnerFrame] holds everything about it that can be
     * checked without one.
     */
    private val ownerFrame: JFrame by lazy { JFrame().apply { configureOwnerFrame(this) } }

    /**
     * Makes [frame] the invisible, iconned owner a file dialog hangs off: no decorations, no size,
     * centred, carrying the app icon.
     *
     * A frame that cannot take the icon is still a usable owner, so a failure there is swallowed —
     * the alternative is no file dialogs at all on a machine whose icon is unreadable.
     */
    internal fun configureOwnerFrame(frame: JFrame) {
        frame.isUndecorated = true
        frame.setSize(0, 0)
        frame.setLocationRelativeTo(null)
        try {
            frame.iconImage = loadAppIcon()
        } catch (_: Exception) {}
    }

    /** Loads the app icon from appResources (packaged) or the source tree (IDE run). */
    private fun loadAppIcon(): java.awt.Image? {
        // 1. Packaged app: compose.application.resources.dir is set
        System.getProperty("compose.application.resources.dir")?.let { resDir ->
            listOf("icon-32.png", "icon-48.png", "icon.png")
                .map { File(resDir, it) }
                .firstOrNull { it.exists() }
                ?.let { return ImageIO.read(it) }
        }
        // 2. IDE / development run: walk up from working directory to find appResources
        var dir = File(System.getProperty("user.dir"))
        repeat(6) {
            val candidate = File(dir, "composeApp/src/jvmMain/appResources/common/icon-32.png")
            if (candidate.exists()) return ImageIO.read(candidate)
            dir = dir.parentFile ?: return@repeat
        }
        return null
    }

    /**
     * Offers [filters] and pre-selects the first one, which is the filter the dialog opens showing.
     *
     * With filters present "All Files" is removed, so only supported types are selectable; with no
     * filters nothing is added and the dialog keeps its default "All Files" entry.
     */
    internal fun JFileChooser.applyFilters(filters: List<FileNameExtensionFilter>) {
        if (filters.isEmpty()) return
        // Disable "All Files" so only the supported types are selectable
        isAcceptAllFileFilterUsed = false
        filters.forEach { addChoosableFileFilter(it) }
        // Pre-select the first filter as the active one
        fileFilter = filters.first()
    }

    /** Everything the open dialog is told before it is shown. */
    internal fun configureOpen(
        chooser: JFileChooser,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ) {
        chooser.dialogTitle = title
        chooser.fileSelectionMode =
            if (selectDirectory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
        chooser.isMultiSelectionEnabled = multiple
        chooser.applyFilters(filters)
    }

    /** Everything the save dialog is told before it is shown, including the name it opens with. */
    internal fun configureSave(
        chooser: JFileChooser,
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ) {
        chooser.dialogTitle = title
        chooser.selectedFile = location.resolve(suggestedName).toFile()
        chooser.applyFilters(filters)
    }

    /** Reads the open dialog's outcome; anything but approval means the operator cancelled. */
    internal fun openResult(returnCode: Int, chooser: JFileChooser, multiple: Boolean): List<Path>? =
        if (returnCode == JFileChooser.APPROVE_OPTION) {
            if (multiple) chooser.selectedFiles.map { it.toPath() } else listOf(chooser.selectedFile.toPath())
        } else {
            null
        }

    /** Reads the save dialog's outcome; anything but approval means the operator cancelled. */
    internal fun saveResult(returnCode: Int, chooser: JFileChooser): Path? =
        if (returnCode == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.toPath()
        } else {
            null
        }

    /**
     * Runs [block] on the event dispatch thread and hands back what it returned.
     *
     * Swing insists a dialog is opened from the EDT, but the callers are suspending functions on
     * whatever dispatcher they happen to be on, so the value has to be carried back out by hand.
     * A failure inside [block] surfaces as `InvocationTargetException`, and calling this from the
     * EDT itself is an error — both are `invokeAndWait`'s own behaviour, unchanged.
     */
    internal fun <T> onEventDispatchThread(block: () -> T): T {
        var result: T? = null
        SwingUtilities.invokeAndWait { result = block() }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    /**
     * Shows [dialog] owned by [frame] and returns its return code.
     *
     * The frame exists only to give the dialog the app icon, so it is visible for exactly as long
     * as the dialog is up and hidden again afterwards — leaving it showing would strand a
     * zero-sized window on the desktop. [frame] is a parameter so this holds without a display;
     * in production it is always [ownerFrame].
     */
    internal fun showOwned(frame: JFrame, dialog: (JFrame) -> Int): Int {
        frame.isVisible = true
        val returnCode = dialog(frame)
        frame.isVisible = false
        return returnCode
    }

    /**
     * The whole open interaction: build a chooser at [path], configure it, put it on screen with
     * [show], and read the outcome.
     *
     * [show] is a parameter rather than a direct call so the sequence can be exercised without a
     * display; in production it is always [showOwned].
     */
    internal fun openWith(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean,
        show: (JFileChooser) -> Int
    ): List<Path>? {
        val chooser = JFileChooser(path.toFile())
        configureOpen(chooser, filters, title, selectDirectory, multiple)
        return openResult(show(chooser), chooser, multiple)
    }

    /** The whole save interaction, with [show] standing in for the step that needs a display. */
    internal fun saveWith(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String,
        show: (JFileChooser) -> Int
    ): Path? {
        val chooser = JFileChooser(location.toFile())
        configureSave(chooser, location, suggestedName, filters, title)
        return saveResult(show(chooser), chooser)
    }

    /**
     * The full open path: on the event dispatch thread, build and configure a chooser, show it
     * owned by [frame] via [showDialog], and read the result. [frame] and [showDialog] are
     * parameters so the orchestration runs under test with a stand-in frame and a stand-in for the
     * one display call; in production they are [ownerFrame] and `JFileChooser.showOpenDialog`.
     */
    internal fun runOpen(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean,
        frame: JFrame,
        showDialog: (JFileChooser, JFrame) -> Int
    ): List<Path>? = onEventDispatchThread {
        openWith(path, filters, title, selectDirectory, multiple) { chooser ->
            showOwned(frame) { showDialog(chooser, it) }
        }
    }

    /** The full save path, with [frame] and [showDialog] injected as in [runOpen]. */
    internal fun runSave(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String,
        frame: JFrame,
        showDialog: (JFileChooser, JFrame) -> Int
    ): Path? = onEventDispatchThread {
        saveWith(location, suggestedName, filters, title) { chooser ->
            showOwned(frame) { showDialog(chooser, it) }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun chooseImpl(
        path: Path,
        filters: List<FileNameExtensionFilter>,
        title: String,
        selectDirectory: Boolean,
        multiple: Boolean
    ): List<Path>? = runOpen(path, filters, title, selectDirectory, multiple, ownerFrame) { chooser, frame ->
        chooser.showOpenDialog(frame)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun saveImpl(
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String
    ): Path? = runSave(location, suggestedName, filters, title, ownerFrame) { chooser, frame ->
        chooser.showSaveDialog(frame)
    }
}
