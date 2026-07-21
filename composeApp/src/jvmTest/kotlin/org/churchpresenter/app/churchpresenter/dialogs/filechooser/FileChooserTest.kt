package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The logic every file dialog shares, regardless of which platform implementation runs underneath.
 *
 * [FileChooser] is a template: each platform supplies `chooseImpl`/`saveImpl`, and the base class
 * decides what the caller actually gets back. Two of those decisions matter to the operator:
 *
 *  - **Saving always produces a file with the right extension.** A schedule typed as "sunday"
 *    must come back as "sunday.cps", or it saves fine and then cannot be found by the open dialog
 *    ever again. Some platforms append the extension themselves and some don't, so the base class
 *    has to normalise it.
 *  - **A dialog never opens on a folder that isn't there.** Pointing a chooser at a path from a
 *    settings file that has since been deleted (an unplugged drive, a moved library) must fall back
 *    to the home directory rather than failing.
 *
 * The real implementations need a display or a D-Bus portal, so a recording stand-in supplies the
 * `*Impl` answers here and captures what the base class asked for.
 */
class FileChooserTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-filechooser-test").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun restoreHome() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** Stands in for a platform dialog: answers with canned results and records the request. */
    private class RecordingChooser(
        private val chooseResult: List<Path>? = null,
        private val saveResult: Path? = null,
    ) : FileChooser() {
        var chooseCall: ChooseCallHolder? = null
        var saveCall: SaveCallHolder? = null

        class ChooseCallHolder(
            val path: Path,
            val filters: List<FileNameExtensionFilter>,
            val title: String,
            val selectDirectory: Boolean,
            val multiple: Boolean,
        )

        class SaveCallHolder(
            val location: Path,
            val suggestedName: String,
            val filters: List<FileNameExtensionFilter>,
            val title: String,
        )

        override suspend fun chooseImpl(
            path: Path,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<Path>? {
            chooseCall = ChooseCallHolder(path, filters, title, selectDirectory, multiple)
            return chooseResult
        }

        override suspend fun saveImpl(
            location: Path,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): Path? {
            saveCall = SaveCallHolder(location, suggestedName, filters, title)
            return saveResult
        }
    }

    private fun filter(description: String, vararg extensions: String) =
        FileNameExtensionFilter(description, *extensions)

    private val scheduleFilter get() = filter("Church Presenter Schedule (*.cps)", "cps")

    /** A chooser whose save dialog returns [name] inside the home directory. */
    private fun savingAs(name: String) = RecordingChooser(saveResult = File(home, name).toPath())

    // ── Saving: the extension is guaranteed ─────────────────────────────────────

    @Test
    fun `a name typed without an extension gets the filter's one`() = runBlocking {
        val chosen = savingAs("sunday").save(null, "schedule.cps", listOf(scheduleFilter), "")

        assertEquals(
            "sunday.cps",
            chosen?.fileName.toString(),
            "a file saved without its extension cannot be found by the open dialog afterwards",
        )
    }

    @Test
    fun `a name that already has the extension is left alone`() = runBlocking {
        val chosen = savingAs("sunday.cps").save(null, "schedule.cps", listOf(scheduleFilter), "")

        assertEquals("sunday.cps", chosen?.fileName.toString(), "the platform had already appended it")
    }

    @Test
    fun `the extension check ignores case`() = runBlocking {
        val chosen = savingAs("SUNDAY.CPS").save(null, "schedule.cps", listOf(scheduleFilter), "")

        assertEquals(
            "SUNDAY.CPS",
            chosen?.fileName.toString(),
            "appending again would produce SUNDAY.CPS.cps",
        )
    }

    @Test
    fun `a name carrying some other extension keeps it and gains the right one`() = runBlocking {
        // Documents CURRENT behaviour: the extension is appended, not replaced — "notes.txt"
        // becomes "notes.txt.cps". Replacing would risk eating part of a name with a dot in it.
        val chosen = savingAs("notes.txt").save(null, "schedule.cps", listOf(scheduleFilter), "")

        assertEquals("notes.txt.cps", chosen?.fileName.toString())
    }

    @Test
    fun `any of the offered extensions counts as already correct`() = runBlocking {
        val images = filter("Images", "png", "jpg", "jpeg")

        val chosen = savingAs("logo.jpg").save(null, "logo.png", listOf(images), "")

        assertEquals("logo.jpg", chosen?.fileName.toString(), "the user picked a format the filter allows")
    }

    @Test
    fun `a second filter's extension counts too`() = runBlocking {
        val chooser = savingAs("deck.pdf")

        val chosen = chooser.save(null, "deck.pptx", listOf(filter("PowerPoint", "pptx"), filter("PDF", "pdf")), "")

        assertEquals("deck.pdf", chosen?.fileName.toString())
    }

    @Test
    fun `the first filter's extension is the one added`() = runBlocking {
        val chosen = savingAs("deck").save(null, "deck", listOf(filter("PowerPoint", "pptx"), filter("PDF", "pdf")), "")

        assertEquals("deck.pptx", chosen?.fileName.toString(), "the first filter is the one the dialog preselects")
    }

    @Test
    fun `with no filters the name is taken as typed`() = runBlocking {
        val chosen = savingAs("whatever").save(null, "whatever", emptyList(), "")

        assertEquals("whatever", chosen?.fileName.toString())
    }

    @Test
    fun `the corrected name stays in the folder the user chose`() = runBlocking {
        val elsewhere = File(home, "Documents").also { it.mkdirs() }
        val chooser = RecordingChooser(saveResult = File(elsewhere, "sunday").toPath())

        val chosen = chooser.save(null, "schedule.cps", listOf(scheduleFilter), "")

        assertEquals(elsewhere.toPath(), chosen?.parent, "fixing the extension must not move the file")
        assertEquals("sunday.cps", chosen?.fileName.toString())
    }

    @Test
    fun `cancelling the save dialog returns nothing`() = runBlocking {
        assertNull(RecordingChooser(saveResult = null).save(null, "schedule.cps", listOf(scheduleFilter), ""))
    }

    @Test
    fun `saving starts in the home directory when no location is given`() = runBlocking {
        val chooser = savingAs("sunday.cps")

        chooser.save(null, "schedule.cps", listOf(scheduleFilter), "Save Schedule As")

        assertEquals(Path(home.absolutePath), chooser.saveCall?.location)
        assertEquals("schedule.cps", chooser.saveCall?.suggestedName)
        assertEquals("Save Schedule As", chooser.saveCall?.title)
    }

    @Test
    fun `a given location is passed straight through`() = runBlocking {
        val documents = File(home, "Documents").also { it.mkdirs() }
        val chooser = savingAs("sunday.cps")

        chooser.save(documents.toPath(), "schedule.cps", listOf(scheduleFilter), "")

        assertEquals(documents.toPath(), chooser.saveCall?.location)
    }

    // ── Opening: where the dialog starts ────────────────────────────────────────

    @Test
    fun `a folder that exists is where the dialog opens`() = runBlocking {
        val library = File(home, "Songs").also { it.mkdirs() }
        val chooser = RecordingChooser(chooseResult = listOf(File(library, "a.sps").toPath()))

        chooser.chooseSingle(library.toPath(), emptyList(), "", selectDirectory = false)

        assertEquals(library.toPath(), chooser.chooseCall?.path)
    }

    @Test
    fun `a folder that has gone away falls back to home`() = runBlocking {
        val unplugged = File(home, "usb-stick").toPath() // never created
        val chooser = RecordingChooser(chooseResult = null)

        chooser.chooseMultiple(unplugged, emptyList(), "", selectDirectory = false)

        assertEquals(
            Path(home.absolutePath),
            chooser.chooseCall?.path,
            "a library folder from settings may not exist any more; the dialog must still open",
        )
    }

    @Test
    fun `no folder at all means home`() = runBlocking {
        val chooser = RecordingChooser(chooseResult = null)

        chooser.chooseMultiple(null, emptyList(), "", selectDirectory = false)

        assertEquals(Path(home.absolutePath), chooser.chooseCall?.path)
    }

    // ── Opening: what comes back ────────────────────────────────────────────────

    @Test
    fun `choosing one file returns it`() = runBlocking {
        val picked = File(home, "song.sps").toPath()
        val chooser = RecordingChooser(chooseResult = listOf(picked))

        assertEquals(picked, chooser.chooseSingle(null, emptyList(), "", selectDirectory = false))
    }

    @Test
    fun `choosing several files returns them all`() = runBlocking {
        val picked = listOf(File(home, "a.sps").toPath(), File(home, "b.sps").toPath())
        val chooser = RecordingChooser(chooseResult = picked)

        assertEquals(picked, chooser.chooseMultiple(null, emptyList(), "", selectDirectory = false))
    }

    @Test
    fun `cancelling returns nothing from either form`() = runBlocking {
        assertNull(RecordingChooser(chooseResult = null).chooseSingle(null, emptyList(), "", false))
        assertNull(RecordingChooser(chooseResult = null).chooseMultiple(null, emptyList(), "", false))
    }

    @Test
    fun `the request is handed down unchanged`() = runBlocking {
        val filters = listOf(scheduleFilter)
        val chooser = RecordingChooser(chooseResult = null)

        chooser.chooseMultiple(null, filters, "Open Schedule", selectDirectory = true)

        val call = chooser.chooseCall
        assertEquals(filters, call?.filters)
        assertEquals("Open Schedule", call?.title)
        assertEquals(true, call?.selectDirectory)
        assertEquals(true, call?.multiple, "chooseMultiple must ask for multi-selection")
    }

    @Test
    fun `choosing a single file asks for single selection`() = runBlocking {
        val chooser = RecordingChooser(chooseResult = listOf(File(home, "a.sps").toPath()))

        chooser.chooseSingle(null, emptyList(), "", selectDirectory = false)

        assertEquals(false, chooser.chooseCall?.multiple)
    }

    /**
     * Documents a CONTRACT on the platform implementations rather than desired behaviour for
     * callers: `chooseSingle` unwraps with `single()`, so an implementation that hands back two
     * paths for a single-selection dialog throws rather than silently taking the first. Worth
     * knowing when writing a new platform chooser — the failure is an exception out of the dialog,
     * not a wrong file.
     */
    @Test
    fun `an implementation returning more than one file for a single choice throws`() {
        val chooser = RecordingChooser(
            chooseResult = listOf(File(home, "a.sps").toPath(), File(home, "b.sps").toPath()),
        )

        assertFailsWith<IllegalArgumentException> {
            runBlocking { chooser.chooseSingle(null, emptyList(), "", selectDirectory = false) }
        }
    }

    @Test
    fun `an implementation returning no files for a single choice throws`() {
        // An implementation must return null to mean "cancelled" — not an empty list.
        val chooser = RecordingChooser(chooseResult = emptyList())

        assertFailsWith<NoSuchElementException> {
            runBlocking { chooser.chooseSingle(null, emptyList(), "", selectDirectory = false) }
        }
    }
}

