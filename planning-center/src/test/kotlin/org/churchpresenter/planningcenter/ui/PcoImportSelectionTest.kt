package org.churchpresenter.planningcenter.ui

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.planningcenter.PcoItemType
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.planningcenter.PlanningCenterClient.PlanAttachment
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What an import brings into the schedule from what the operator ticked, driven straight through
 * [canImportSelection] and [importSelection] on a selection set up by hand: matched songs, ticked
 * headers, the verses chosen of a detected scripture, an item's text as an announcement when it has
 * no file, and its ticked files as presentations, one picture slideshow and media.
 */
class PcoImportSelectionTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-selection-home").toFile()
        System.setProperty("user.home", home.absolutePath)
        mockkObject(PlanningCenterClient)
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any(), any()) } answers {
            PlanningCenterClient.AttachmentUrlOutcome.Success("https://files.test/${secondArg<String>()}")
        }
        coEvery { PlanningCenterClient.downloadFile(any(), any(), any()) } answers {
            val destination = secondArg<File>()
            destination.parentFile.mkdirs()
            destination.writeText("file")
            PlanningCenterClient.FileDownloadOutcome.Success(destination)
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PlanningCenterClient)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun item(id: String, type: String, title: String = "Item $id", description: String = "",
        songTitle: String? = null) =
        PlanningCenterClient.PlanItem(
            id = id, title = title, description = description, itemType = type, sequence = 0,
            songTitle = songTitle,
        )

    private fun row(pco: PlanningCenterClient.PlanItem, matchedSongId: String? = null, selected: Boolean = true) =
        PlanningCenterImportViewModel.ImportPlanItem(pco = pco, matchedSongId = matchedSongId, selected = selected)

    private fun verse(n: Int) = PlanningCenterScripture("Psalms", 19, 23, n, "Verse $n", "")

    private fun viewModel(
        rows: List<PlanningCenterImportViewModel.ImportPlanItem>,
        scriptures: Map<String, List<PlanningCenterScripture>> = emptyMap(),
        chosenVerses: Map<String, Set<Int>> = emptyMap(),
        files: Map<String, List<PlanAttachment>> = emptyMap(),
        ticked: Map<String, Set<String>> = files.mapValues { (_, list) -> list.map { it.id }.toSet() },
        slides: Int = 7,
    ): PlanningCenterImportViewModel {
        val selection = PlanSelection().apply {
            planItems = rows
            detectedScripturesByItemId = scriptures
            selectedScriptureIndices = chosenVerses
            attachmentsByItemId = files
            selectedAttachmentIds = ticked
        }
        return PlanningCenterImportViewModel(
            initialAccessToken = "token",
            initialRefreshToken = "refresh",
            initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
            initialServiceTypeId = "st",
            importSongbookName = "",
            onTokensRefreshed = { _, _, _ -> },
            services = PlanningCenterImportServices("id", "secret", countSlides = { slides }),
            selection = selection,
        )
    }

    /** Every call an import made, in order, as "kind:args". */
    private class Calls {
        val made = mutableListOf<String>()
        val actions = PcoImportActions(
            onAddSong = { n, t, b, id -> made += "song:$n|$t|$b|$id" },
            onAddLabel = { t, fg, bg -> made += "label:$t|$fg|$bg" },
            onAddPresentation = { _, name, count, type -> made += "presentation:$name|$count|$type" },
            onAddPicture = { _, name, count -> made += "picture:$name|$count" },
            onAddMedia = { _, title, type -> made += "media:$title|$type" },
            onAddAnnouncement = { made += "announcement:$it" },
            onAddBibleVerse = { book, ch, v, text, range, id -> made += "verse:$book|$ch|$v|$text|$range|$id" },
            headerTextColor = "#FFF",
            headerBackgroundColor = "#000",
        )
    }

    private fun import(vm: PlanningCenterImportViewModel): List<String> {
        val calls = Calls()
        runBlocking { importSelection(vm, "plan", calls.actions) }
        return calls.made
    }

    // ── canImportSelection ──────────────────────────────────────────────────────

    @Test
    fun `nothing can be imported from an empty plan or from rows that bring nothing`() {
        assertFalse(canImportSelection(viewModel(emptyList())))
        assertFalse(canImportSelection(viewModel(listOf(row(item("h", PcoItemType.HEADER), selected = false)))))
        assertFalse(canImportSelection(viewModel(listOf(row(item("s", PcoItemType.SONG))))), "an unmatched song")
        assertFalse(canImportSelection(viewModel(listOf(row(item("m", PcoItemType.MEDIA))))), "media rows")
    }

    @Test
    fun `a ticked header or a matched song can be imported`() {
        assertTrue(canImportSelection(viewModel(listOf(row(item("h", PcoItemType.HEADER))))))
        assertTrue(canImportSelection(viewModel(listOf(row(item("s", PcoItemType.SONG), matchedSongId = "b::1")))))
    }

    @Test
    fun `an item with scripture counts only while one of its verses is chosen`() {
        val rows = listOf(row(item("i", PcoItemType.ITEM)))
        val scriptures = mapOf("i" to listOf(verse(1)))
        assertTrue(canImportSelection(viewModel(rows, scriptures, mapOf("i" to setOf(0)))))
        assertFalse(canImportSelection(viewModel(rows, scriptures, mapOf("i" to emptySet()))))
        assertFalse(canImportSelection(viewModel(rows, scriptures)), "no verse chosen at all")
    }

    @Test
    fun `an unticked item still counts for a ticked file it can bring in, never for one it cannot`() {
        val rows = listOf(row(item("i", PcoItemType.ITEM), selected = false))
        val supported = mapOf("i" to listOf(PlanAttachment("a", "slides.pptx")))
        val unsupported = mapOf("i" to listOf(PlanAttachment("a", "notes.xyz")))
        assertTrue(canImportSelection(viewModel(rows, files = supported)))
        assertFalse(canImportSelection(viewModel(rows, files = unsupported)))
        assertFalse(canImportSelection(viewModel(rows, files = supported, ticked = emptyMap())))
        assertFalse(canImportSelection(viewModel(rows)), "nothing ticked and no file")
    }

    // ── importSelection ─────────────────────────────────────────────────────────

    @Test
    fun `a ticked matched song is added by its songbook and number, under its song title`() {
        val vm = viewModel(
            listOf(
                row(item("s1", PcoItemType.SONG, title = "Plan title", songTitle = "Amazing Grace"), "Hymnal::12"),
                row(item("s2", PcoItemType.SONG, title = "No number"), "Loose"),
                row(item("s3", PcoItemType.SONG), "Hymnal::3", selected = false),
                row(item("s4", PcoItemType.SONG)),
            ),
        )
        assertEquals(
            listOf("song:12|Amazing Grace|Hymnal|Hymnal::12", "song:0|No number|Loose|Loose"),
            import(vm),
        )
    }

    @Test
    fun `a ticked header becomes a label in the heading colours, an unticked one does not`() {
        val vm = viewModel(
            listOf(
                row(item("h1", PcoItemType.HEADER, "Worship")),
                row(item("h2", PcoItemType.HEADER), selected = false),
            ),
        )
        assertEquals(listOf("label:Worship|#FFF|#000"), import(vm))
    }

    @Test
    fun `only the chosen verses of a detected scripture are added`() {
        val vm = viewModel(
            listOf(row(item("i", PcoItemType.ITEM))),
            scriptures = mapOf("i" to listOf(verse(1), verse(2), verse(3))),
            chosenVerses = mapOf("i" to setOf(0, 2)),
        )
        assertEquals(listOf("verse:Psalms|23|1|Verse 1||19", "verse:Psalms|23|3|Verse 3||19"), import(vm))
    }

    @Test
    fun `a ticked item with no file becomes an announcement of its description, or its title`() {
        val vm = viewModel(
            listOf(
                row(item("i1", PcoItemType.ITEM, title = "Offering", description = "Give online")),
                row(item("i2", PcoItemType.ITEM, title = "Welcome")),
                row(item("i3", PcoItemType.ITEM, title = "Skipped"), selected = false),
                row(item("m", PcoItemType.MEDIA)),
            ),
        )
        assertEquals(listOf("announcement:Give online", "announcement:Welcome"), import(vm))
    }

    @Test
    fun `an item's ticked files come in as their kinds, the pictures as one slideshow, and no announcement`() {
        val files = listOf(
            PlanAttachment("p", "deck.pptx"),
            PlanAttachment("i1", "one.jpg"),
            PlanAttachment("i2", "two.png"),
            PlanAttachment("v", "clip.mp4"),
            PlanAttachment("x", "notes.xyz"),
            PlanAttachment("u", "unticked.mp3"),
        )
        val vm = viewModel(
            listOf(row(item("i", PcoItemType.ITEM, title = "Sermon"))),
            files = mapOf("i" to files),
            ticked = mapOf("i" to setOf("p", "i1", "i2", "v", "x")),
        )
        assertEquals(listOf("presentation:Sermon|7|pptx", "media:Sermon|local", "picture:Sermon|2"), import(vm))
    }

    @Test
    fun `an untitled item's files are named after the file and its folder`() {
        val files = listOf(
            PlanAttachment("p", "deck.pdf"),
            PlanAttachment("v", "song.wav"),
            PlanAttachment("i", "a.gif"),
        )
        val vm = viewModel(
            listOf(row(item("it", PcoItemType.ITEM, title = ""), selected = false)),
            files = mapOf("it" to files),
            slides = 0,
        )
        assertEquals(listOf("presentation:deck|0|pdf", "media:song|local", "picture:it|1"), import(vm))
    }

    @Test
    fun `a file that cannot be fetched is left out`() {
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any(), any()) } returns
            PlanningCenterClient.AttachmentUrlOutcome.Failure
        val vm = viewModel(
            listOf(row(item("i", PcoItemType.ITEM), selected = false)),
            files = mapOf("i" to listOf(PlanAttachment("p", "deck.pptx"))),
        )
        assertEquals(emptyList(), import(vm))
    }

    // ── newSongPrefill ──────────────────────────────────────────────────────────

    @Test
    fun `a new song opens with the plan's details, filed under the default songbook`() {
        val pco = PlanningCenterClient.PlanItem(
            id = "s", title = "Plan title", itemType = PcoItemType.SONG, sequence = 0,
            description = "Line one\nLine two", songAuthor = "Newton", songCcliNumber = "22025",
        )
        val song = runBlocking { newSongPrefill(viewModel(emptyList()), pco) }
        assertEquals("Plan title", song.title)
        assertEquals("Planning Center", song.songbook)
        assertEquals("Newton", song.author)
        assertEquals("22025", song.ccliNumber)
        assertEquals(listOf("Line one", "Line two"), song.lyrics)
    }

    @Test
    fun `a new song with nothing to go on opens blank`() {
        val pco = PlanningCenterClient.PlanItem(id = "s", title = "Bare", itemType = PcoItemType.SONG, sequence = 0)
        val song = runBlocking { newSongPrefill(viewModel(emptyList()), pco) }
        assertEquals("", song.author)
        assertEquals("", song.ccliNumber)
        assertEquals(listOf(""), song.lyrics)
    }

    @Test
    fun `a scripture shows its range when it has one, else its verse`() {
        assertEquals("Psalms 23:1", verse(1).displayReference)
        assertEquals("Psalms 23:1-6", verse(1).copy(verseRange = "1-6").displayReference)
    }
}
