package org.churchpresenter.slides.tabs

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.testing.RecentFilesSwap
import org.churchpresenter.sharedui.testing.showsExactly
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.awaitDeck
import org.churchpresenter.slides.data.RecentPresentationFiles
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.tempDir
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class PresentationTabOptionsTest {

    private val dir = tempDir("cp-presentation-options")
    private val swap = RecentFilesSwap(
        readPaths = { RecentPresentationFiles.file to RecentPresentationFiles.pinnedFile },
        writePaths = { f, p -> RecentPresentationFiles.file = f; RecentPresentationFiles.pinnedFile = p },
        entries = RecentPresentationFiles.files,
        pinned = RecentPresentationFiles.pinned,
        prefix = "cp-presentation-options-recent",
    )

    @BeforeTest
    fun setUp() = swap.install()

    @AfterTest
    fun tearDown() {
        swap.restore()
        dir.deleteRecursively()
    }

    private class Calls {
        val scheduled = mutableListOf<String>()
        val presets = mutableListOf<String>()
        val projected = mutableListOf<ScheduleItem>()
        val loaded = mutableListOf<Triple<String, String, Int>>()
        var freezes = 0
        var clears = 0
        var nextSent = 0
        var previousSent = 0
    }

    private class Harness(val vm: PresentationViewModel, val calls: Calls, val output: FakeSlidesOutput) {
        var item by mutableStateOf<ScheduleItem.PresentationItem?>(null)
        var version by mutableIntStateOf(0)
    }

    private fun tab(
        output: FakeSlidesOutput = FakeSlidesOutput(),
        frozen: Boolean = false,
        remoteDialog: @Composable (() -> Unit) -> Unit = {},
        block: ComposeUiTest.(Harness) -> Unit,
    ) {
        val settings = AppSettings()
        val vm = PresentationViewModel(settings)
        val calls = Calls()
        val harness = Harness(vm, calls, output)
        try {
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                        PresentationTab(
                            modifier = Modifier,
                            hostWindow = null,
                            appSettings = settings,
                            onAddToSchedule = { p, n, c, t -> calls.scheduled += "$p:$n:$c:$t" },
                            onSavePreset = { p, n, c, t -> calls.presets += "$p:$n:$c:$t" },
                            onInstanceLinkSendProject = { calls.projected += it },
                            onInstanceLinkSendNextSlide = { calls.nextSent++ },
                            onInstanceLinkSendPreviousSlide = { calls.previousSent++ },
                            instanceLinkFetchPresentationSlideBytes = { _, _ -> null },
                            selectedPresentationItem = harness.item,
                            selectedPresentationItemVersion = harness.version,
                            presenterManager = output,
                            onSlidesLoaded = { id, path, name, type, files, notes ->
                                calls.loaded += Triple("$id|$path|$name|$type", "${notes.size}", files.size)
                            },
                            onSettingsChange = {},
                            viewModel = vm,
                            remoteDialog = remoteDialog,
                            presentationFrozen = frozen,
                            onFreezeToggle = { calls.freezes++ },
                            onClearPresentation = { calls.clears++ },
                            vlcAvailable = true,
                            vlcArchMismatch = false,
                            vlcLoadFailed = false,
                        )
                    }
                }
                block(harness)
            }
        } finally {
            runCatching { vm.dispose() }
        }
    }

    private fun ComposeUiTest.loadDeck(h: Harness, pages: Int = 3): File {
        val file = pdfDeck(dir, pages)
        h.vm.addPresentation(file)
        awaitDeck(h.vm)
        return file
    }

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `with every option given and nothing loaded the tab asks for a file`() = tab { _ ->
        assertTrue(showsExactly(PresentationLabel.NO_FILE))
    }

    @Test
    fun `the remote button shows the app's dialog and its dismiss closes it`() = tab(
        remoteDialog = { onDismiss -> TextButton(onClick = onDismiss) { Text("Close remote") } },
    ) { _ ->
        presentationButton(PresentationLabel.REMOTE).performClick()
        waitForIdle()
        onNodeWithText("Close remote").performClick()
        waitForIdle()
        onNodeWithText("Close remote").assertDoesNotExist()
    }

    @Test
    fun `a loaded deck is reported to the host with its slides`() = tab { h ->
        val file = loadDeck(h, pages = 3)
        waitUntil("the slides reported", 5_000) { h.calls.loaded.isNotEmpty() }
        val (identity, _, count) = h.calls.loaded.last()
        assertTrue(identity.contains(file.absolutePath))
        assertTrue(identity.endsWith("|pdf"))
        assertEquals(3, count)
    }

    @Test
    fun `save preset reports the loaded deck`() = tab { h ->
        val file = loadDeck(h, pages = 2)
        presentationButton("Save preset").performClick()
        waitForIdle()
        assertEquals(listOf("${file.absolutePath}:${file.nameWithoutExtension}:2:pdf"), h.calls.presets)
    }

    @Test
    fun `save preset does nothing with no deck open`() = tab { h ->
        presentationButton("Save preset").performClick()
        waitForIdle()
        assertTrue(h.calls.presets.isEmpty())
    }

    @Test
    fun `a schedule item for a deck on this machine opens it`() = tab { h ->
        val file = pdfDeck(dir, 2, "scheduled.pdf")
        h.item = ScheduleItem.PresentationItem("p1", file.absolutePath, file.nameWithoutExtension, 2, "pdf")
        awaitDeck(h.vm)
        assertEquals(file.absolutePath, h.vm.selectedPresentation?.absolutePath)
    }

    @Test
    fun `clicking the same schedule item again reloads it`() = tab { h ->
        val file = pdfDeck(dir, 2, "again.pdf")
        h.item = ScheduleItem.PresentationItem("p1", file.absolutePath, file.nameWithoutExtension, 2, "pdf")
        awaitDeck(h.vm)
        val before = h.vm.loadGeneration
        h.version++
        waitUntil("the deck reloaded", 10_000) { h.vm.loadGeneration > before && !h.vm.isLoading }
    }

    @Test
    fun `a right arrow that plays a build stays on the slide`() = tab { h ->
        loadDeck(h)
        h.output.stepsAhead = 1
        press(Key.DirectionRight)
        assertEquals(0, h.vm.selectedSlideIndex)
        assertEquals(0, h.output.stepsAhead)
    }

    @Test
    fun `a left arrow that rewinds a build stays on the slide`() = tab { h ->
        loadDeck(h)
        h.vm.selectSlide(1)
        waitForIdle()
        h.output.stepsBehind = 1
        press(Key.DirectionLeft)
        assertEquals(1, h.vm.selectedSlideIndex)
        assertEquals(0, h.output.stepsBehind)
    }

    @Test
    fun `with no build left the arrows change slides and tell Instance Link`() = tab { h ->
        loadDeck(h)
        press(Key.DirectionRight)
        assertEquals(1, h.vm.selectedSlideIndex)
        press(Key.DirectionLeft)
        assertEquals(0, h.vm.selectedSlideIndex)
        assertEquals(1, h.calls.nextSent)
        assertEquals(1, h.calls.previousSent)
    }

    @Test
    fun `a key the tab does not use changes nothing`() = tab { h ->
        loadDeck(h)
        press(Key.Q)
        assertEquals(0, h.vm.selectedSlideIndex)
        assertEquals(0, h.calls.freezes)
    }

    @Test
    fun `the blank key while the deck is live toggles the freeze`() = tab { h ->
        loadDeck(h)
        h.output.setPresentingMode(Presenting.PRESENTATION)
        press(Key.B)
        assertEquals(1, h.calls.freezes)
    }

    @Test
    fun `auto-play moves to the next slide on its own`() = tab { h ->
        loadDeck(h)
        h.vm.autoScrollInterval = 1f
        h.vm.togglePlayPause()
        mainClock.advanceTimeBy(1_500)
        waitUntil("the next slide", 5_000) { h.vm.selectedSlideIndex == 1 }
    }

    @Test
    fun `auto-play plays a build before it leaves the slide`() = tab { h ->
        loadDeck(h)
        h.output.stepsAhead = 1
        h.vm.autoScrollInterval = 1f
        h.vm.togglePlayPause()
        mainClock.advanceTimeBy(1_500)
        waitUntil("the build played", 5_000) { h.output.stepsAhead == 0 }
        assertEquals(0, h.vm.selectedSlideIndex)
    }

    @Test
    fun `a screen locked to the deck is sent the slide that is selected`() = tab { h ->
        loadDeck(h)
        h.output.setScreenLock(1, Presenting.PRESENTATION)
        h.vm.selectSlide(1)
        waitUntil("the slide sent to the locked screen", 5_000) { h.output.liveSlide.value?.second == 1 }
        assertNotNull(h.output.selectedSlide.value)
    }

    @Test
    fun `hiding a slide while a screen is locked refreshes what comes next`() = tab { h ->
        loadDeck(h)
        h.output.setScreenLock(1, Presenting.PRESENTATION)
        waitForIdle()
        h.output.nextSlide.value = null
        h.vm.toggleSlideHidden(1)
        waitUntil("the next slide refreshed", 5_000) { h.output.nextSlide.value != null }
    }

    @Test
    fun `the transition settings reach the output`() = tab { h ->
        waitForIdle()
        assertEquals(h.vm.animationType, h.output.animationType.value)
        assertEquals(h.vm.transitionDuration.toInt(), h.output.transitionDuration.value)
    }

    @Test
    fun `a frozen output offers to unblank`() = tab(frozen = true) { _ ->
        presentationButton(PresentationLabel.UNBLANK_OUTPUT).assertExists()
    }

    @Test
    fun `go live puts the slide, its notes and the deck on the output`() = tab { h ->
        val file = loadDeck(h)
        presentationButton(PresentationLabel.GO_LIVE).performClick()
        waitUntil("the slide on the output", 5_000) { h.output.selectedSlide.value != null }
        assertEquals(Presenting.PRESENTATION, h.output.presentingMode.value)
        assertTrue(h.output.showPresenterWindow.value)
        assertEquals(file.name to 0, h.output.liveSlide.value)
        assertEquals(listOf(0), h.output.shownSlides)
        assertEquals(1, h.calls.projected.size)
    }

    @Test
    fun `clear empties the tab and tells the host`() = tab { h ->
        loadDeck(h)
        presentationButton(PresentationLabel.CLEAR).performClick()
        waitForIdle()
        assertTrue(h.vm.slideFiles.isEmpty())
        assertEquals(1, h.calls.clears)
    }

    @Test
    fun `a recent deck's chip opens it`() {
        val file = pdfDeck(dir, 2, "recent.pdf")
        RecentPresentationFiles.add(file.absolutePath)
        tab { h ->
            onNodeWithText("recent.pdf").performClick()
            awaitDeck(h.vm)
            assertEquals(file.absolutePath, h.vm.selectedPresentation?.absolutePath)
        }
    }

    @Test
    fun `a recent deck that has gone missing opens nothing`() {
        RecentPresentationFiles.add(File(dir, "gone.pdf").absolutePath)
        tab { h ->
            onNodeWithText("gone.pdf").performClick()
            waitForIdle()
            assertFalse(h.vm.isLoading)
            assertTrue(h.vm.presentations.isEmpty())
        }
    }

    @Test
    fun `the recent decks' clear button empties the row`() {
        RecentPresentationFiles.add(pdfDeck(dir, 1, "first.pdf").absolutePath)
        tab { _ ->
            onNodeWithContentDescription("Clear").performClick()
            waitForIdle()
            assertTrue(RecentPresentationFiles.files.isEmpty())
        }
    }

    @Test
    fun `pinning a recent deck keeps it`() {
        val path = pdfDeck(dir, 1, "pinned.pdf").absolutePath
        RecentPresentationFiles.add(path)
        tab { _ ->
            onNodeWithContentDescription("Pin").performClick()
            waitForIdle()
            assertEquals(listOf(path), RecentPresentationFiles.pinned.toList())
        }
    }
}
