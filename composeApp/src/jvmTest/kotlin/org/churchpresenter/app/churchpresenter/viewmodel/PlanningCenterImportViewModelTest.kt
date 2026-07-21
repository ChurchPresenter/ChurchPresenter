package org.churchpresenter.app.churchpresenter.viewmodel

import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.data.SongFileParser
import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.SettingsManager
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
 * Importing a Planning Center service plan.
 *
 * Every remote call goes through the `PlanningCenterClient` object, so MockK replaces the whole
 * API surface — no network, no OAuth. What is actually under test is the app-side logic: matching
 * plan items against the local song library, the cascading selection checkboxes, and the token
 * refresh that has to happen before any request.
 *
 * Local-song matching is the part that decides whether an import creates duplicates of songs the
 * church already has, so its three tiers (CCLI number, leading 4-digit song number, then title)
 * are covered individually.
 */
class PlanningCenterImportViewModelTest {

    private lateinit var home: File
    private var realHome: String? = null
    private lateinit var songDir: File

    private val refreshed = mutableListOf<Triple<String, String, Long>>()

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-home").toFile()
        System.setProperty("user.home", home.absolutePath)

        songDir = File(home, "songs").also { it.mkdirs() }
        // A local library to match plan items against.
        writeSong(number = "0042", title = "Amazing Grace", ccli = "22025")
        writeSong(number = "0100", title = "How Great Thou Art", ccli = "")

        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().let { s ->
                s.copy(songSettings = s.songSettings.copy(storageDirectory = songDir.absolutePath))
            },
        )

        mockkObject(PlanningCenterClient)
        refreshed.clear()
    }

    @AfterTest
    fun tearDown() {
        unmockkObject(PlanningCenterClient)
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    private fun writeSong(number: String, title: String, ccli: String) {
        SongFileParser().writeSongFile(
            SongItem(
                number = number, title = title, songbook = "Hymnal",
                ccliNumber = ccli, lyrics = listOf("[Verse 1]", "a line"),
            ),
            File(File(songDir, "Hymnal"), "$number - $title.song").absolutePath,
        )
    }

    /** A view model whose token is valid for another hour, so no refresh is attempted. */
    private fun viewModel(
        accessToken: String = "valid-token",
        refreshToken: String = "refresh-token",
        expiresInMs: Long = 3_600_000,
        songbook: String = "Planning Center",
    ) = PlanningCenterImportViewModel(
        initialAccessToken = accessToken,
        initialRefreshToken = refreshToken,
        initialExpiresAtEpochMs = System.currentTimeMillis() + expiresInMs,
        initialServiceTypeId = "st-1",
        importSongbookName = songbook,
        onTokensRefreshed = { a, r, e -> refreshed.add(Triple(a, r, e)) },
    )

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for $what")
    }

    private fun planItem(
        id: String,
        title: String,
        itemType: String = "song",
        songTitle: String? = null,
        ccli: String? = null,
        description: String = "",
        sequence: Int = 0,
    ) = PlanningCenterClient.PlanItem(
        id = id, title = title, description = description, itemType = itemType,
        sequence = sequence, songTitle = songTitle, songCcliNumber = ccli,
    )

    /** Stubs the plan-items call and loads them into [vm]. */
    private fun loadItems(vm: PlanningCenterImportViewModel, items: List<PlanningCenterClient.PlanItem>) {
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(items)
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(emptyList())
        vm.selectPlan("plan-1")
        awaitUntil("plan items") { !vm.isLoadingItems && vm.planItems.isNotEmpty() }
    }

    // ── Service types and plans ─────────────────────────────────────────────────

    @Test
    fun `service types load`() {
        coEvery { PlanningCenterClient.listServiceTypes(any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.Success(
                listOf(PlanningCenterClient.ServiceType("st-1", "Sunday Morning")),
            )
        val vm = viewModel()
        vm.loadServiceTypes()
        awaitUntil("service types") { !vm.isLoadingServiceTypes && vm.serviceTypes.isNotEmpty() }

        assertEquals("Sunday Morning", vm.serviceTypes.single().name)
        assertNull(vm.errorMessage)
    }

    @Test
    fun `an expired session surfaces a reconnect message`() {
        coEvery { PlanningCenterClient.listServiceTypes(any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.Unauthorized
        val vm = viewModel()
        vm.loadServiceTypes()
        awaitUntil("the error") { vm.errorMessage != null }
        assertTrue(vm.errorMessage!!.contains("reconnect", ignoreCase = true))
    }

    @Test
    fun `a network failure surfaces an error rather than an empty list`() {
        coEvery { PlanningCenterClient.listServiceTypes(any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.NetworkError
        val vm = viewModel()
        vm.loadServiceTypes()
        awaitUntil("the error") { vm.errorMessage != null }
        assertTrue(vm.serviceTypes.isEmpty())
    }

    @Test
    fun `selecting a service type loads its plans`() {
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(
                listOf(PlanningCenterClient.Plan("plan-1", "Sunday", "21 July 2026")),
            )
        val vm = viewModel()
        vm.selectServiceType("st-2")
        awaitUntil("plans") { !vm.isLoadingPlans && vm.plans.isNotEmpty() }

        assertEquals("st-2", vm.selectedServiceTypeId)
        assertEquals("Sunday", vm.plans.single().title)
    }

    // ── Token refresh ───────────────────────────────────────────────────────────

    @Test
    fun `an expiring token is refreshed before the request`() {
        coEvery { PlanningCenterClient.refreshAccessToken(any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.Success(
                PlanningCenterClient.TokenSet("new-access", "new-refresh", System.currentTimeMillis() + 7_200_000),
            )
        coEvery { PlanningCenterClient.listServiceTypes(any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.Success(emptyList())

        // Already inside the 60-second refresh window.
        val vm = viewModel(expiresInMs = 30_000)
        vm.loadServiceTypes()
        awaitUntil("the refresh") { refreshed.isNotEmpty() }

        assertEquals("new-access", refreshed.single().first, "the new tokens must be persisted")
        assertEquals("new-refresh", refreshed.single().second)
    }

    @Test
    fun `a failed refresh abandons the request`() {
        coEvery { PlanningCenterClient.refreshAccessToken(any(), any(), any()) } returns
            PlanningCenterClient.TokenOutcome.Failure
        val vm = viewModel(expiresInMs = 30_000)
        vm.loadServiceTypes()
        awaitUntil("loading to finish") { !vm.isLoadingServiceTypes }

        assertTrue(vm.serviceTypes.isEmpty())
        assertTrue(refreshed.isEmpty())
    }

    @Test
    fun `no access token means no request at all`() {
        val vm = viewModel(accessToken = "")
        vm.loadServiceTypes()
        awaitUntil("loading to finish") { !vm.isLoadingServiceTypes }
        assertTrue(vm.serviceTypes.isEmpty())
    }

    @Test
    fun `a valid token is not refreshed`() {
        coEvery { PlanningCenterClient.listServiceTypes(any()) } returns
            PlanningCenterClient.ServiceTypesOutcome.Success(emptyList())
        val vm = viewModel(expiresInMs = 3_600_000)
        vm.loadServiceTypes()
        awaitUntil("loading to finish") { !vm.isLoadingServiceTypes }
        assertTrue(refreshed.isEmpty(), "a token good for an hour needs no refresh")
    }

    // ── Matching against the local library ──────────────────────────────────────

    @Test
    fun `a song is matched by its CCLI number`() {
        // The strongest signal: the same song may be titled differently in PCO.
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "Totally Different Title", ccli = "22025")))
        assertNotNull(vm.planItems.single().matchedSongId, "a CCLI hit must not create a duplicate song")
    }

    @Test
    fun `a song is matched by a leading four-digit number`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "0042 Amazing Grace")))
        assertNotNull(vm.planItems.single().matchedSongId)
    }

    @Test
    fun `three or five digit leading numbers are not song numbers`() {
        // "1999 Christmas Service" is a year, not song 1999.
        val vm = viewModel()
        loadItems(
            vm,
            listOf(
                planItem("i1", "042 Something Else"),
                planItem("i2", "00420 Something Else"),
            ),
        )
        assertTrue(vm.planItems.all { it.matchedSongId == null }, "only an exact 4-digit prefix counts")
    }

    @Test
    fun `a song is matched by title as a last resort`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "ignored", songTitle = "how great thou art")))
        assertNotNull(vm.planItems.single().matchedSongId, "title matching is case-insensitive")
    }

    @Test
    fun `a song not in the library is left unmatched`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "A Song We Do Not Have")))
        assertNull(vm.planItems.single().matchedSongId, "an unmatched item becomes a new song")
    }

    @Test
    fun `non-song items are never matched`() {
        val vm = viewModel()
        loadItems(
            vm,
            listOf(
                planItem("i1", "Amazing Grace", itemType = "header"),
                planItem("i2", "Amazing Grace", itemType = "media"),
            ),
        )
        assertTrue(vm.planItems.all { it.matchedSongId == null }, "a header that happens to name a song is not a song")
    }

    // ── Selection ───────────────────────────────────────────────────────────────

    @Test
    fun `every item starts selected`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "One"), planItem("i2", "Two")))
        assertTrue(vm.planItems.all { it.selected })
        assertTrue(vm.allSelected)
    }

    @Test
    fun `toggling one item leaves the others alone`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "One"), planItem("i2", "Two")))

        vm.toggleItemSelected("i1")
        assertFalse(vm.planItems.first { it.pco.id == "i1" }.selected)
        assertTrue(vm.planItems.first { it.pco.id == "i2" }.selected)
        assertFalse(vm.allSelected)

        vm.toggleItemSelected("i1")
        assertTrue(vm.allSelected, "re-checking restores the master checkbox")
    }

    @Test
    fun `the master checkbox selects and clears everything`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "One"), planItem("i2", "Two")))

        vm.setAllSelected(false)
        assertTrue(vm.planItems.none { it.selected })
        assertFalse(vm.allSelected)

        vm.setAllSelected(true)
        assertTrue(vm.planItems.all { it.selected })
        assertTrue(vm.allSelected)
    }

    @Test
    fun `an empty plan is not considered fully selected`() {
        val vm = viewModel()
        assertFalse(vm.allSelected, "with nothing to import the master checkbox stays clear")
    }

    @Test
    fun `toggling an unknown item id is harmless`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "One")))
        vm.toggleItemSelected("no-such-item")
        assertTrue(vm.planItems.single().selected)
    }

    // ── Attachments ─────────────────────────────────────────────────────────────

    @Test
    fun `attachments load and start selected`() {
        val vm = viewModel()
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Notices", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(
                    PlanningCenterClient.PlanAttachment("a1", "slides.pptx"),
                    PlanningCenterClient.PlanAttachment("a2", "background.jpg"),
                ),
            )
        vm.selectPlan("plan-1")
        awaitUntil("attachments") { vm.attachmentsByItemId["i1"]?.size == 2 }

        assertEquals(setOf("a1", "a2"), vm.selectedAttachmentIds["i1"])
    }

    @Test
    fun `unchecking a row clears its attachments too`() {
        // The row checkbox is the master for its files; leaving them checked would import files
        // for an item the operator just excluded.
        val vm = viewModel()
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Notices", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(PlanningCenterClient.PlanAttachment("a1", "slides.pptx")),
            )
        vm.selectPlan("plan-1")
        awaitUntil("attachments") { vm.attachmentsByItemId["i1"]?.isNotEmpty() == true }

        vm.toggleItemSelected("i1")
        assertTrue(vm.selectedAttachmentIds["i1"].isNullOrEmpty())

        vm.toggleItemSelected("i1")
        assertEquals(setOf("a1"), vm.selectedAttachmentIds["i1"], "re-checking restores them")
    }

    @Test
    fun `an individual attachment can be toggled`() {
        val vm = viewModel()
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Notices", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(
                    PlanningCenterClient.PlanAttachment("a1", "one.pptx"),
                    PlanningCenterClient.PlanAttachment("a2", "two.jpg"),
                ),
            )
        vm.selectPlan("plan-1")
        awaitUntil("attachments") { vm.attachmentsByItemId["i1"]?.size == 2 }

        vm.toggleAttachmentSelected("i1", "a1")
        assertEquals(setOf("a2"), vm.selectedAttachmentIds["i1"])
        assertFalse(vm.allSelected, "a partially selected row breaks the master checkbox")

        vm.toggleAttachmentSelected("i1", "a1")
        assertEquals(setOf("a1", "a2"), vm.selectedAttachmentIds["i1"])
    }

    // ── Lyrics prefill ──────────────────────────────────────────────────────────

    @Test
    fun `the item description is used when there is no arrangement`() = kotlinx.coroutines.runBlocking {
        val vm = viewModel()
        val detail = vm.fetchArrangementForAddSong(
            planItem("i1", "Song", description = "Verse one line\nVerse two line"),
        )
        assertEquals("Verse one line\nVerse two line", assertNotNull(detail).lyrics)
    }

    @Test
    fun `an item with nothing usable yields no prefill`() = kotlinx.coroutines.runBlocking {
        val vm = viewModel()
        assertNull(vm.fetchArrangementForAddSong(planItem("i1", "Song")))
    }

    // ── New songs ───────────────────────────────────────────────────────────────

    @Test
    fun `new songs default to the configured import songbook`() {
        assertEquals("Sunday Imports", viewModel(songbook = "Sunday Imports").defaultSongbookForNewSongs())
    }

    @Test
    fun `a blank import songbook falls back to a sensible name`() {
        assertEquals("Planning Center", viewModel(songbook = "").defaultSongbookForNewSongs())
    }

    @Test
    fun `a confirmed song is written into the song library`() {
        val vm = viewModel()
        val created = vm.createLocalSong(
            SongItem(number = "0500", title = "Imported Song", songbook = "Hymnal", lyrics = listOf("[Verse 1]", "line")),
        )
        assertNotNull(created)
        assertTrue(File(File(songDir, "Hymnal"), "0500 - Imported Song.song").exists())
    }

    @Test
    fun `resolving an item records its matched song`() {
        val vm = viewModel()
        loadItems(vm, listOf(planItem("i1", "A Song We Do Not Have")))
        assertNull(vm.planItems.single().matchedSongId)

        vm.markItemResolved("i1", "Hymnal::0500")
        assertEquals("Hymnal::0500", vm.planItems.single().matchedSongId)
    }
}
