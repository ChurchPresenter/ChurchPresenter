package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import io.github.vinceglb.filekit.dialogs.FileKitType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import java.awt.Image
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-platform choosers, minus the dialogs themselves.
 *
 * `SwingFileChooser` and `FileKitFileChooser` are mostly calls into a toolkit that needs a display,
 * but the ordinary logic around those calls — what the dialog is configured with, which picker a
 * request maps to, what comes back out — is `internal` and called directly here. The one step that
 * genuinely needs a display is a parameter (`openWith`/`saveWith` take the showing as a lambda), so
 * the whole sequence runs under test with only that step stood in for.
 *
 * Touching these objects builds no window: the Swing owner frame is created lazily, on first use of
 * a real dialog. `loadAppIcon` is still reached by reflection because it is private for its own
 * reasons; new tests should widen to `internal` instead.
 *
 * [FileChooserTest] covers the shared base class, which is where most of the behaviour lives.
 */
class PlatformFileChooserTest {

    private lateinit var dir: File
    private var realResourcesDir: String? = null
    private var realUserDir: String? = null

    @BeforeTest
    fun isolateLookupPaths() {
        dir = Files.createTempDirectory("cp-platform-chooser-test").toFile()
        realResourcesDir = System.getProperty("compose.application.resources.dir")
        realUserDir = System.getProperty("user.dir")
        System.clearProperty("compose.application.resources.dir")
        // The source-tree fallback walks up from user.dir; pointed at a temp folder it finds
        // nothing, so tests decide for themselves what is discoverable.
        System.setProperty("user.dir", dir.absolutePath)
        // nativeDialogsBroken is a per-JVM latch; force it off so one test's fault can't decide
        // another's, and so a leftover value from an earlier suite doesn't skew these.
        FileKitFileChooser.nativeDialogsBroken = false
    }

    @AfterTest
    fun restoreLookupPaths() {
        realResourcesDir?.let { System.setProperty("compose.application.resources.dir", it) }
            ?: System.clearProperty("compose.application.resources.dir")
        realUserDir?.let { System.setProperty("user.dir", it) }
        dir.deleteRecursively()
        FileKitFileChooser.nativeDialogsBroken = false
    }

