package org.churchpresenter.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.sharedui.screenshot.PinnedFonts
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The line a drag over the schedule shows where it will land, and the list telling the drop its slots. */
@OptIn(ExperimentalTestApi::class)
class ScheduleDropMarkTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private lateinit var vm: ScheduleViewModel

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-drop-mark").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        vm = ScheduleViewModel()
    }

    @AfterTest
    fun restoreHome() {
        runCatching { vm.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private val hint = "Drag files here to add to schedule"

    private fun rowList(drop: ScheduleFileDrop, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            PinnedFonts {
                MaterialTheme {
                    Box(Modifier.size(320.dp, 480.dp)) {
                        ScheduleRowList(
                            viewModel = vm,
                            look = ScheduleRowLook(ScheduleDensity.NORMAL, false, emptyMap(), null),
                            onRowClick = {},
                            onPresent = {},
                            onEditLabel = {},
                            fileDrop = drop,
                        )
                    }
                }
            }
        }
        block()
    }

    @Test
    fun `a drag over rows marks its slot and leaves the callout off`() {
        vm.addSong(1, "Amazing Grace", "Hymnal")
        vm.addSong(2, "Be Thou My Vision", "Hymnal")
        val drop = ScheduleFileDrop { vm }
        rowList(drop) {
            drop.dragOver = true
            drop.slot = DropSlot(1, 60f)
            waitForIdle()
            onNodeWithTag(SCHEDULE_DROP_MARK_TAG).assertExists()
            assertEquals(0, onAllNodesWithText(hint).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the mark moves with the slot`() {
        vm.addSong(1, "Amazing Grace", "Hymnal")
        val drop = ScheduleFileDrop { vm }
        rowList(drop) {
            drop.dragOver = true
            drop.slot = DropSlot(0, 0f)
            waitForIdle()
            val top = onNodeWithTag(SCHEDULE_DROP_MARK_TAG).getBoundsInRoot().top
            drop.slot = DropSlot(1, 80f)
            waitForIdle()
            val lower = onNodeWithTag(SCHEDULE_DROP_MARK_TAG).getBoundsInRoot().top
            assertTrue(lower > top, "the mark follows the slot down the list")
        }
    }

    @Test
    fun `an empty schedule shows the callout and no mark`() {
        val drop = ScheduleFileDrop { vm }
        rowList(drop) {
            drop.dragOver = true
            drop.slot = DropSlot(0, 0f)
            waitForIdle()
            assertEquals(1, onAllNodesWithText(hint).fetchSemanticsNodes().size)
            assertEquals(0, onAllNodesWithTagCount(SCHEDULE_DROP_MARK_TAG))
        }
    }

    @Test
    fun `no drag, no mark`() {
        vm.addSong(1, "Amazing Grace", "Hymnal")
        val drop = ScheduleFileDrop { vm }
        rowList(drop) {
            waitForIdle()
            assertEquals(0, onAllNodesWithTagCount(SCHEDULE_DROP_MARK_TAG))
        }
    }

    @Test
    fun `the list tells the drop which slot a window position is`() {
        vm.addSong(1, "Amazing Grace", "Hymnal")
        vm.addSong(2, "Be Thou My Vision", "Hymnal")
        val drop = ScheduleFileDrop { vm }
        rowList(drop) {
            waitForIdle()
            assertEquals(0, assertNotNull(drop.slotAt(Offset(10f, 0f))).index, "above every row: the top")
            assertEquals(2, assertNotNull(drop.slotAt(Offset(10f, 10_000f))).index, "below every row: the end")
        }
    }

    private fun ComposeUiTest.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size
}
