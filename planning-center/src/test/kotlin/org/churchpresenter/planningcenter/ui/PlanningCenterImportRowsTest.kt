@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.planningcenter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.coEvery
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.planningcenter.PcoItemType
import org.churchpresenter.planningcenter.PlanningCenterClient
import org.churchpresenter.planningcenter.PlanningCenterClient.PlanAttachment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One plan row of the import picker, drawn on its own over a selection set up by hand: what each
 * kind of row offers, and that each of its checkboxes changes exactly the selection it stands for.
 */
class PlanningCenterImportRowsTest {

    @BeforeTest
    fun stubClient() {
        mockkObject(PlanningCenterClient)
        coEvery { PlanningCenterClient.fetchThumbnailBytes(any(), any()) } returns null
    }

    @AfterTest
    fun unstubClient() = unmockkObject(PlanningCenterClient)

    private val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    private fun item(id: String, type: String, title: String) =
        PlanningCenterClient.PlanItem(id = id, title = title, itemType = type, sequence = 0)

    private fun viewModel(
        row: PlanningCenterImportViewModel.ImportPlanItem,
        scriptures: List<PlanningCenterScripture> = emptyList(),
        files: List<PlanAttachment>? = null,
    ): PlanningCenterImportViewModel {
        val id = row.pco.id
        val selection = PlanSelection().apply {
            planItems = listOf(row)
            if (scriptures.isNotEmpty()) {
                detectedScripturesByItemId = mapOf(id to scriptures)
                selectedScriptureIndices = mapOf(id to scriptures.indices.toSet())
            }
            if (files != null) {
                attachmentsByItemId = mapOf(id to files)
                selectedAttachmentIds = mapOf(id to files.map { it.id }.toSet())
            }
        }
        return PlanningCenterImportViewModel(
            initialAccessToken = "token",
            initialRefreshToken = "refresh",
            initialExpiresAtEpochMs = System.currentTimeMillis() + 3_600_000,
            initialServiceTypeId = "st",
            importSongbookName = "",
            onTokensRefreshed = { _, _, _ -> },
            selection = selection,
        )
    }

    private fun showRow(
        vm: PlanningCenterImportViewModel,
        fetching: String? = null,
        onAddSong: (PlanningCenterClient.PlanItem) -> Unit = {},
        check: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            MaterialTheme { Column { PcoPlanItemRow(vm.planItems.single(), vm, fetching, onAddSong) } }
        }
        check()
    }

    private fun PlanningCenterImportViewModel.row() = planItems.single()

    @Test
    fun `a header's checkbox ticks the header in and out`() {
        val vm = viewModel(PlanningCenterImportViewModel.ImportPlanItem(item("h", PcoItemType.HEADER, "Worship")))
        showRow(vm) {
            onNodeWithText("Worship").assertExists()
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertFalse(vm.row().selected)
        }
    }

    @Test
    fun `a media row can never be ticked`() {
        val vm = viewModel(PlanningCenterImportViewModel.ImportPlanItem(item("m", PcoItemType.MEDIA, "Video")))
        showRow(vm) { onAllNodes(isToggleable())[0].assertIsNotEnabled() }
    }

    @Test
    fun `a matched song's checkbox ticks it in and out`() {
        val vm = viewModel(
            PlanningCenterImportViewModel.ImportPlanItem(item("s", PcoItemType.SONG, "Grace"), matchedSongId = "b::1"),
        )
        showRow(vm) {
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertFalse(vm.row().selected)
        }
    }

    @Test
    fun `a song whose arrangement is being fetched shows a spinner instead of Add Song`() {
        val vm = viewModel(PlanningCenterImportViewModel.ImportPlanItem(item("s", PcoItemType.SONG, "Grace")))
        showRow(vm, fetching = "s") {
            onAllNodes(progress).fetchSemanticsNodes().let { assertEquals(1, it.size, "one spinner") }
            onNodeWithText("Add Song", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `an unmatched song asks for the song to be added`() {
        val vm = viewModel(PlanningCenterImportViewModel.ImportPlanItem(item("s", PcoItemType.SONG, "Grace")))
        var asked: PlanningCenterClient.PlanItem? = null
        showRow(vm, onAddSong = { asked = it }) {
            onNodeWithText("Add Song", substring = true).performClick()
            assertEquals("s", asked?.id)
        }
    }

    @Test
    fun `an item whose files are still loading shows a spinner, and its checkbox ticks it out`() {
        val vm = viewModel(PlanningCenterImportViewModel.ImportPlanItem(item("i", PcoItemType.ITEM, "Notes")))
        showRow(vm) {
            assertEquals(1, onAllNodes(progress).fetchSemanticsNodes().size)
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
            assertFalse(vm.row().selected)
        }
    }

    @Test
    fun `an item with files opens to list them, and each file ticks in and out on its own`() {
        val files = listOf(
            PlanAttachment("a", "deck.pptx"),
            PlanAttachment("b", "photo.jpg", thumbnailUrl = "https://thumbs.test/photo.jpg"),
            PlanAttachment("c", "notes.xyz"),
        )
        val vm = viewModel(
            PlanningCenterImportViewModel.ImportPlanItem(item("i", PcoItemType.ITEM, "Sermon")),
            files = files,
        )
        showRow(vm) {
            onNodeWithText("deck.pptx").assertDoesNotExist()
            onNodeWithText("Sermon").performClick()
            waitForIdle()
            onNodeWithText("deck.pptx").assertExists()
            onNodeWithText("notes.xyz").assertExists()
            // Row checkbox, then one per file in order.
            val boxes = onAllNodes(isToggleable())
            boxes[3].assertIsNotEnabled()
            boxes[1].performClick()
            waitForIdle()
            assertEquals(setOf("b", "c"), vm.selectedAttachmentIds["i"])
            onNodeWithText("Sermon").performClick()
            waitForIdle()
            onNodeWithText("deck.pptx").assertDoesNotExist()
        }
    }

    @Test
    fun `an item with no files has nothing to open`() {
        val vm = viewModel(
            PlanningCenterImportViewModel.ImportPlanItem(item("i", PcoItemType.ITEM, "Bare")),
            files = emptyList(),
        )
        showRow(vm) {
            assertEquals(0, onAllNodes(progress).fetchSemanticsNodes().size, "files are known, so no spinner")
            onNodeWithText("Bare").performClick()
            waitForIdle()
            assertEquals(1, onAllNodes(isToggleable()).fetchSemanticsNodes().size, "still only the row's checkbox")
        }
    }

    @Test
    fun `an item with scripture is chosen verse by verse, not by a row checkbox`() {
        val verses = listOf(
            PlanningCenterScripture("Psalms", 19, 23, 1, "One", ""),
            PlanningCenterScripture("John", 43, 3, 16, "Two", ""),
        )
        val vm = viewModel(
            PlanningCenterImportViewModel.ImportPlanItem(item("i", PcoItemType.ITEM, "Readings")),
            scriptures = verses,
        )
        showRow(vm) {
            val boxes = onAllNodes(isToggleable())
            assertEquals(2, boxes.fetchSemanticsNodes().size, "one per verse and none for the row")
            onNodeWithText("John 3:16").performClick()
            waitForIdle()
            assertEquals(setOf(0), vm.selectedScriptureIndices["i"])
            assertTrue(vm.row().selected, "the row's own selection is untouched")
        }
    }
}
