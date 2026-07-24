package org.churchpresenter.app.churchpresenter.dialogs.filechooser

import kotlinx.coroutines.runBlocking
import org.freedesktop.dbus.types.UInt32
import org.freedesktop.dbus.types.Variant
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
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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

    // ── Which implementation the platform gets ──────────────────────────────────
    //
    // Picking wrong is not a graceful failure: the XDG chooser on a machine with no session bus
    // cannot open a dialog at all, so every Open and Save in the app stops working.

    @Test
    fun `Linux talks to the XDG desktop portal`() {
        assertEquals(XdgFileChooser, FileChooser.platformFor("Linux"))
    }

    @Test
    fun `a name carrying unix is recognised too`() {
        assertEquals(XdgFileChooser, FileChooser.platformFor("Some Unix"), "matched by \"nix\" in \"unix\"")
    }

    /**
     * Documents a GAP rather than desired behaviour: the check is a substring match on `nix`/`nux`,
     * so the unix-likes whose `os.name` carries neither — Solaris, AIX, the BSDs — are handed
     * FileKit even though they run the same XDG desktop portals as Linux. Nobody has reported it,
     * and FileKit falls back to the Swing dialog rather than failing outright, so this records the
     * behaviour instead of asserting it is right.
     */
    @Test
    fun `unix-likes without nix or nux in the name do not get the portal`() {
        assertEquals(FileKitFileChooser, FileChooser.platformFor("SunOS"))
        assertEquals(FileKitFileChooser, FileChooser.platformFor("FreeBSD"))
        assertEquals(FileKitFileChooser, FileChooser.platformFor("AIX"))
    }

    @Test
    fun `macOS and Windows get native dialogs through FileKit`() {
        assertEquals(FileKitFileChooser, FileChooser.platformFor("Mac OS X"))
        assertEquals(FileKitFileChooser, FileChooser.platformFor("Windows 11"))
    }

    @Test
    fun `the os name is matched regardless of case`() {
        assertEquals(FileChooser.platformFor("linux"), FileChooser.platformFor("LINUX"))
        assertEquals(XdgFileChooser, FileChooser.platformFor("LINUX"))
    }

    @Test
    fun `an unrecognised platform falls to FileKit rather than failing`() {
        assertEquals(
            FileKitFileChooser,
            FileChooser.platformFor("Some Future OS"),
            "an unknown desktop is likelier to have a native dialog than a D-Bus portal",
        )
    }

    @Test
    fun `the instance the app uses follows the same rule`() {
        assertEquals(
            FileChooser.platformFor(System.getProperty("os.name")),
            FileChooser.platformInstance,
        )
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

/**
 * What the Linux (XDG portal) chooser puts on the bus, and what it makes of the answer.
 *
 * The dialog itself is a D-Bus round trip to the desktop portal, so it cannot run here — but
 * everything either side of that call is ordinary logic, and all of it is invisible to anyone
 * developing on macOS or Windows. The failure modes are quiet ones: an option key the portal
 * ignores, a request path no signal is ever emitted on (the dialog then hangs rather than
 * failing), or a response code read as success. The helpers are `internal`, so they are called
 * directly.
 */
class XdgPortalRequestTest {

    private fun filter(description: String, vararg extensions: String) =
        FileNameExtensionFilter(description, *extensions)

    // ── The filters the portal is given ─────────────────────────────────────────

    private fun toDBusFilters(filters: List<FileNameExtensionFilter>) =
        XdgFileChooser.toDBusFilters(filters)

    @Test
    fun `a filter becomes a named struct of glob patterns`() {
        val structs = toDBusFilters(listOf(filter("Songs (*.sps)", "sps")))

        assertEquals(1, structs.size)
        val struct = structs.single()
        assertEquals("Songs (*.sps)", struct.name, "this is the label shown in the portal's filter dropdown")

        val pattern = struct.patterns.single()
        assertEquals(0, pattern.type.toInt(), "type 0 means a glob; 1 would mean a MIME type")
        assertEquals(
            "*.[sS][pP][sS]",
            pattern.pattern,
            "the portal matches literally, so a file saved as SONG.SPS would otherwise be hidden",
        )
    }

    @Test
    fun `every extension of a filter gets its own pattern`() {
        val structs = toDBusFilters(listOf(filter("PowerPoint", "pptx", "ppt")))

        assertEquals(
            listOf("*.[pP][pP][tT][xX]", "*.[pP][pP][tT]"),
            structs.single().patterns.map { it.pattern },
        )
    }

    @Test
    fun `each filter stays its own entry in the dropdown`() {
        val structs = toDBusFilters(listOf(filter("PowerPoint", "pptx"), filter("PDF", "pdf")))

        assertEquals(listOf("PowerPoint", "PDF"), structs.map { it.name })
    }

    @Test
    fun `no filters means no filter structs`() {
        assertEquals(0, toDBusFilters(emptyList()).size)
    }

    // ── The options the portal is given ─────────────────────────────────────────

    private fun buildOptions(
        path: Path,
        filters: List<FileNameExtensionFilter> = emptyList(),
        suggestedName: String? = null,
        selectDirectory: Boolean = false,
        multiple: Boolean = false,
        token: String = "deadbeef",
    ): Map<String, Variant<*>> =
        XdgFileChooser.buildOptions(path, filters, suggestedName, selectDirectory, multiple, token)

    @Test
    fun `an open dialog is told where to start and what it may select`() {
        val options = buildOptions(Path("/home/leader/Songs"), selectDirectory = true, multiple = true)

        assertEquals(true, options["multiple"]?.value)
        assertEquals(true, options["directory"]?.value)
        assertEquals("/home/leader/Songs", options["current_folder"]?.value)
        assertEquals("deadbeef", options["handle_token"]?.value)
    }

    @Test
    fun `an open dialog suggests no name at all`() {
        val options = buildOptions(Path("/home/leader"))

        assertFalse(
            options.containsKey("current_name"),
            "sending the key at all would have the portal pre-fill the name box",
        )
    }

    @Test
    fun `a save dialog carries the suggested name`() {
        val options = buildOptions(Path("/home/leader"), suggestedName = "schedule.cps")

        assertEquals("schedule.cps", options["current_name"]?.value)
    }

    @Test
    fun `the filters travel with the options`() {
        val options = buildOptions(Path("/home/leader"), filters = listOf(filter("Schedule", "cps")))

        val structs = options["filters"]?.value as Array<*>
        assertEquals("Schedule", (structs.single() as XdgFileChooser.DBusFilter).name)
    }

    // ── The path the portal will answer on ──────────────────────────────────────

    private fun requestPath(uniqueName: String, token: String) =
        XdgFileChooser.requestPath(uniqueName, token)

    @Test
    fun `the request path is built from the connection's unique name`() {
        // ":1.42" -> "1_42": the leading colon is dropped and dots become underscores. A wrong path
        // registers the handler where no signal arrives, so the dialog hangs instead of failing.
        assertEquals(
            "/org/freedesktop/portal/desktop/request/1_42/deadbeef",
            requestPath(":1.42", "deadbeef"),
        )
    }

    @Test
    fun `every dot in the unique name is replaced`() {
        assertEquals("/org/freedesktop/portal/desktop/request/1_2_3/tok", requestPath(":1.2.3", "tok"))
    }

    // ── The rule that catches the answer ────────────────────────────────────────

    private fun responseMatchRule(requestPath: String) =
        XdgFileChooser.responseMatchRule(requestPath)

    @Test
    fun `the match rule listens for the portal's Response signal on the request path`() {
        // Every field has to match what the portal emits. Anything wrong here means the handler
        // waits for a signal that never arrives and the dialog hangs instead of failing.
        val rule = responseMatchRule("/org/freedesktop/portal/desktop/request/1_42/deadbeef")

        assertEquals("signal", rule.messageType)
        assertEquals("org.freedesktop.portal.Request", rule.getInterface())
        assertEquals("Response", rule.member)
        assertEquals("/org/freedesktop/portal/desktop/request/1_42/deadbeef", rule.path)
    }

    @Test
    fun `each request gets a rule for its own path`() {
        assertNotEquals(
            responseMatchRule(requestPath(":1.42", "one")),
            responseMatchRule(requestPath(":1.42", "two")),
            "two dialogs open at once must not answer each other's signals",
        )
    }

    // ── What the portal answers ─────────────────────────────────────────────────

    private fun parseResponse(response: Int, results: Map<String, Variant<*>>) =
        XdgFileChooser.parseResponse(arrayOf(UInt32(response.toLong()), results))

    private fun uris(vararg values: String) = mapOf("uris" to Variant(values.toList(), "as"))

    @Test
    fun `a successful response yields the selected uris`() {
        assertEquals(
            listOf("file:///home/leader/a.sps", "file:///home/leader/b.sps"),
            parseResponse(0, uris("file:///home/leader/a.sps", "file:///home/leader/b.sps")),
        )
    }

    @Test
    fun `a cancelled response yields nothing`() {
        // 1 is the portal's code for "cancelled by the user", 2 for "ended some other way".
        assertNull(parseResponse(1, uris("file:///home/leader/a.sps")))
        assertNull(parseResponse(2, emptyMap()))
    }

    @Test
    fun `a success carrying no uris is treated as no selection`() {
        assertNull(parseResponse(0, emptyMap()), "a response without uris must not crash the dialog")
    }

    // ── The whole request, end to end ───────────────────────────────────────────
    //
    // requestPaths is the real sequence with only the bus round-trip passed in, so the real
    // buildOptions, requestPath and toPaths all run.

    @Test
    fun `the request carries the options and listens on the matching path`() = runBlocking {
        var seenOptions: Map<String, Variant<*>>? = null
        var seenRequestPath: String? = null

        XdgFileChooser.requestPaths(
            path = Path("/home/leader/Songs"),
            filters = listOf(filter("Songs", "sps")),
            suggestedName = null,
            selectDirectory = false,
            multiple = true,
            uniqueName = ":1.42",
            token = "deadbeef",
        ) { options, requestPath ->
            seenOptions = options
            seenRequestPath = requestPath
            null
        }

        assertEquals("/home/leader/Songs", seenOptions?.get("current_folder")?.value)
        assertEquals(true, seenOptions?.get("multiple")?.value)
        assertEquals("deadbeef", seenOptions?.get("handle_token")?.value)
        assertEquals(
            "/org/freedesktop/portal/desktop/request/1_42/deadbeef",
            seenRequestPath,
            "the token in the options and the token in the path must be the same one",
        )
    }

    @Test
    fun `the uris the portal answered with come back as paths`() = runBlocking {
        val picked = XdgFileChooser.requestPaths(
            path = Path("/home/leader"),
            filters = emptyList(),
            suggestedName = null,
            selectDirectory = false,
            multiple = true,
            uniqueName = ":1.42",
            token = "tok",
        ) { _, _ -> listOf("file:///home/leader/a.sps", "file:///home/leader/b.sps") }

        assertEquals(listOf(Path("/home/leader/a.sps"), Path("/home/leader/b.sps")), picked)
    }

    @Test
    fun `a cancelled portal request comes back as nothing`() = runBlocking {
        val picked = XdgFileChooser.requestPaths(
            path = Path("/home/leader"),
            filters = emptyList(),
            suggestedName = null,
            selectDirectory = false,
            multiple = false,
            uniqueName = ":1.42",
            token = "tok",
        ) { _, _ -> null }

        assertNull(picked)
    }

    @Test
    fun `a save request tells the portal the suggested name`() = runBlocking {
        var seenOptions: Map<String, Variant<*>>? = null

        XdgFileChooser.requestPaths(
            path = Path("/home/leader"),
            filters = emptyList(),
            suggestedName = "schedule.cps",
            selectDirectory = false,
            multiple = false,
            uniqueName = ":1.42",
            token = "tok",
        ) { options, _ -> seenOptions = options; null }

        assertEquals("schedule.cps", seenOptions?.get("current_name")?.value)
    }

    // ── What a save makes of the answer ─────────────────────────────────────────

    @Test
    fun `a save takes the one path the portal named`() {
        assertEquals(Path("/home/leader/sunday.cps"), XdgFileChooser.saveSelection(listOf(Path("/home/leader/sunday.cps"))))
    }

    @Test
    fun `a save that somehow named several files saves none of them`() {
        // A save dialog can only name one file, so more than one is a result that cannot be
        // honoured — better no save than silently writing to whichever came first.
        assertNull(XdgFileChooser.saveSelection(listOf(Path("/a.cps"), Path("/b.cps"))))
        assertNull(XdgFileChooser.saveSelection(emptyList()))
        assertNull(XdgFileChooser.saveSelection(null))
    }

    // ── Turning the answer into paths ───────────────────────────────────────────

    private fun toPaths(uris: List<String>?) = XdgFileChooser.toPaths(uris)

    @Test
    fun `file uris become paths`() {
        assertEquals(
            listOf(Path("/home/leader/a.sps"), Path("/home/leader/b.sps")),
            toPaths(listOf("file:///home/leader/a.sps", "file:///home/leader/b.sps")),
        )
    }

    @Test
    fun `an escaped uri decodes back to the real name`() {
        assertEquals(
            listOf(Path("/home/leader/Sunday morning.cps")),
            toPaths(listOf("file:///home/leader/Sunday%20morning.cps")),
            "a space in a filename must not survive as %20",
        )
    }

    @Test
    fun `nothing selected stays nothing`() {
        assertNull(toPaths(null), "null means cancelled and must not become an empty selection")
        assertEquals(emptyList(), toPaths(emptyList()))
    }
}
