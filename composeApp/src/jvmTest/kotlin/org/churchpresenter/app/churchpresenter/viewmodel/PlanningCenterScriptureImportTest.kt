package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveoutput.content
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.data.PlanningCenterPrimaryBible
import org.churchpresenter.app.churchpresenter.dialogs.appPlanningCenterServices
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.planningcenter.ui.PlanningCenterImportViewModel
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The scripture half of a Planning Center import, through the app's own wiring of it.
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
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
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
        services = appPlanningCenterServices(PlanningCenterPrimaryBible()),
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
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
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
}
