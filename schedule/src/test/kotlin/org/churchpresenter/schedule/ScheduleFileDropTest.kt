package org.churchpresenter.schedule

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class ScheduleFileDropTest {

    private lateinit var tempHome: File
    private var realHome: String? = null
    private lateinit var viewModel: ScheduleViewModel

    @BeforeTest
    fun isolateHome() {
        realHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("cp-schedule-file-drop-test").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        viewModel = ScheduleViewModel()
    }

    @AfterTest
    fun restoreHome() {
        runCatching { viewModel.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    private val unreadable = DragAndDropEvent(DragAndDropTransferAction.Copy, null, Offset.Zero)

    @Test
    fun `the panel lights up while a drag is over it and goes dark when it leaves or ends`() {
        val drop = ScheduleFileDrop { viewModel }

        drop.onEntered(unreadable)
        assertTrue(drop.dragOver)
        drop.onExited(unreadable)
        assertFalse(drop.dragOver)

        drop.onEntered(unreadable)
        drop.onEnded(unreadable)
        assertFalse(drop.dragOver)
    }

    @Test
    fun `a drag whose data cannot be read is not taken`() {
        val drop = ScheduleFileDrop { viewModel }

        assertFalse(drop.accepts(unreadable))
    }

    @Test
    fun `a drop whose data cannot be read adds nothing and reports nothing`() {
        val drop = ScheduleFileDrop { viewModel }
        drop.onEntered(unreadable)

        assertFalse(drop.onDrop(unreadable))

        assertFalse(drop.dragOver)
        assertTrue(drop.skippedFiles.isEmpty())
        assertTrue(viewModel.scheduleItems.isEmpty())
    }
}