    /** Writes a real square PNG whose width identifies which file was loaded. */
    private fun icon(name: String, size: Int, into: File = dir) {
        into.mkdirs()
        ImageIO.write(BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB), "png", File(into, name))
    }

    // ── SwingFileChooser: finding the app icon ──────────────────────────────────

    private fun loadAppIcon(): Image? =
        SwingFileChooser::class.java
            .getDeclaredMethod("loadAppIcon")
            .apply { isAccessible = true }
            .invoke(SwingFileChooser) as Image?

    @Test
    fun `a packaged app takes its icon from the resources directory`() {
        val resources = File(dir, "app-resources")
        icon("icon-32.png", 32, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(32, loadAppIcon()?.getWidth(null))
    }

    @Test
    fun `the smallest icon is preferred when several are packaged`() {
        val resources = File(dir, "app-resources")
        icon("icon-32.png", 32, into = resources)
        icon("icon-48.png", 48, into = resources)
        icon("icon.png", 64, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(
            32,
            loadAppIcon()?.getWidth(null),
            "the dialog's title bar wants the small one; the others are fallbacks",
        )
    }

    @Test
    fun `the next size is used when the smallest is missing`() {
        val resources = File(dir, "app-resources")
        icon("icon-48.png", 48, into = resources)
        icon("icon.png", 64, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)

        assertEquals(48, loadAppIcon()?.getWidth(null))
    }

    @Test
    fun `a development run finds the icon in the source tree`() {
        // The walk climbs up to six levels from the working directory looking for the repo layout.
        val workingDir = File(dir, "some/nested/module").also { it.mkdirs() }
        icon("icon-32.png", 32, into = File(dir, "composeApp/src/jvmMain/appResources/common"))
        System.setProperty("user.dir", workingDir.absolutePath)

        assertEquals(32, loadAppIcon()?.getWidth(null), "running from the IDE must still get an icon")
    }

    @Test
    fun `no icon anywhere is not an error`() {
        assertNull(loadAppIcon(), "a missing icon means a default frame icon, not a broken dialog")
    }

    @Test
    fun `a resources directory that does not exist falls through to the source tree`() {
        System.setProperty("compose.application.resources.dir", File(dir, "not-created").absolutePath)
        icon("icon-32.png", 32, into = File(dir, "composeApp/src/jvmMain/appResources/common"))

        assertEquals(32, loadAppIcon()?.getWidth(null))
    }

    // ── SwingFileChooser: the hidden frame that owns the dialog ─────────────────
    //
    // A JFrame cannot be built headless, so these use a MockK instance — created without running
    // the constructor — with the properties under test wired to real backing fields. The
    // assertions are on the frame's resulting state, not on which setters were called.

    /** A JFrame that actually remembers what was set on it, usable with no display. */
    private fun recordingFrame(): JFrame {
        val frame = mockk<JFrame>(relaxed = true)
        var visible = false
        var undecorated = false
        every { frame.isVisible = any() } answers { visible = firstArg() }
        every { frame.isVisible } answers { visible }
        every { frame.isUndecorated = any() } answers { undecorated = firstArg() }
        every { frame.isUndecorated } answers { undecorated }
        return frame
    }

    @Test
    fun `the owner frame is invisible and unobtrusive`() {
        val frame = recordingFrame()

        SwingFileChooser.configureOwnerFrame(frame)

        assertTrue(frame.isUndecorated, "a decorated owner would flash a title bar behind the dialog")
        verify { frame.setSize(0, 0) }
    }

    @Test
    fun `the owner frame is given the app icon`() {
        val resources = File(dir, "app-resources")
        icon("icon-32.png", 32, into = resources)
        System.setProperty("compose.application.resources.dir", resources.absolutePath)
        val frame = recordingFrame()
        val icons = mutableListOf<Image?>()
        every { frame.iconImage = any() } answers { icons += firstArg<Image?>() }

        SwingFileChooser.configureOwnerFrame(frame)

        assertEquals(32, icons.single()?.getWidth(null), "the dialog shows the app icon, not a coffee cup")
    }

    @Test
    fun `a frame that refuses the icon is still set up`() {
        // The icon is cosmetic. If applying it fails, the alternative to swallowing that is no file
        // dialogs at all on the affected machine.
        val frame = recordingFrame()
        every { frame.iconImage = any() } throws RuntimeException("no icon for you")

        SwingFileChooser.configureOwnerFrame(frame)

        assertTrue(frame.isUndecorated, "the frame must still be a usable owner")
    }

    @Test
    fun `the owner frame is showing while the dialog is up and hidden again afterwards`() {
        // Leaving it visible strands a zero-sized undecorated window on the desktop; hiding it too
        // early leaves the dialog with no owner and no icon.
        val frame = recordingFrame()
        var visibleDuringDialog: Boolean? = null

        SwingFileChooser.showOwned(frame) {
            visibleDuringDialog = it.isVisible
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(true, visibleDuringDialog)
        assertFalse(frame.isVisible, "the owner frame must not outlive the dialog")
    }

    @Test
    fun `the dialog is shown owned by the frame it was given`() {
        val frame = recordingFrame()
        var owner: JFrame? = null

        SwingFileChooser.showOwned(frame) { owner = it; JFileChooser.CANCEL_OPTION }

        assertEquals(frame, owner, "an unowned dialog loses the app icon and the modality")
    }

    @Test
    fun `the return code from the dialog is passed straight back`() {
        val frame = recordingFrame()

        assertEquals(
            JFileChooser.APPROVE_OPTION,
            SwingFileChooser.showOwned(frame) { JFileChooser.APPROVE_OPTION },
        )
        assertEquals(
            JFileChooser.ERROR_OPTION,
            SwingFileChooser.showOwned(frame) { JFileChooser.ERROR_OPTION },
        )
    }

    // ── SwingFileChooser: what the dialog is told ───────────────────────────────

    /**
     * A chooser pointed at the test's temp folder. Building one needs no display — only showing it
     * does — so everything the dialog is configured with can be asserted here.
     */
    private fun chooser() = JFileChooser(dir)

    private fun configureOpen(
        chooser: JFileChooser,
        filters: List<FileNameExtensionFilter>,
        title: String = "",
        selectDirectory: Boolean = false,
        multiple: Boolean = false,
    ) = SwingFileChooser.configureOpen(chooser, filters, title, selectDirectory, multiple)

    private fun configureSave(
        chooser: JFileChooser,
        location: Path,
        suggestedName: String,
        filters: List<FileNameExtensionFilter>,
        title: String = "",
    ) = SwingFileChooser.configureSave(chooser, location, suggestedName, filters, title)

    private fun filter(description: String, vararg extensions: String) =
        FileNameExtensionFilter(description, *extensions)

    @Test
    fun `the open dialog is given its title and selection mode`() {
        val chooser = chooser()

        configureOpen(chooser, emptyList(), title = "Open Schedule", multiple = true)

        assertEquals("Open Schedule", chooser.dialogTitle)
        assertEquals(JFileChooser.FILES_ONLY, chooser.fileSelectionMode)
        assertTrue(chooser.isMultiSelectionEnabled)
    }

    @Test
    fun `picking a folder restricts the dialog to directories`() {
        val chooser = chooser()

        configureOpen(chooser, emptyList(), selectDirectory = true)

        assertEquals(
            JFileChooser.DIRECTORIES_ONLY,
            chooser.fileSelectionMode,
            "choosing a song library must not offer files",
        )
        assertFalse(chooser.isMultiSelectionEnabled, "a single-selection request must stay single")
    }

    @Test
    fun `offered filters replace the All Files entry`() {
        val songs = filter("Songs (*.sps)", "sps")
        val chooser = chooser()

        configureOpen(chooser, listOf(songs))

        assertFalse(
            chooser.isAcceptAllFileFilterUsed,
            "leaving All Files on lets an operator pick a file the app cannot open",
        )
        assertEquals(listOf(songs), chooser.choosableFileFilters.toList())
    }

    @Test
    fun `the first filter is the one the dialog opens showing`() {
        val powerPoint = filter("PowerPoint", "pptx", "ppt")
        val pdf = filter("PDF", "pdf")
        val chooser = chooser()

        configureOpen(chooser, listOf(powerPoint, pdf))

        assertEquals(powerPoint, chooser.fileFilter, "the preselected filter decides what is listed")
        assertEquals(listOf(powerPoint, pdf), chooser.choosableFileFilters.toList())
    }

    @Test
    fun `with no filters the dialog keeps All Files`() {
        val chooser = chooser()

        configureOpen(chooser, emptyList())

        assertTrue(
            chooser.isAcceptAllFileFilterUsed,
            "a chooser with nothing to filter by must not end up listing nothing",
        )
    }

    @Test
    fun `the save dialog opens on the suggested name inside the given folder`() {
        val chooser = chooser()

        configureSave(chooser, dir.toPath(), "schedule.cps", listOf(filter("Schedule", "cps")), "Save As")

        assertEquals("Save As", chooser.dialogTitle)
        assertEquals(File(dir, "schedule.cps"), chooser.selectedFile)
    }

    /**
     * Documents why [SwingFileChooser] sets the name BEFORE the filters: `JFileChooser.setFileFilter`
     * clears the selection when the selected file does not pass the new filter. Every caller passes a
     * name the filter accepts, so this only bites if that ever stops being true — at which point the
     * save dialog would open with an empty name box instead of a suggested one.
     */
    @Test
    fun `a suggested name the filter rejects is dropped`() {
        val chooser = chooser()

        configureSave(chooser, dir.toPath(), "schedule.txt", listOf(filter("Schedule", "cps")))

        assertNull(chooser.selectedFile, "Swing drops a selection the active filter does not accept")
    }

    @Test
    fun `the save dialog filters the same way the open dialog does`() {
        val schedule = filter("Schedule", "cps")
        val chooser = chooser()

        configureSave(chooser, dir.toPath(), "schedule.cps", listOf(schedule))

        assertFalse(chooser.isAcceptAllFileFilterUsed)
        assertEquals(schedule, chooser.fileFilter)
    }

    @Test
    fun `a save dialog with no filters keeps All Files`() {
        val chooser = chooser()

        configureSave(chooser, dir.toPath(), "notes", emptyList())

        assertTrue(chooser.isAcceptAllFileFilterUsed)
        assertEquals(File(dir, "notes"), chooser.selectedFile, "no filter can reject the name")
    }

    // ── SwingFileChooser: what comes back out of it ─────────────────────────────

    private fun openResult(returnCode: Int, chooser: JFileChooser, multiple: Boolean): List<Path>? =
        SwingFileChooser.openResult(returnCode, chooser, multiple)

    private fun saveResult(returnCode: Int, chooser: JFileChooser): Path? =
        SwingFileChooser.saveResult(returnCode, chooser)

    @Test
    fun `an approved single choice comes back as one path`() {
        val chooser = chooser().apply { selectedFile = File(dir, "song.sps") }

        assertEquals(
            listOf(File(dir, "song.sps").toPath()),
            openResult(JFileChooser.APPROVE_OPTION, chooser, multiple = false),
        )
    }

    @Test
    fun `an approved multiple choice comes back as every path`() {
        val chooser = chooser().apply {
            isMultiSelectionEnabled = true
            selectedFiles = arrayOf(File(dir, "a.sps"), File(dir, "b.sps"))
        }

        assertEquals(
            listOf(File(dir, "a.sps").toPath(), File(dir, "b.sps").toPath()),
            openResult(JFileChooser.APPROVE_OPTION, chooser, multiple = true),
        )
    }

    @Test
    fun `selecting nothing in a multiple choice comes back empty rather than null`() {
        // The base class treats null as "cancelled"; an approved dialog with no files is not that.
        val chooser = chooser().apply { isMultiSelectionEnabled = true }

        assertEquals(emptyList(), openResult(JFileChooser.APPROVE_OPTION, chooser, multiple = true))
    }

    @Test
    fun `a cancelled open dialog returns nothing`() {
        val chooser = chooser().apply { selectedFile = File(dir, "song.sps") }

        assertNull(
            openResult(JFileChooser.CANCEL_OPTION, chooser, multiple = false),
            "a stale selection must not be returned as though it were picked",
        )
        assertNull(openResult(JFileChooser.ERROR_OPTION, chooser, multiple = false))
    }

    @Test
    fun `an approved save returns the chosen path`() {
        val chooser = chooser().apply { selectedFile = File(dir, "sunday.cps") }

        assertEquals(File(dir, "sunday.cps").toPath(), saveResult(JFileChooser.APPROVE_OPTION, chooser))
    }

    @Test
    fun `a cancelled save returns nothing`() {
        val chooser = chooser().apply { selectedFile = File(dir, "sunday.cps") }

        assertNull(saveResult(JFileChooser.CANCEL_OPTION, chooser))
        assertNull(
            saveResult(JFileChooser.ERROR_OPTION, chooser),
            "a dialog that failed must not overwrite whatever the name box happened to hold",
        )
    }

    // ── SwingFileChooser: getting the answer off the dispatch thread ────────────

    private fun <T> onEventDispatchThread(block: () -> T): T =
        SwingFileChooser.onEventDispatchThread(block)

    @Test
    fun `the dialog is opened from the event dispatch thread`() {
        assertTrue(
            onEventDispatchThread { SwingUtilities.isEventDispatchThread() },
            "Swing requires a dialog be opened from the EDT, not from the caller's dispatcher",
        )
    }

    @Test
    fun `what the dispatch thread produced is handed back to the caller`() {
        assertEquals(listOf("song.sps"), onEventDispatchThread { listOf("song.sps") })
    }

    @Test
    fun `a null answer survives the trip back`() {
        // Cancelling produces null, and the value is carried out of the EDT through an unchecked
        // cast — one that cannot carry a null would turn every cancelled dialog into a crash.
        assertNull(onEventDispatchThread<List<Path>?> { null })
    }

    @Test
    fun `a failure inside the dialog is not swallowed`() {
        val boom = IllegalStateException("the dialog blew up")

        val thrown = assertFailsWith<InvocationTargetException> {
            onEventDispatchThread<Unit> { throw boom }
        }

        assertTrue(
            generateSequence(thrown as Throwable) { it.cause }.any { it === boom },
            "a chooser that fails must report it, not return as though the operator cancelled",
        )
    }

    // ── SwingFileChooser: running the dialog end to end ─────────────────────────
    //
    // openWith/saveWith are the real sequence — build a chooser, configure it, show it, read the
    // outcome — with only the showing passed in. These drive the whole path with a stand-in for
    // that one step, so the real configureOpen/configureSave and openResult/saveResult run.

    @Test
    fun `the open dialog is built on the folder it was given and set up before it is shown`() {
        var shownWith: JFileChooser? = null

        SwingFileChooser.openWith(
            path = dir.toPath(),
            filters = listOf(filter("Songs", "sps")),
            title = "Open Song",
            selectDirectory = false,
            multiple = false,
        ) { chooser ->
            // Whatever the operator sees must already be configured by the time it is on screen.
            shownWith = chooser
            JFileChooser.CANCEL_OPTION
        }

        val shown = assertNotNull(shownWith)
        assertEquals(dir.canonicalFile, shown.currentDirectory.canonicalFile)
        assertEquals("Open Song", shown.dialogTitle)
        assertFalse(shown.isAcceptAllFileFilterUsed)
    }

    @Test
    fun `an approved open returns what the dialog was left holding`() {
        val picked = SwingFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            title = "",
            selectDirectory = false,
            multiple = false,
        ) { chooser ->
            chooser.selectedFile = File(dir, "song.sps")
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(listOf(File(dir, "song.sps").toPath()), picked)
    }

    @Test
    fun `an approved multi-select open returns every file`() {
        val picked = SwingFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            title = "",
            selectDirectory = false,
            multiple = true,
        ) { chooser ->
            chooser.selectedFiles = arrayOf(File(dir, "a.sps"), File(dir, "b.sps"))
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(listOf(File(dir, "a.sps").toPath(), File(dir, "b.sps").toPath()), picked)
    }

    @Test
    fun `a cancelled open returns nothing even though a file was selected`() {
        val picked = SwingFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            title = "",
            selectDirectory = false,
            multiple = false,
        ) { chooser ->
            chooser.selectedFile = File(dir, "song.sps")
            JFileChooser.CANCEL_OPTION
        }

        assertNull(picked, "a selection left behind by a cancelled dialog must not be returned")
    }

    @Test
    fun `the save dialog is set up with the suggested name before it is shown`() {
        var shownWith: JFileChooser? = null

        SwingFileChooser.saveWith(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = listOf(filter("Schedule", "cps")),
            title = "Save As",
        ) { chooser ->
            shownWith = chooser
            JFileChooser.CANCEL_OPTION
        }

        val shown = assertNotNull(shownWith)
        assertEquals("Save As", shown.dialogTitle)
        assertEquals(File(dir, "schedule.cps"), shown.selectedFile)
    }

    @Test
    fun `an approved save returns the path the dialog was left holding`() {
        val saved = SwingFileChooser.saveWith(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = listOf(filter("Schedule", "cps")),
            title = "",
        ) { chooser ->
            chooser.selectedFile = File(dir, "sunday.cps")
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(File(dir, "sunday.cps").toPath(), saved)
    }

    @Test
    fun `a cancelled save returns nothing even though the name box was filled`() {
        val saved = SwingFileChooser.saveWith(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = emptyList(),
            title = "",
        ) { chooser ->
            chooser.selectedFile = File(dir, "sunday.cps")
            JFileChooser.CANCEL_OPTION
        }

        assertNull(saved)
    }

    // ── SwingFileChooser: the full open/save path on the dispatch thread ─────────
    //
    // runOpen/runSave are the whole override body with only the owner frame and the one display
    // call injected — so the real onEventDispatchThread, openWith, configureOpen, showOwned and
    // openResult all run, on the actual EDT, with a recording frame standing in for the window.

    @Test
    fun `the open path configures shows and reads on the dispatch thread`() {
        val frame = recordingFrame()
        var onEdt = false
        var shownChooser: JFileChooser? = null

        val picked = SwingFileChooser.runOpen(
            path = dir.toPath(),
            filters = listOf(filter("Songs", "sps")),
            title = "Open Song",
            selectDirectory = false,
            multiple = false,
            frame = frame,
        ) { chooser, ownerFrame ->
            onEdt = SwingUtilities.isEventDispatchThread()
            shownChooser = chooser
            assertEquals(frame, ownerFrame, "the dialog must be owned by the frame it was given")
            chooser.selectedFile = File(dir, "song.sps")
            JFileChooser.APPROVE_OPTION
        }

        assertTrue(onEdt, "Swing requires the dialog be shown on the EDT")
        assertEquals("Open Song", shownChooser?.dialogTitle, "the chooser was configured before it was shown")
        assertFalse(shownChooser!!.isAcceptAllFileFilterUsed, "filters were applied before showing")
        assertEquals(listOf(File(dir, "song.sps").toPath()), picked)
        assertFalse(frame.isVisible, "the owner frame is hidden again once the dialog closes")
    }

    @Test
    fun `a cancelled open path returns nothing`() {
        val frame = recordingFrame()

        val picked = SwingFileChooser.runOpen(
            path = dir.toPath(),
            filters = emptyList(),
            title = "",
            selectDirectory = false,
            multiple = false,
            frame = frame,
        ) { _, _ -> JFileChooser.CANCEL_OPTION }

        assertNull(picked)
    }

    @Test
    fun `the save path configures shows and reads on the dispatch thread`() {
        val frame = recordingFrame()
        var nameWhenShown: File? = null
        var titleWhenShown: String? = null

        val saved = SwingFileChooser.runSave(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = listOf(filter("Schedule", "cps")),
            title = "Save As",
            frame = frame,
        ) { chooser, _ ->
            // Read the configuration the operator would see, before standing in for their pick.
            nameWhenShown = chooser.selectedFile
            titleWhenShown = chooser.dialogTitle
            chooser.selectedFile = File(dir, "sunday.cps")
            JFileChooser.APPROVE_OPTION
        }

        assertEquals(File(dir, "schedule.cps"), nameWhenShown, "opened on the suggested name")
        assertEquals("Save As", titleWhenShown)
        assertEquals(File(dir, "sunday.cps").toPath(), saved)
    }

    @Test
    fun `a cancelled save path returns nothing`() {
        val frame = recordingFrame()

        val saved = SwingFileChooser.runSave(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = emptyList(),
            title = "",
            frame = frame,
        ) { _, _ -> JFileChooser.CANCEL_OPTION }

        assertNull(saved)
    }

    // ── FileKitFileChooser: what the native dialog is told ──────────────────────

    private fun allExtensions(filters: List<FileNameExtensionFilter>): List<String> =
        with(FileKitFileChooser) { filters.allExtensions() }

    private fun toFileKitType(filters: List<FileNameExtensionFilter>): FileKitType =
        with(FileKitFileChooser) { filters.toFileKitType() }

    @Test
    fun `filters are flattened into one extension list`() {
        // The native dialogs take a single combined filter rather than a list of them.
        val extensions = allExtensions(
            listOf(
                FileNameExtensionFilter("PowerPoint", "pptx", "ppt"),
                FileNameExtensionFilter("PDF", "pdf"),
            )
        )

        assertEquals(listOf("pptx", "ppt", "pdf"), extensions)
    }

    @Test
    fun `an extension offered by two filters is listed once`() {
        val extensions = allExtensions(
            listOf(
                FileNameExtensionFilter("Presentations", "pptx", "pdf"),
                FileNameExtensionFilter("Documents", "pdf", "docx"),
            )
        )

        assertEquals(listOf("pptx", "pdf", "docx"), extensions, "a repeated extension would be offered twice")
    }

    @Test
    fun `no filters means no extensions`() {
        assertEquals(emptyList<String>(), allExtensions(emptyList()))
    }

    @Test
    fun `the combined extensions become the type the picker filters by`() {
        val type = toFileKitType(listOf(filter("PowerPoint", "pptx", "ppt"), filter("PDF", "pdf")))

        assertEquals(setOf("pptx", "ppt", "pdf"), (type as FileKitType.File).extensions)
    }

    @Test
    fun `no filters means a picker that accepts anything`() {
        // FileKitType.File() with no extensions means "all files"; an empty extension set would
        // instead be a picker that can select nothing at all.
        assertNull((toFileKitType(emptyList()) as FileKitType.File).extensions)
    }

    // ── FileKitFileChooser: which picker a request maps to ──────────────────────

    @Test
    fun `a plain request opens the single-file picker`() {
        assertEquals(
            FileKitFileChooser.PickerMode.SINGLE_FILE,
            FileKitFileChooser.pickerMode(selectDirectory = false, multiple = false),
        )
    }

    @Test
    fun `a multiple request opens the multi-file picker`() {
        assertEquals(
            FileKitFileChooser.PickerMode.MULTIPLE_FILES,
            FileKitFileChooser.pickerMode(selectDirectory = false, multiple = true),
        )
    }

    @Test
    fun `a directory request opens the directory picker whatever multiple says`() {
        // No call site asks for several directories, and the directory picker has no multi mode —
        // so selectDirectory has to win, or the request would open a file picker instead.
        assertEquals(
            FileKitFileChooser.PickerMode.DIRECTORY,
            FileKitFileChooser.pickerMode(selectDirectory = true, multiple = false),
        )
        assertEquals(
            FileKitFileChooser.PickerMode.DIRECTORY,
            FileKitFileChooser.pickerMode(selectDirectory = true, multiple = true),
        )
    }

    // ── FileKitFileChooser: the name the save dialog opens with ─────────────────

    @Test
    fun `a suggested name loses the extension the picker will add back`() {
        // FileKit appends the default extension itself; leaving it on offers "schedule.cps.cps".
        assertEquals("schedule" to "cps", FileKitFileChooser.saveNameParts("schedule.cps", listOf("cps")))
    }

    @Test
    fun `stripping the extension ignores case`() {
        assertEquals("SUNDAY" to "cps", FileKitFileChooser.saveNameParts("SUNDAY.CPS", listOf("cps")))
    }

    @Test
    fun `a name carrying a different offered extension keeps that one as the default`() {
        val parts = FileKitFileChooser.saveNameParts("deck.pdf", listOf("pptx", "pdf"))

        assertEquals("deck" to "pdf", parts, "saving a PDF must not default the dialog back to pptx")
    }

    @Test
    fun `a name with no offered extension is left whole and gains the first`() {
        assertEquals("notes.txt" to "cps", FileKitFileChooser.saveNameParts("notes.txt", listOf("cps")))
    }

    @Test
    fun `with no extensions on offer the name is passed through as typed`() {
        assertEquals("whatever" to null, FileKitFileChooser.saveNameParts("whatever", emptyList()))
    }

    // ── FileKitFileChooser: what comes back out of it ───────────────────────────

    private fun toPathOrNull(file: File): Path? = with(FileKitFileChooser) { file.toPathOrNull() }

    @Test
    fun `a real selection converts to a path`() {
        val picked = File(dir, "schedule.cps")

        assertNotNull(toPathOrNull(picked))
    }

    @Test
    fun `a selection with no filesystem path is treated as no selection`() {
        // Windows hands back virtual shell items (the "This PC" node and friends) that have no real
        // path, and converting them throws. The guard must turn that into "nothing was picked"
        // rather than a crash that also latches the native dialogs off for the rest of the session.
        // A NUL byte is the portable way to make the same conversion fail.
        val notARealPath = File("bad\u0000name")

        assertNull(toPathOrNull(notARealPath))
    }

    /** The same unconvertible pick used above, reused by the selection tests below. */
    private val virtualShellItem get() = File("bad\u0000name")

    @Test
    fun `one picked file comes back as a one-item selection`() {
        assertEquals(
            listOf(File(dir, "song.sps").toPath()),
            FileKitFileChooser.singleSelection(File(dir, "song.sps")),
        )
    }

    @Test
    fun `picking nothing is not a selection`() {
        assertNull(FileKitFileChooser.singleSelection(null))
        assertNull(
            FileKitFileChooser.singleSelection(virtualShellItem),
            "an unconvertible pick must read as cancelled, not crash",
        )
    }

    @Test
    fun `several picked files come back in order`() {
        assertEquals(
            listOf(File(dir, "a.sps").toPath(), File(dir, "b.sps").toPath()),
            FileKitFileChooser.multipleSelection(listOf(File(dir, "a.sps"), File(dir, "b.sps"))),
        )
    }

    @Test
    fun `unconvertible files are dropped from a multiple selection`() {
        assertEquals(
            listOf(File(dir, "a.sps").toPath()),
            FileKitFileChooser.multipleSelection(listOf(File(dir, "a.sps"), virtualShellItem)),
            "one virtual shell item must not lose the real files picked alongside it",
        )
    }

    @Test
    fun `a multiple selection left with nothing reads as cancelled`() {
        // The base class reads null as "cancelled"; an empty list would instead be unwrapped as a
        // selection, so both of these have to collapse to null.
        assertNull(FileKitFileChooser.multipleSelection(emptyList()))
        assertNull(FileKitFileChooser.multipleSelection(listOf(virtualShellItem)))
        assertNull(FileKitFileChooser.multipleSelection(null))
    }

    // ── FileKitFileChooser: running the native dialog end to end ────────────────
    //
    // openWith/saveWith are the real sequence with only the native picker passed in, so the real
    // pickerMode, toFileKitType, saveNameParts and selection shaping all run.

    @Test
    fun `the picker is told the mode the type and the folder`() = runBlocking {
        var seenMode: FileKitFileChooser.PickerMode? = null
        var seenType: FileKitType? = null
        var seenDirectory: File? = null

        FileKitFileChooser.openWith(
            path = dir.toPath(),
            filters = listOf(filter("Songs", "sps")),
            selectDirectory = false,
            multiple = true,
        ) { mode, type, directory ->
            seenMode = mode
            seenType = type
            seenDirectory = directory.file
            null
        }

        assertEquals(FileKitFileChooser.PickerMode.MULTIPLE_FILES, seenMode)
        assertEquals(setOf("sps"), (seenType as FileKitType.File).extensions)
        assertEquals(dir, seenDirectory)
    }

    @Test
    fun `a single open takes the one file the picker returned`() = runBlocking {
        val picked = FileKitFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            selectDirectory = false,
            multiple = false,
        ) { _, _, _ -> listOf(File(dir, "song.sps")) }

        assertEquals(listOf(File(dir, "song.sps").toPath()), picked)
    }

    @Test
    fun `a multiple open keeps every file the picker returned`() = runBlocking {
        val picked = FileKitFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            selectDirectory = false,
            multiple = true,
        ) { _, _, _ -> listOf(File(dir, "a.sps"), File(dir, "b.sps")) }

        assertEquals(listOf(File(dir, "a.sps").toPath(), File(dir, "b.sps").toPath()), picked)
    }

    @Test
    fun `a directory open takes one folder even though the picker could hand back more`() = runBlocking {
        // The directory picker has no multi mode, so a stray extra result must not widen the answer.
        val picked = FileKitFileChooser.openWith(
            path = dir.toPath(),
            filters = emptyList(),
            selectDirectory = true,
            multiple = true,
        ) { _, _, _ -> listOf(File(dir, "Songs"), File(dir, "Media")) }

        assertEquals(listOf(File(dir, "Songs").toPath()), picked)
    }

    @Test
    fun `a cancelled native picker reads as cancelled`() = runBlocking {
        assertNull(
            FileKitFileChooser.openWith(dir.toPath(), emptyList(), false, false) { _, _, _ -> null }
        )
        assertNull(
            FileKitFileChooser.openWith(dir.toPath(), emptyList(), false, true) { _, _, _ -> emptyList() },
            "an approved picker with nothing in it is still nothing picked",
        )
    }

    @Test
    fun `the saver is told the split name and the extensions it may use`() = runBlocking {
        var seenName: String? = null
        var seenExtension: String? = null
        var seenAllowed: Set<String>? = null
        var seenDirectory: File? = null

        FileKitFileChooser.saveWith(
            location = dir.toPath(),
            suggestedName = "schedule.cps",
            filters = listOf(filter("Schedule", "cps"), filter("PDF", "pdf")),
        ) { baseName, extension, allowed, directory ->
            seenName = baseName
            seenExtension = extension
            seenAllowed = allowed
            seenDirectory = directory.file
            null
        }

        assertEquals("schedule", seenName, "FileKit appends the extension itself")
        assertEquals("cps", seenExtension)
        assertEquals(setOf("cps", "pdf"), seenAllowed)
        assertEquals(dir, seenDirectory)
    }

    @Test
    fun `with no filters the saver is given no restriction at all`() = runBlocking {
        var seenAllowed: Set<String>? = emptySet()

        FileKitFileChooser.saveWith(dir.toPath(), "notes", emptyList()) { _, _, allowed, _ ->
            seenAllowed = allowed
            null
        }

        assertNull(seenAllowed, "an empty set would be a saver that permits nothing")
    }

    @Test
    fun `a saved file comes back as a path`() = runBlocking {
        val saved = FileKitFileChooser.saveWith(dir.toPath(), "schedule.cps", emptyList()) { _, _, _, _ ->
            File(dir, "sunday.cps")
        }

        assertEquals(File(dir, "sunday.cps").toPath(), saved)
    }

    @Test
    fun `a cancelled saver returns nothing`() = runBlocking {
        assertNull(FileKitFileChooser.saveWith(dir.toPath(), "schedule.cps", emptyList()) { _, _, _, _ -> null })
    }

    // ── FileKitFileChooser: which window owns the dialog ────────────────────────

    @Test
    fun `every native dialog is given its title and an owner`() {
        val settings = FileKitFileChooser.dialogSettings("Open Schedule")

        assertEquals("Open Schedule", settings.title)
        assertNull(settings.parentWindow, "headless there is no window to own it")
    }

    @Test
    fun `with no windows open there is no parent to own the dialog`() {
        // Headless there is nothing to find — the same answer as a dialog opened before any window
        // is showing. FileKit must get null rather than a hidden helper window (JCEF, JavaFX).
        assertNull(FileKitFileChooser.resolveParentWindow())
    }

    @Test
    fun `the parent window is resolved from a background thread too`() {
        // Callers are suspending functions on the IO dispatcher, so this has to marshal to the EDT
        // itself rather than returning whatever an off-EDT query happens to see.
        assertFalse(SwingUtilities.isEventDispatchThread(), "the test must not already be on the EDT")

        assertNull(FileKitFileChooser.parentWindow())
    }

    @Test
    fun `the parent window is resolved directly when already on the dispatch thread`() {
        // invokeAndWait from the EDT is an error, so this path must not marshal.
        assertNull(SwingFileChooser.onEventDispatchThread { FileKitFileChooser.parentWindow() })
    }

    /** A stand-in AWT window with the properties the owner-selection reads. */
    private fun window(showing: Boolean, focused: Boolean, width: Int, height: Int): Window {
        val win = mockk<Window>(relaxed = true)
        every { win.isShowing } returns showing
        every { win.isFocused } returns focused
        every { win.width } returns width
        every { win.height } returns height
        return win
    }

    @Test
    fun `the toolkit's active window owns the dialog outright`() {
        val active = window(showing = true, focused = true, width = 800, height = 600)
        val bigger = window(showing = true, focused = false, width = 1920, height = 1080)

        assertEquals(
            active,
            FileKitFileChooser.chooseParentWindow(active, arrayOf(bigger, active)),
            "the window the toolkit reports as active is the one the operator is looking at",
        )
    }

    @Test
    fun `with no active window a showing focused window is chosen`() {
        val focused = window(showing = true, focused = true, width = 400, height = 300)
        val unfocused = window(showing = true, focused = false, width = 1920, height = 1080)

        assertEquals(focused, FileKitFileChooser.chooseParentWindow(null, arrayOf(unfocused, focused)))
    }

    @Test
    fun `failing that the largest still-visible window is chosen`() {
        // Nothing is focused, so the biggest real window wins — this is what keeps a small hidden
        // helper window (JCEF, JavaFX) from stealing the dialog and hiding it behind itself.
        val small = window(showing = true, focused = false, width = 200, height = 100)
        val big = window(showing = true, focused = false, width = 1920, height = 1080)
        val hiddenButHuge = window(showing = false, focused = false, width = 3000, height = 2000)

        assertEquals(big, FileKitFileChooser.chooseParentWindow(null, arrayOf(small, big, hiddenButHuge)))
    }

    @Test
    fun `a zero-sized window is never chosen as owner`() {
        // The Swing owner frame is a 0x0 helper; it must not end up owning a native dialog.
        val zeroSized = window(showing = true, focused = false, width = 0, height = 0)

        assertNull(FileKitFileChooser.chooseParentWindow(null, arrayOf(zeroSized)))
    }

    @Test
    fun `no windows at all means no owner`() {
        assertNull(
            FileKitFileChooser.chooseParentWindow(null, emptyArray()),
            "a dialog with no owner centres on screen — better than crashing",
        )
    }

    // ── FileKitFileChooser: falling back when native dialogs fail ────────────────
    //
    // withNativeDialog is why a machine whose native picker is broken (a missing library, a
    // locked-down OS) still gets a working file dialog: the first failure latches and every dialog
    // after it goes straight to Swing. nativeDialogsBroken is reset in setup/teardown so this
    // per-JVM latch cannot leak into another test.

    @Test
    fun `a working native dialog is used and the fallback never runs`() = runBlocking {
        var fellBack = false

        val result = FileKitFileChooser.withNativeDialog(
            context = "test",
            attempt = { "native" },
            fallback = { fellBack = true; "swing" },
        )

        assertEquals("native", result)
        assertFalse(fellBack, "the Swing fallback must not run when the native dialog works")
        assertFalse(FileKitFileChooser.nativeDialogsBroken, "a success must not latch the native path off")
    }

    @Test
    fun `a failing native dialog falls back to Swing and latches`() = runBlocking {
        var fellBack = false

        val result = FileKitFileChooser.withNativeDialog(
            context = "test",
            attempt = { throw RuntimeException("no native dialog library") },
            fallback = { fellBack = true; "swing" },
        )

        assertEquals("swing", result, "the operator still gets a dialog, just the Swing one")
        assertTrue(fellBack)
        assertTrue(FileKitFileChooser.nativeDialogsBroken, "a broken machine must not be asked to try again")
    }

    @Test
    fun `once latched the native dialog is not attempted again`() = runBlocking {
        FileKitFileChooser.nativeDialogsBroken = true
        var attempted = false

        val result = FileKitFileChooser.withNativeDialog(
            context = "test",
            attempt = { attempted = true; "native" },
            fallback = { "swing" },
        )

        assertEquals("swing", result)
        assertFalse(attempted, "re-attempting would fail and re-report the same fault on every single dialog")
    }

    @Test
    fun `a cancelled dialog propagates and does not latch the native path off`() {
        // Dismissing the dialog throws CancellationException — the operator's choice, not a fault.
        // It must propagate unchanged, and it must NOT trip the broken latch.
        var fellBack = false

        assertFailsWith<CancellationException> {
            runBlocking {
                FileKitFileChooser.withNativeDialog(
                    context = "test",
                    attempt = { throw CancellationException("operator dismissed the dialog") },
                    fallback = { fellBack = true; "swing" },
                )
            }
        }

        assertFalse(fellBack, "cancelling must not silently reopen as a Swing dialog")
        assertFalse(FileKitFileChooser.nativeDialogsBroken, "cancelling is not a fault; the native path stays on")
    }
}
