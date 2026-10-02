package org.churchpresenter.slides.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.RecentFilesSwap
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PicturesTabOptionsTest {

    private val dir = tempDir("cp-pictures-options")
    private val swap = RecentFilesSwap(
        readPaths = { RecentPictureFolders.file to RecentPictureFolders.pinnedFile },
        writePaths = { f, p -> RecentPictureFolders.file = f; RecentPictureFolders.pinnedFile = p },
        entries = RecentPictureFolders.folders,
        pinned = RecentPictureFolders.pinned,
        prefix = "cp-pictures-options-recent",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() {
        swap.restore()
        dir.deleteRecursively()
    }

    private fun folder(name: String = "Sunday", count: Int = 3): File =
        File(dir, name).apply {
            mkdirs()
            repeat(count) { solidImage(this, "pic$it.png", Color(it * 60, 0, 255 - it * 60)) }
        }

    private class Calls {
        val scheduled = mutableListOf<Triple<String, String, Int>>()
        val presets = mutableListOf<Triple<String, String, Int>>()
        val projected = mutableListOf<ScheduleItem>()
        var settingsChanges = 0
        var nextSent = 0
        var previousSent = 0
    }

    private class Harness(val vm: PicturesViewModel, val calls: Calls, val output: FakeSlidesOutput) {
        var item by mutableStateOf<ScheduleItem.PictureItem?>(null)
        var version by mutableIntStateOf(0)
    }

    private fun tab(
        startFolder: File? = folder(),
        output: FakeSlidesOutput = FakeSlidesOutput(),
        fetch: (suspend (String, Int) -> ByteArray?)? = null,
        block: ComposeUiTest.(Harness) -> Unit,
    ) {
        val settings = AppSettings(
            pictureSettings = PictureSettings(storageDirectory = startFolder?.absolutePath ?: ""),
        )
        val vm = PicturesViewModel(settings)
        startFolder?.let { vm.selectFolder(it) }
        val calls = Calls()
        val harness = Harness(vm, calls, output)
        try {
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                        PicturesTab(
                            modifier = Modifier,
                            hostWindow = null,
                            appSettings = settings,
                            onAddToSchedule = { p, n, c -> calls.scheduled += Triple(p, n, c) },
                            onSavePreset = { p, n, c -> calls.presets += Triple(p, n, c) },
                            onInstanceLinkSendProject = { calls.projected += it },
                            onInstanceLinkSendNextPicture = { calls.nextSent++ },
                            onInstanceLinkSendPreviousPicture = { calls.previousSent++ },
                            instanceLinkFetchPictureImageBytes = fetch,
                            selectedPictureItem = harness.item,
                            selectedPictureItemVersion = harness.version,
                            presenterManager = output,
                            onSettingsChange = { calls.settingsChanges++ },
                            viewModel = vm,
                        )
                    }
                }
                block(harness)
            }
        } finally {
            runCatching { vm.dispose() }
        }
    }

    @Test
    fun `with every option given the tab shows the folder's pictures`() = tab { h ->
        waitUntil("the pictures listed", 5_000) { h.vm.images.size == 3 }
    }

    @Test
    fun `save preset reports the folder`() = tab { h ->
        pictureButton("Save preset").performClick()
        waitForIdle()
        val (path, name, count) = h.calls.presets.single()
        assertTrue(path.endsWith("Sunday"))
        assertEquals("Sunday", name)
        assertEquals(3, count)
    }

    @Test
    fun `save preset does nothing with no folder open`() = tab(startFolder = null) { h ->
        pictureButton("Save preset").performClick()
        waitForIdle()
        assertTrue(h.calls.presets.isEmpty())
    }

    @Test
    fun `go live puts the picture and the one after it on the output`() = tab { h ->
        pictureButton(PictureLabel.GO_LIVE).performClick()
        waitForIdle()
        assertEquals(Presenting.PICTURES, h.output.presentingMode.value)
        assertEquals(h.vm.images[0].absolutePath, h.output.selectedImagePath.value)
        assertEquals(h.vm.images[1].absolutePath, h.output.nextImagePath.value)
        assertEquals(1, h.calls.projected.size)
    }

    @Test
    fun `double-clicking a picture takes it live`() = tab { h ->
        waitUntil("two thumbnails drawn", 5_000) { drawnThumbnails().size >= 2 }
        onNodeWithContentDescription(drawnThumbnails()[1]).performTouchInput {
            down(center); up(); advanceEventTime(50); down(center); up()
        }
        waitForIdle()
        assertEquals(1, h.vm.selectedImageIndex)
        assertEquals(Presenting.PICTURES, h.output.presentingMode.value)
    }

    @Test
    fun `a picture selected while live is kept on the output`() = tab { h ->
        h.output.setPresentingMode(Presenting.PICTURES)
        h.vm.selectImage(2)
        waitUntil("the output follows", 5_000) { h.output.selectedImagePath.value == h.vm.images[2].absolutePath }
        assertNull(h.output.nextImagePath.value)
    }

    @Test
    fun `a screen locked to pictures is kept in step too`() = tab { h ->
        h.output.setScreenLock(2, Presenting.PICTURES)
        h.vm.selectImage(1)
        waitUntil("the locked screen follows", 5_000) {
            h.output.selectedImagePath.value == h.vm.images[1].absolutePath
        }
    }

    @Test
    fun `the transition settings reach the output`() = tab { h ->
        waitForIdle()
        assertEquals(h.vm.animationType, h.output.animationType.value)
        assertEquals(h.vm.transitionDuration.toInt(), h.output.transitionDuration.value)
    }

    @Test
    fun `auto-play steps to the next picture on its own`() = tab { h ->
        h.vm.autoScrollInterval = 1f
        h.vm.togglePlayPause()
        mainClock.advanceTimeBy(1_500)
        waitUntil("the next picture", 5_000) { h.vm.selectedImageIndex == 1 }
    }

    @Test
    fun `a scheduled folder on this machine opens`() = tab(startFolder = null) { h ->
        val scheduled = folder("Scheduled", 2)
        h.item = ScheduleItem.PictureItem("p1", scheduled.absolutePath, scheduled.name, 2)
        waitUntil("the folder opened", 5_000) { h.vm.images.size == 2 }
    }

    @Test
    fun `a scheduled folder from another machine is fetched over Instance Link`() {
        val source = folder("Remote", 2)
        val bytes = source.listFiles()!!.sortedBy { it.name }.map { it.readBytes() }
        tab(startFolder = null, fetch = { _, index -> bytes.getOrNull(index) }) { h ->
            h.item = ScheduleItem.PictureItem("p1", "/elsewhere/Remote", "Remote", 2)
            waitUntil("the remote pictures arrived", 10_000) { h.vm.images.size == 2 }
        }
    }

    @Test
    fun `a scheduled folder that is missing and cannot be fetched opens nothing`() = tab(startFolder = null) { h ->
        h.item = ScheduleItem.PictureItem("p1", "/no/such/folder", "Missing", 4)
        waitForIdle()
        assertTrue(h.vm.images.isEmpty())
    }

    @Test
    fun `a file that is not really a picture is marked unreadable`() {
        val broken = folder("Broken", 1)
        File(broken, "broken.png").writeText("not a picture")
        tab(startFolder = broken) { h ->
            waitUntil("the failure recorded", 10_000) { h.vm.thumbnailFailures.isNotEmpty() }
            waitUntil("the failure shown", 5_000) {
                onAllNodesWithText("Unreadable image").fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    @Test
    fun `the loop button turns looping on and saves it`() = tab { h ->
        val before = h.vm.isLooping
        onNodeWithContentDescription(if (before) "Loop On" else "Loop Off").performClick()
        waitForIdle()
        assertEquals(!before, h.vm.isLooping)
        assertTrue(h.calls.settingsChanges > 0)
    }

    @Test
    fun `hovering the play button names what it does`() = tab { _ ->
        mainClock.autoAdvance = false
        pictureButton(PictureLabel.PLAY).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        assertTrue(onAllNodesWithText(PictureLabel.PLAY).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a recent folder's chip opens it`() {
        val recent = folder("Recent", 2)
        RecentPictureFolders.add(recent.absolutePath)
        tab(startFolder = null) { h ->
            onNodeWithText("Recent").performClick()
            waitUntil("the folder opened", 5_000) { h.vm.images.size == 2 }
            assertTrue(h.calls.settingsChanges > 0)
        }
    }

    @Test
    fun `a recent folder that has gone missing opens nothing`() {
        RecentPictureFolders.add(File(dir, "Gone").absolutePath)
        tab(startFolder = null) { h ->
            onNodeWithText("Gone").performClick()
            waitForIdle()
            assertTrue(h.vm.images.isEmpty())
        }
    }

    @Test
    fun `pinning a recent folder keeps it`() {
        val path = folder("Pinned", 1).absolutePath
        RecentPictureFolders.add(path)
        tab(startFolder = null) { _ ->
            onNodeWithContentDescription("Pin").performClick()
            waitForIdle()
            assertEquals(listOf(path), RecentPictureFolders.pinned.toList())
        }
    }

    @Test
    fun `the recent folders' clear button empties the row`() {
        RecentPictureFolders.add(folder("Old", 1).absolutePath)
        tab(startFolder = null) { _ ->
            onNodeWithContentDescription("Clear").performClick()
            waitForIdle()
            assertTrue(RecentPictureFolders.folders.isEmpty())
        }
    }

    @Test
    fun `next and previous tell Instance Link`() = tab { h ->
        pictureButton(PictureLabel.NEXT).performClick()
        pictureButton(PictureLabel.PREVIOUS).performClick()
        waitForIdle()
        assertEquals(1, h.calls.nextSent)
        assertEquals(1, h.calls.previousSent)
    }
}
