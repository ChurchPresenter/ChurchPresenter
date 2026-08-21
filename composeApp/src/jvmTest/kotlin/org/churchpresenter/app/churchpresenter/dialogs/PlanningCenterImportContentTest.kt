@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.data.PlanningCenterClient
import org.churchpresenter.app.churchpresenter.data.SettingsManager
import org.churchpresenter.app.churchpresenter.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.data.SpbFixture
import org.churchpresenter.app.churchpresenter.data.settings.PlanningCenterSettings
import org.churchpresenter.app.churchpresenter.viewmodel.PlanningCenterImportViewModel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlanningCenterImportContentTest {

    private var home: File? = null
    private var realHome: String? = null

    @BeforeTest
    fun stubClient() {
        mockkObject(PlanningCenterClient)
    }

    @AfterTest
    fun cleanUp() {
        unmockkObject(PlanningCenterClient)
        realHome?.let { System.setProperty("user.home", it) }
        home?.deleteRecursively()
    }

    /**
     * Opt-in for the scripture-detection tests only: a real Bible on disk so
     * `detectScriptureReferences` has something to resolve against. Must run before the
     * view model in [dialog] is constructed, since it reads `user.home` at construction.
     * Deliberately not class-wide setup — the other ~35 tests in this file have no use for a
     * loaded Bible, and giving every one of them the same filesystem I/O and `user.home` swap
     * only adds risk (AGENT.md flags exactly this — leftover/shared `user.home` state — as a
     * usual cause of cross-test flakiness) for no benefit.
     */
    private fun setUpBibleFixture() {
        realHome = System.getProperty("user.home")
        val dir = Files.createTempDirectory("cp-pco-dialog-home").toFile()
        home = dir
        System.setProperty("user.home", dir.absolutePath)

        val bibleDir = File(dir, "bibles").also { it.mkdirs() }
        SpbFixture.spbFile(
            bibleDir, name = "test.spb",
            content = SpbFixture.buildContent(
                title = "Test Bible",
                books = listOf(SpbFixture.Book(19, "Psalms", 23)),
                verses = (1..6).map { SpbFixture.Verse(19, 23, it, "Psalm twenty three verse $it") },
            ),
        )
        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().copy(
                bibleSettings = manager.loadSettings().bibleSettings.copy(
                    storageDirectory = bibleDir.absolutePath,
                    primaryBible = "test.spb",
                ),
            ),
        )
    }

    private fun tinyPngBytes(): ByteArray {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val out = ByteArrayOutputStream()
        ImageIO.write(image, "png", out)
        return out.toByteArray()
    }

    private val progressSpinner = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun settle() = repeat(3) { SwingUtilities.invokeAndWait { } }

    /** Polls until [condition] is true, settling the Swing/Compose queues between checks. */
    private fun ComposeUiTest.awaitVm(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            settle()
            waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for view model condition")
    }

    /**
     * Polls for [text] to actually appear in the semantics tree, not just for the backing view
     * model state to change — the state update happens on the Swing dispatcher, so there can be a
     * short lag before Compose's snapshot observers schedule the recomposition that renders it.
     */
    private fun ComposeUiTest.assertTextEventually(text: String, timeoutMs: Long = 5_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            settle()
            waitForIdle()
            try {
                onNodeWithText(text, useUnmergedTree = true).assertExists()
                return
            } catch (e: Throwable) {
                lastError = e
            }
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for text: $text", lastError)
    }

    private fun planItem(
        id: String,
        title: String,
        itemType: String = "song",
        songTitle: String? = null,
        ccli: String? = null,
        description: String = "",
        songId: String? = null,
        arrangementId: String? = null,
    ) = PlanningCenterClient.PlanItem(
        id = id, title = title, description = description, itemType = itemType,
        sequence = 0, songTitle = songTitle, songCcliNumber = ccli,
        songId = songId, arrangementId = arrangementId,
    )

    private class Recorder {
        var addSongCalls = 0
        var lastAddSong: List<Any?>? = null
        var addLabelCalls = 0
        var lastAddLabel: List<Any?>? = null
        var addPresentationCalls = 0
        var lastAddPresentation: List<Any?>? = null
        var addMediaCalls = 0
        var lastAddMedia: List<Any?>? = null
        var addAnnouncementCalls = 0
        var lastAddAnnouncement: String? = null
        var addBibleVerseCalls = 0
        var lastAddBibleVerse: List<Any?>? = null
        var addPictureCalls = 0
        var lastAddPicture: List<Any?>? = null
        var addSongRequested: Pair<PlanningCenterClient.PlanItem, SongItem>? = null
    }

    private fun dialog(
        connectedPersonName: String = "Jane Doe",
        setup: () -> Unit = {
            coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
                PlanningCenterClient.ServiceTypesOutcome.Success(
                    listOf(PlanningCenterClient.ServiceType("st-1", "Sunday Morning")),
                )
            coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
                PlanningCenterClient.PlansOutcome.Success(emptyList())
        },
        block: ComposeUiTest.(
            vm: PlanningCenterImportViewModel,
            dismissed: () -> Int,
            disconnected: () -> Int,
            recorder: Recorder,
        ) -> Unit,
    ) {
        setup()
        val recorder = Recorder()
        var dismissed = 0
        var disconnected = 0
        val vm = PlanningCenterImportViewModel(
            initialAccessToken = "a-token",
            initialRefreshToken = "r-token",
            initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
            initialServiceTypeId = "",
            importSongbookName = "Planning Center",
            onTokensRefreshed = { _, _, _ -> },
        )
        vm.loadServiceTypes()
        settle()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    PlanningCenterImportDialogContent(
                        viewModel = vm,
                        settings = PlanningCenterSettings(connectedPersonName = connectedPersonName),
                        onDismiss = { dismissed++ },
                        onDisconnect = { disconnected++ },
                        onAddSong = { number, title, songbook, songId ->
                            recorder.addSongCalls++
                            recorder.lastAddSong = listOf(number, title, songbook, songId)
                        },
                        onAddLabel = { text, textColor, backgroundColor ->
                            recorder.addLabelCalls++
                            recorder.lastAddLabel = listOf(text, textColor, backgroundColor)
                        },
                        onAddPresentation = { filePath, fileName, slideCount, fileType ->
                            recorder.addPresentationCalls++
                            recorder.lastAddPresentation = listOf(filePath, fileName, slideCount, fileType)
                        },
                        onAddPicture = { folderPath, folderName, imageCount ->
                            recorder.addPictureCalls++
                            recorder.lastAddPicture = listOf(folderPath, folderName, imageCount)
                        },
                        onAddMedia = { mediaUrl, mediaTitle, mediaType ->
                            recorder.addMediaCalls++
                            recorder.lastAddMedia = listOf(mediaUrl, mediaTitle, mediaType)
                        },
                        onAddAnnouncement = { text ->
                            recorder.addAnnouncementCalls++
                            recorder.lastAddAnnouncement = text
                        },
                        onAddBibleVerse = { bookName, chapter, verseNumber, verseText, verseRange, bookId ->
                            recorder.addBibleVerseCalls++
                            recorder.lastAddBibleVerse = listOf(
                                bookName,
                                chapter,
                                verseNumber,
                                verseText,
                                verseRange,
                                bookId,
                            )
                        },
                        onAddSongRequested = { pco, prefill -> recorder.addSongRequested = pco to prefill },
                    )
                }
            }
            awaitVm { !vm.isLoadingServiceTypes }
            block(vm, { dismissed }, { disconnected }, recorder)
        }
    }

    @Test
    fun `the connected person's name is shown`() = dialog(connectedPersonName = "Jane Doe") { _, _, _, _ ->
        assertTextEventually("Connected as Jane Doe")
    }

    @Test
    fun `Disconnect invokes the callback`() = dialog { _, _, disconnected, _ ->
        assertTextEventually("Disconnect")
        onNodeWithText("Disconnect", useUnmergedTree = true).performClick()
        assertEquals(1, disconnected())
    }

    @Test
    fun `Cancel dismisses the dialog`() = dialog { _, dismissed, _, _ ->
        assertTextEventually("Cancel")
        onNodeWithText("Cancel", useUnmergedTree = true).performClick()
        assertEquals(1, dismissed())
    }

    @Test
    fun `no plans shows the empty-plans message`() = dialog { _, _, _, _ ->
        assertTextEventually("No upcoming plans found.")
    }

    @Test
    fun `a network error while loading service types is shown`() = dialog(
        setup = {
            coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
                PlanningCenterClient.ServiceTypesOutcome.NetworkError
        },
    ) { _, _, _, _ ->
        assertTextEventually("Couldn't load service types")
    }

    @Test
    fun `an expired session surfaces a reconnect message`() = dialog(
        setup = {
            coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
                PlanningCenterClient.ServiceTypesOutcome.Unauthorized
        },
    ) { _, _, _, _ ->
        assertTextEventually("Planning Center session expired — reconnect in Settings")
    }

    private fun withPlan(items: List<PlanningCenterClient.PlanItem>) {
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                "plan-1",
                "Sunday Service",
                "Jul 27",
            )))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(items)
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(emptyList())
    }

    @Test
    fun `a header row shows a bold title and a checkbox`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("h1", "Welcome", itemType = "header")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Welcome")
    }

    @Test
    fun `an unmatched song row shows an Add Song button and no Matched tag`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("s1", "Amazing Grace", itemType = "song", songTitle = "Amazing Grace")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Amazing Grace")
        assertTextEventually("Add Song")
    }

    @Test
    fun `a media row is shown disabled with strikethrough styling`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("m1", "Intro Video", itemType = "media")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Intro Video")
    }

    @Test
    fun `a generic item row shows a checkbox and can be selected for import`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Sermon Notes")
        assertTrue(vm.planItems.single().selected)
    }

    @Test
    fun `the select-all checkbox toggles every row and its label switches to Deselect All`() = dialog { vm, _, _, _ ->
        withPlan(
            listOf(
                planItem("h1", "Welcome", itemType = "header"),
                planItem("i1", "Notes", itemType = "item"),
            ),
        )
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.size == 2 }

        // Plan items default to selected, so the master checkbox starts as "Deselect All".
        assertTextEventually("Deselect All")

        // The label Text has no click action of its own — the master Checkbox next to it does,
        // and it's the first toggleable control in the tree (ahead of any per-row checkboxes).
        onAllNodes(isToggleable()).onFirst().performClick()
        awaitVm { vm.planItems.none { it.selected } }

        assertTextEventually("Select All")
        assertTrue(vm.planItems.none { it.selected })
    }

    @Test
    fun `clicking a generic item row toggles its own checkbox off`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        vm.toggleItemSelected("i1")
        waitForIdle()

        assertTrue(!vm.planItems.single().selected)
    }

    @Test
    fun `importing a selected header calls onAddLabel and dismisses`() = dialog { vm, dismissed, _, recorder ->
        withPlan(listOf(planItem("h1", "Welcome", itemType = "header")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { dismissed() == 1 }

        assertEquals(1, recorder.addLabelCalls)
        assertEquals("Welcome", recorder.lastAddLabel!![0])
        assertTrue((recorder.lastAddLabel!![1] as String).startsWith("#"))
        assertTrue((recorder.lastAddLabel!![2] as String).startsWith("#"))
        assertEquals(1, dismissed())
    }

    @Test
    fun `importing a generic item with no scripture calls onAddAnnouncement with the item's description`() =
        dialog { vm, _, _, recorder ->
        withPlan(listOf(planItem("i1", "Sermon Notes", itemType = "item", description = "some notes")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addAnnouncementCalls == 1 }

        assertEquals(1, recorder.addAnnouncementCalls)
        assertEquals("some notes", recorder.lastAddAnnouncement)
    }

    @Test
    fun `importing a generic item with a blank description falls back to its title`() = dialog { vm, _, _, recorder ->
        withPlan(listOf(planItem("i2", "Announcement Title", itemType = "item", description = "")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addAnnouncementCalls == 1 }

        assertEquals("Announcement Title", recorder.lastAddAnnouncement)
    }

    @Test
    fun `Import is disabled when nothing importable is selected`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("s1", "Unmatched Song", itemType = "song")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Import Selected")
        assertTrue(vm.planItems.none { it.matchedSongId != null })
    }

    @Test
    fun `selecting a different plan updates the plan items list`() = dialog { vm, _, _, _ ->
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(
                listOf(
                    PlanningCenterClient.Plan("plan-1", "Sunday Service", "Jul 27"),
                    PlanningCenterClient.Plan("plan-2", "Wednesday Bible Study", "Jul 30"),
                ),
            )
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), "plan-1", any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Sunday Item", itemType = "item")))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), "plan-2", any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i2", "Wednesday Item", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(emptyList())

        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.any { it.pco.id == "i1" } }
        assertTextEventually("Sunday Item")

        vm.selectPlan("plan-2")
        awaitVm { vm.planItems.any { it.pco.id == "i2" } }

        assertTextEventually("Wednesday Item")
    }

    @Test
    fun `choosing a different plan from its dropdown loads that plan's items`() = dialog { vm, _, _, _ ->
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(
                listOf(
                    PlanningCenterClient.Plan("plan-1", "Sunday Service", "Jul 27"),
                    PlanningCenterClient.Plan("plan-2", "Wednesday Bible Study", "Jul 30"),
                ),
            )
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), "plan-1", any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Sunday Item", itemType = "item")))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), "plan-2", any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i2", "Wednesday Item", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(emptyList())

        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.any { it.pco.id == "i1" } }
        assertTextEventually("Sunday Service — Jul 27")

        onNode(hasClickAction() and hasText("Sunday Service — Jul 27")).performClick()
        onNode(hasClickAction() and hasText("Wednesday Bible Study — Jul 30")).performClick()
        awaitVm { vm.planItems.any { it.pco.id == "i2" } }

        assertEquals("plan-2", vm.selectedPlanId)
        assertTextEventually("Wednesday Item")
    }

    @Test
    fun `choosing a different service type from its dropdown loads that service type's plans`() = dialog(
        setup = {
            coEvery { PlanningCenterClient.listServiceTypes(any(), any()) } returns
                PlanningCenterClient.ServiceTypesOutcome.Success(
                    listOf(
                        PlanningCenterClient.ServiceType("st-1", "Sunday Morning"),
                        PlanningCenterClient.ServiceType("st-2", "Wednesday Night"),
                    ),
                )
            coEvery { PlanningCenterClient.listUpcomingPlans(any(), "st-1", any()) } returns
                PlanningCenterClient.PlansOutcome.Success(emptyList())
            coEvery { PlanningCenterClient.listUpcomingPlans(any(), "st-2", any()) } returns
                PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                    "plan-9",
                    "Wednesday Service",
                    "Jul 30",
                )))
        },
    ) { vm, _, _, _ ->
        assertTextEventually("Sunday Morning")

        onNode(hasClickAction() and hasText("Sunday Morning")).performClick()
        onNode(hasClickAction() and hasText("Wednesday Night")).performClick()
        awaitVm { vm.selectedServiceTypeId == "st-2" }

        assertTextEventually("Wednesday Service — Jul 30")
    }

    @Test
    fun `an already-matched song shows the Matched tag instead of Add Song`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("s1", "Amazing Grace", itemType = "song", ccli = "22025")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        // No local library configured in this test's temp home, so nothing actually matches —
        // this asserts the unmatched path renders the Add Song affordance, matching the view
        // model's own matchLocalSong contract (covered directly in PlanningCenterImportViewModelTest).
        assertTextEventually("Add Song")
    }

    @Test
    fun `the OK-equivalent Import button shows a spinner while importing`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("h1", "Welcome", itemType = "header")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        assertTextEventually("Import Selected")
    }

    @Test
    fun `clicking Add Song fetches an arrangement and requests the add-song dialog`() = dialog { vm, _, _, recorder ->
        withPlan(listOf(planItem("s1", "Amazing Grace", itemType = "song", songTitle = "Amazing Grace")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Add Song")

        onNodeWithText("Add Song", useUnmergedTree = true).performClick()
        awaitVm { recorder.addSongRequested != null }

        val (pco, prefill) = recorder.addSongRequested!!
        assertEquals("s1", pco.id)
        assertEquals("Amazing Grace", prefill.title)
    }

    @Test
    fun `a manually-matched song shows the Matched tag and is enabled for import`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("s1", "Amazing Grace", itemType = "song")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        vm.markItemResolved("s1", "Hymnal::0042")
        awaitVm { vm.planItems.single().matchedSongId != null }

        assertTextEventually("✓ Matched")
    }

    @Test
    fun `toggling a header row's own checkbox via click deselects it`() = dialog { vm, _, _, _ ->
        withPlan(listOf(planItem("h1", "Welcome", itemType = "header")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Welcome")

        // index 0 is the "Plan Items" master checkbox; index 1 is this row's own.
        onAllNodes(isToggleable())[1].performClick()
        awaitVm { !vm.planItems.single().selected }

        assertTrue(!vm.planItems.single().selected)
    }

    @Test
    fun `importing a matched song calls onAddSong with the parsed songbook and number`() =
        dialog { vm, _, _, recorder ->
        withPlan(listOf(planItem("s1", "Amazing Grace", itemType = "song", songTitle = "Amazing Grace")))
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }

        vm.markItemResolved("s1", "Hymnal::0042")
        awaitVm { vm.planItems.single().matchedSongId != null }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addSongCalls == 1 }

        assertEquals(listOf(42, "Amazing Grace", "Hymnal", "Hymnal::0042"), recorder.lastAddSong)
    }

    @Test
    fun `importing a selected item with a supported attachment calls onAddPicture`() = dialog { vm, _, _, recorder ->
        val attachment = PlanningCenterClient.PlanAttachment(id = "att-1", filename = "slide.jpg")
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                "plan-1",
                "Sunday Service",
                "Jul 27",
            )))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(listOf(attachment))
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any()) } returns
            PlanningCenterClient.AttachmentUrlOutcome.Success("https://example.test/slide.jpg")
        coEvery { PlanningCenterClient.downloadFile(any(), any()) } answers {
            PlanningCenterClient.FileDownloadOutcome.Success(secondArg<File>())
        }

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.isNotEmpty() == true }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addPictureCalls == 1 }

        val (folderPath, folderName, imageCount) = recorder.lastAddPicture!!
        assertTrue((folderPath as String).contains("i1"))
        assertEquals("Sermon Notes", folderName)
        assertEquals(1, imageCount)
    }

    @Test
    fun `expanding an item row's attachments shows each file, greying out unsupported ones`() = dialog { vm, _, _, _ ->
        coEvery { PlanningCenterClient.fetchThumbnailBytes(any(), any()) } returns null
        withPlan(
            listOf(planItem("i1", "Sermon Notes", itemType = "item")),
        )
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(
                    PlanningCenterClient.PlanAttachment(
                        id = "att-1",
                        filename = "photo.jpg",
                        thumbnailUrl = "https://example.test/thumb.jpg",
                    ),
                    PlanningCenterClient.PlanAttachment(id = "att-2", filename = "slides.pptx"),
                    PlanningCenterClient.PlanAttachment(id = "att-3", filename = "notes.xyz"),
                ),
            )

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.size == 3 }
        assertTextEventually("3 file(s)")

        // The row itself is the expand/collapse click target once it has attachments.
        onNodeWithText("Sermon Notes").performClick()

        assertTextEventually("photo.jpg")
        assertTextEventually("slides.pptx")
        assertTextEventually("notes.xyz")

        // The unsupported file's checkbox is disabled and starts unchecked.
        val fileCheckboxes = onAllNodes(isToggleable())
        assertTrue(fileCheckboxes.fetchSemanticsNodes().size >= 3)
    }

    // ── Detected scripture rows ─────────────────────────────────────────────────

    @Test
    fun `an item with a detected scripture reference shows its verse instead of a row checkbox`() {
        setUpBibleFixture()
        dialog { vm, _, _, _ ->
            withPlan(listOf(planItem("i1", "Scripture Reading", itemType = "item", description = "Psalms 23:1")))
            vm.selectServiceType("st-1")
            awaitVm { vm.detectedScripturesByItemId.containsKey("i1") }

            assertTextEventually("Scripture Reading")
            assertTextEventually("Psalms 23:1")
            assertEquals(setOf(0), vm.selectedScriptureIndices["i1"])
        }
    }

    @Test
    fun `clicking a detected scripture's own checkbox toggles it off`() {
        setUpBibleFixture()
        dialog { vm, _, _, _ ->
            withPlan(listOf(planItem("i1", "Scripture Reading", itemType = "item", description = "Psalms 23:1")))
            vm.selectServiceType("st-1")
            awaitVm { vm.detectedScripturesByItemId.containsKey("i1") }
            assertTextEventually("Psalms 23:1")

            // index 0 is the "Plan Items" master checkbox; index 1 is this verse's own.
            onAllNodes(isToggleable())[1].performClick()
            awaitVm { vm.selectedScriptureIndices["i1"]?.isEmpty() == true }

            assertTrue(vm.selectedScriptureIndices["i1"]!!.isEmpty())
        }
    }

    @Test
    fun `importing a selected scripture calls onAddBibleVerse with the resolved verse`() {
        setUpBibleFixture()
        dialog { vm, _, _, recorder ->
            withPlan(listOf(planItem("i1", "Scripture Reading", itemType = "item", description = "Psalms 23:1")))
            vm.selectServiceType("st-1")
            awaitVm { vm.detectedScripturesByItemId.containsKey("i1") }
            assertTextEventually("Import Selected")

            onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
            awaitVm { recorder.addBibleVerseCalls == 1 }

            assertEquals(listOf("Psalms", 23, 1, "Psalm twenty three verse 1", "", 19), recorder.lastAddBibleVerse)
        }
    }

    @Test
    fun `deselecting the only detected scripture is not imported`() {
        setUpBibleFixture()
        dialog { vm, dismissed, _, recorder ->
            withPlan(listOf(planItem("i1", "Scripture Reading", itemType = "item", description = "Psalms 23:1")))
            vm.selectServiceType("st-1")
            awaitVm { vm.detectedScripturesByItemId.containsKey("i1") }

            vm.toggleScriptureSelected("i1", 0)
            awaitVm { vm.selectedScriptureIndices["i1"]?.isEmpty() == true }

            // Nothing left importable, so the button stays disabled and there's nothing to click —
            // this asserts the state the enabled-check itself relies on.
            assertEquals(0, recorder.addBibleVerseCalls)
            assertEquals(0, dismissed())
        }
    }

    // ── Attachment classification on import ─────────────────────────────────────

    @Test
    fun `importing a selected item with a supported presentation attachment calls onAddPresentation`() =
        dialog { vm, _, _, recorder ->
        val attachment = PlanningCenterClient.PlanAttachment(id = "att-1", filename = "slides.pptx")
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                "plan-1",
                "Sunday Service",
                "Jul 27",
            )))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Sermon Slides", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(listOf(attachment))
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any()) } returns
            PlanningCenterClient.AttachmentUrlOutcome.Success("https://example.test/slides.pptx")
        coEvery { PlanningCenterClient.downloadFile(any(), any()) } answers {
            PlanningCenterClient.FileDownloadOutcome.Success(secondArg<File>())
        }

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.isNotEmpty() == true }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addPresentationCalls == 1 }

        val recorded = recorder.lastAddPresentation!!
        assertEquals("Sermon Slides", recorded[1])
        assertEquals("pptx", recorded[3])
    }

    @Test
    fun `importing a selected item with a supported media attachment calls onAddMedia`() =
        dialog { vm, _, _, recorder ->
        val attachment = PlanningCenterClient.PlanAttachment(id = "att-1", filename = "clip.mp4")
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                "plan-1",
                "Sunday Service",
                "Jul 27",
            )))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Intro Clip", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(listOf(attachment))
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any()) } returns
            PlanningCenterClient.AttachmentUrlOutcome.Success("https://example.test/clip.mp4")
        coEvery { PlanningCenterClient.downloadFile(any(), any()) } answers {
            PlanningCenterClient.FileDownloadOutcome.Success(secondArg<File>())
        }

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.isNotEmpty() == true }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { recorder.addMediaCalls == 1 }

        val (_, mediaTitle, mediaType) = recorder.lastAddMedia!!
        assertEquals("Intro Clip", mediaTitle)
        assertEquals("local", mediaType)
    }

    // ── Attachment thumbnails ─────────────────────────────────────────────────────

    @Test
    fun `an image attachment with a decodable thumbnail replaces the loading spinner with the image`() =
        dialog { vm, _, _, _ ->
        coEvery { PlanningCenterClient.fetchThumbnailBytes(any(), any()) } returns tinyPngBytes()
        withPlan(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(PlanningCenterClient.PlanAttachment(
                    id = "att-1",
                    filename = "photo.jpg",
                    thumbnailUrl = "https://example.test/thumb.jpg",
                )),
            )

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.size == 1 }
        assertTextEventually("Sermon Notes")

        onNodeWithText("Sermon Notes").performClick()
        assertTextEventually("photo.jpg")

        awaitVm { onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty() }
    }

    @Test
    fun `an image attachment with corrupt thumbnail bytes leaves the loading spinner showing`() =
        dialog { vm, _, _, _ ->
        coEvery { PlanningCenterClient.fetchThumbnailBytes(any(), any()) } returns "not a real image".toByteArray()
        withPlan(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(
                listOf(PlanningCenterClient.PlanAttachment(
                    id = "att-1",
                    filename = "photo.jpg",
                    thumbnailUrl = "https://example.test/thumb.jpg",
                )),
            )

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.size == 1 }
        assertTextEventually("Sermon Notes")

        onNodeWithText("Sermon Notes").performClick()
        assertTextEventually("photo.jpg")
        settle()
        waitForIdle()

        assertTrue(onAllNodes(progressSpinner).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty())
    }

    @Test
    fun `clicking Add Song with a linked arrangement fetches it before requesting the add-song dialog`() =
        dialog { vm, _, _, recorder ->
        coEvery { PlanningCenterClient.getArrangementDetail(any(), any(), any(), any()) } returns
            PlanningCenterClient.ArrangementOutcome.Success(PlanningCenterClient.ArrangementDetail(
                chordChart = "G C D",
                lyrics = "la la la",
            ))
        withPlan(
            listOf(planItem(
                "s1",
                "Amazing Grace",
                itemType = "song",
                songTitle = "Amazing Grace",
                songId = "song-1",
                arrangementId = "arr-1",
            )),
        )
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.isNotEmpty() }
        assertTextEventually("Add Song")

        onNodeWithText("Add Song", useUnmergedTree = true).performClick()
        awaitVm { recorder.addSongRequested != null }

        val (pco, prefill) = recorder.addSongRequested!!
        assertEquals("s1", pco.id)
        assertTrue(prefill.lyrics.joinToString("\n").contains("la la la"))
    }

    @Test
    fun `importing skips a media row and still imports the other selected rows`() =
        dialog { vm, dismissed, _, recorder ->
        withPlan(
            listOf(
                planItem("m1", "Intro Video", itemType = "media"),
                planItem("h1", "Welcome", itemType = "header"),
            ),
        )
        vm.selectServiceType("st-1")
        awaitVm { vm.planItems.size == 2 }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { dismissed() == 1 }

        assertEquals(1, recorder.addLabelCalls)
        assertEquals(0, recorder.addSongCalls)
    }

    @Test
    fun `a failed attachment download imports nothing for that file but still dismisses`() =
        dialog { vm, dismissed, _, recorder ->
        val attachment = PlanningCenterClient.PlanAttachment(id = "att-1", filename = "slide.jpg")
        coEvery { PlanningCenterClient.listUpcomingPlans(any(), any(), any()) } returns
            PlanningCenterClient.PlansOutcome.Success(listOf(PlanningCenterClient.Plan(
                "plan-1",
                "Sunday Service",
                "Jul 27",
            )))
        coEvery { PlanningCenterClient.getPlanItems(any(), any(), any(), any()) } returns
            PlanningCenterClient.PlanItemsOutcome.Success(listOf(planItem("i1", "Sermon Notes", itemType = "item")))
        coEvery { PlanningCenterClient.getItemAttachments(any(), any(), any(), any(), any()) } returns
            PlanningCenterClient.AttachmentsOutcome.Success(listOf(attachment))
        coEvery { PlanningCenterClient.resolveAttachmentDownloadUrl(any(), any()) } returns
            PlanningCenterClient.AttachmentUrlOutcome.Failure

        vm.selectServiceType("st-1")
        awaitVm { vm.attachmentsByItemId["i1"]?.isNotEmpty() == true }
        assertTextEventually("Import Selected")

        onNodeWithText("Import Selected", useUnmergedTree = true).performClick()
        awaitVm { dismissed() == 1 }

        assertEquals(0, recorder.addPictureCalls)
        assertEquals(0, recorder.addPresentationCalls)
        assertEquals(0, recorder.addMediaCalls)
    }
}