/**
 * The case-insensitive glob the Linux (XDG portal) chooser builds for each filter extension.
 *
 * The portal matches patterns literally, so `*.sps` would hide a file saved as `SONG.SPS` — the
 * chooser therefore rewrites each letter as a two-case class, `*.[sS][pP][sS]`. Nobody developing
 * on macOS or Windows exercises this path, and the symptom on Linux is a file dialog that simply
 * does not list a file the user can see in their file manager.
 *
 * The builder is a private top-level function, so it is reached by reflection on its file class —
 * the same approach [org.churchpresenter.app.churchpresenter.utils.CrashReporterTest] uses.
 */
class XdgFilterPatternTest {

    private fun anyCase(extension: String): String {
        val method = Class.forName("org.churchpresenter.app.churchpresenter.dialogs.filechooser.XdgFileChooserKt")
            .getDeclaredMethod("asAnyCaseRegex", String::class.java)
            .apply { isAccessible = true }
        return method.invoke(null, extension) as String
    }

    @Test
    fun `each letter is expanded to match either case`() {
        assertEquals("[sS][pP][sS]", anyCase("sps"))
    }

    @Test
    fun `an extension typed in capitals expands the same way`() {
        assertEquals(anyCase("sps"), anyCase("SPS"), "the filter's own casing must not change what matches")
    }

    @Test
    fun `digits and punctuation are left as they are`() {
        assertEquals("[mM][pP]4", anyCase("mp4"))
        assertEquals("[tT][aA][rR].[gG][zZ]", anyCase("tar.gz"))
    }

    @Test
    fun `an empty extension produces an empty pattern`() {
        assertEquals("", anyCase(""))
    }
}
