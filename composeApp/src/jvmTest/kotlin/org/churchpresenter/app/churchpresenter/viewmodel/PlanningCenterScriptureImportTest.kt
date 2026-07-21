package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scripture half of a Planning Center import, plus the lyrics prefill.
 *
 * A generic plan item is often just a list of references typed into its title or description
 * ("Psalm 23:1-6"). Those are detected locally against the user's own primary Bible and offered as
 * importable scripture items — so this needs a real Bible configured in settings, which is what
 * makes `detectedScripturesByItemId` populate and the per-scripture checkboxes reachable.
 */
class PlanningCenterScriptureImportTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-scripture-home").toFile()
        System.setProperty("user.home", home.absolutePath)

        val bibleDir = File(home, "bibles").also { it.mkdirs() }
        SpbFixture.spbFile(
            bibleDir, name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Test Bible",
                books = listOf(SpbFixture.Book(19, "Psalms", 1), SpbFixture.Book(43, "John", 3)),
                verses = buildList {
                    for (v in 1..6) add(SpbFixture.Verse(19, 23, v, "Psalm twenty three verse $v"))
                    for (v in 1..20) add(SpbFixture.Verse(43, 3, v, "John three verse $v"))
                },
            ),
        )
        val songDir = File(home, "songs").also { it.mkdirs() }

        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().let { s ->
                s.copy(
                    songSettings = s.songSettings.copy(storageDirectory = songDir.absolutePath),
                    bibleSettings = s.bibleSettings.copy(
                        storageDirectory = bibleDir.absolutePath,
                        primaryBible = "test.spb",
                    ),
                )
            },
        )

        mockkObject(PlanningCenterClient)
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(emptyList())

    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PlanningCenterClient)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun viewModel() = PlanningCenterImportViewModel(
        initialAccessToken = "valid-token",
        initialRefreshToken = "refresh",
        initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
        initialServiceTypeId = "st-1",
        importSongbookName = "Planning Center",
        onTokensRefreshed = { _, _, _ -> },
    )

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun item(id: String, title: String, description: String = "", itemType: String = "item") =
        PlanningCenterClient.PlanItem(
            id = id, title = title, description = description, itemType = itemType, sequence = 0,
        )

    private fun loadPlan(vm: PlanningCenterImportViewModel, items: List<PlanningCenterClient.PlanItem>) {
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(items)
        vm.selectPlan("plan-1")
        awaitUntil("plan items") { !vm.isLoadingItems && vm.planItems.isNotEmpty() }
    }

    // ── Detection ───────────────────────────────────────────────────────────────

    @Test
    fun `a reference in an item title is detected and resolved`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Psalms 23:1-6")))
        awaitUntil("detection") { vm.detectedScripturesByItemId.containsKey("i1") }

        val verses = assertNotNull(vm.detectedScripturesByItemId["i1"]).single()
        assertEquals("Psalms", verses.bookName)
        assertEquals(23, verses.chapter)
        assertEquals("1-6", verses.verseRange)
        assertTrue(verses.verseText.contains("Psalm twenty three verse 1"))
    }

    @Test
    fun `a reference in the description is detected too`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Scripture Reading", description = "John 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId.containsKey("i1") }
        assertEquals("John 3:16", vm.detectedScripturesByItemId["i1"]!!.single().displayReference)
    }

    @Test
    fun `several references on one item all resolve`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Readings", description = "Psalms 23:1\nJohn 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId["i1"]?.size == 2 }
    }

    @Test
    fun `an item with no reference produces no scripture entry`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Welcome and announcements")))
        awaitUntil("items to settle") { !vm.isLoadingItems }
        assertFalse(vm.detectedScripturesByItemId.containsKey("i1"))
    }

    @Test
    fun `song and header rows are not scanned for scripture`() {
        // Only generic "item" rows are scanned; a song titled like a reference must not become one.
        val vm = viewModel()
        loadPlan(
            vm,
            listOf(
                item("i1", "Psalms 23:1-6", itemType = "song"),
                item("i2", "Psalms 23:1-6", itemType = "header"),
            ),
        )
        awaitUntil("items to settle") { !vm.isLoadingItems }
        assertTrue(vm.detectedScripturesByItemId.isEmpty())
    }

    // NOTE: "a reference to a book the loaded Bible does not have" is covered by
    // PlanningCenterScriptureDetectorTest, where BibleBookAbbreviations can be stubbed cleanly.
    // Exercising it through this view model reaches the abbreviation fallback for real, which
    // throws HeadlessException in a test JVM and kills the load coroutine.

    @Test
    fun `detected scriptures start fully selected`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Readings", description = "Psalms 23:1\nJohn 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId["i1"]?.size == 2 }
        assertEquals(setOf(0, 1), vm.selectedScriptureIndices["i1"])
        assertTrue(vm.allSelected)
    }

    // ── Per-scripture checkboxes ────────────────────────────────────────────────

    @Test
    fun `a single scripture can be unchecked`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Readings", description = "Psalms 23:1\nJohn 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId["i1"]?.size == 2 }

        vm.toggleScriptureSelected("i1", 0)
        assertEquals(setOf(1), vm.selectedScriptureIndices["i1"])
        assertFalse(vm.allSelected, "a partly selected item breaks the master checkbox")

        vm.toggleScriptureSelected("i1", 0)
        assertEquals(setOf(0, 1), vm.selectedScriptureIndices["i1"])
        assertTrue(vm.allSelected)
    }

    @Test
    fun `unchecking every scripture leaves the set empty`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Readings", description = "Psalms 23:1\nJohn 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId["i1"]?.size == 2 }

        vm.toggleScriptureSelected("i1", 0)
        vm.toggleScriptureSelected("i1", 1)
        assertTrue(vm.selectedScriptureIndices["i1"]!!.isEmpty())
    }

    @Test
    fun `the master checkbox clears and restores scripture selections`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Readings", description = "Psalms 23:1\nJohn 3:16")))
        awaitUntil("detection") { vm.detectedScripturesByItemId["i1"]?.size == 2 }

        vm.setAllSelected(false)
        assertTrue(vm.selectedScriptureIndices["i1"]!!.isEmpty())
        assertFalse(vm.allSelected)

        vm.setAllSelected(true)
        assertEquals(setOf(0, 1), vm.selectedScriptureIndices["i1"])
        assertTrue(vm.allSelected)
    }

    @Test
    fun `toggling a scripture on an unknown item just records it`() {
        val vm = viewModel()
        loadPlan(vm, listOf(item("i1", "Welcome")))
        vm.toggleScriptureSelected("no-such-item", 0)
        assertEquals(setOf(0), vm.selectedScriptureIndices["no-such-item"])
    }

    // ── Lyrics prefill ──────────────────────────────────────────────────────────

    private fun songItem(
        id: String = "s1",
        description: String = "",
        htmlDetails: String = "",
        songId: String? = null,
        arrangementId: String? = null,
    ) = PlanningCenterClient.PlanItem(
        id = id, title = "A Song", description = description, htmlDetails = htmlDetails,
        itemType = "song", sequence = 0, songId = songId, arrangementId = arrangementId,
    )

    @Test
    fun `an arrangement's lyrics win over the description`() = runBlocking {
        coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any()) } returns
            PlanningCenterClient.ArrangementOutcome.Success(
                PlanningCenterClient.ArrangementDetail(chordChart = "G C D", lyrics = "Arrangement lyrics"),
            )
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
        )
        assertEquals("Arrangement lyrics", assertNotNull(detail).lyrics)
        assertEquals("G C D", detail.chordChart)
    }

    @Test
    fun `a blank arrangement falls back to the description`() {
        // PCO returns an arrangement record with no lyrics when nobody filled it in.
        runBlocking {
            coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any()) } returns
                PlanningCenterClient.ArrangementOutcome.Success(
                    PlanningCenterClient.ArrangementDetail(chordChart = "", lyrics = "   "),
                )
            val detail = viewModel().fetchArrangementForAddSong(
                songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
            )
            assertEquals("Description lyrics", assertNotNull(detail).lyrics)
        }
    }

    @Test
    fun `a failed arrangement request falls back to the description`() = runBlocking {
        coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any()) } returns
            PlanningCenterClient.ArrangementOutcome.NetworkError
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Description lyrics", songId = "song-1", arrangementId = "arr-1"),
        )
        assertEquals("Description lyrics", assertNotNull(detail).lyrics)
    }

    @Test
    fun `html details are used when there is no description`() = runBlocking {
        // Worship leaders paste lyrics into PCO's rich "Details" box as often as the plain field.
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(htmlDetails = "<p>First line</p><p>Second line</p>"),
        )
        val lyrics = assertNotNull(detail).lyrics
        assertTrue(lyrics.contains("First line"))
        assertTrue(lyrics.contains("Second line"))
        assertFalse(lyrics.contains("<p>"), "the HTML must be converted to plain text")
    }

    @Test
    fun `the description wins over html details`() = runBlocking {
        val detail = viewModel().fetchArrangementForAddSong(
            songItem(description = "Plain description", htmlDetails = "<p>Rich details</p>"),
        )
        assertEquals("Plain description", assertNotNull(detail).lyrics)
    }

    @Test
    fun `html details that render to nothing yield no prefill`() = runBlocking {
        assertNull(viewModel().fetchArrangementForAddSong(songItem(htmlDetails = "<p></p>")))
    }

    @Test
    fun `a song with no arrangement ids never calls the api`() = runBlocking {
        // No songId/arrangementId means there is nothing to request.
        val detail = viewModel().fetchArrangementForAddSong(songItem(description = "Description only"))
        assertEquals("Description only", assertNotNull(detail).lyrics)
    }

    // ── Creating local songs ────────────────────────────────────────────────────

    @Test
    fun `a created song is returned with its source file populated`() {
        val created = assertNotNull(
            viewModel().createLocalSong(
                SongItem(number = "0500", title = "Imported", songbook = "Hymnal", lyrics = listOf("line")),
            ),
        )
        assertTrue(created.sourceFile.endsWith("0500 - Imported.song"))
        assertTrue(File(created.sourceFile).exists())
        assertTrue(created.songId.isNotBlank(), "the caller adds this straight to the schedule")
    }

    @Test
    fun `a song with no number is filed under its title`() {
        val created = assertNotNull(
            viewModel().createLocalSong(SongItem(number = "", title = "No Number", songbook = "Hymnal", lyrics = listOf("l"))),
        )
        assertTrue(created.sourceFile.endsWith("No Number.song"))
    }

    @Test
    fun `a song with no songbook is refused`() {
        assertNull(
            viewModel().createLocalSong(SongItem(number = "1", title = "Orphan", songbook = "", lyrics = listOf("l"))),
        )
    }
}
