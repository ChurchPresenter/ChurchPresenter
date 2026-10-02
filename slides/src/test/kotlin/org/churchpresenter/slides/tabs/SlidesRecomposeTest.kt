package org.churchpresenter.slides.tabs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.awaitDeck
import org.churchpresenter.slides.pdfDeck
import org.churchpresenter.slides.presenter.PresentationFrame
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SlidesRecomposeTest {

    private val dir = tempDir("cp-slides-recompose")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private val unbound = ShortcutMap.from(
        KeyboardShortcutSettings(overrides = ShortcutAction.entries.associate { it.name to emptyList() }),
    )

    /** Everything a tab is handed, held as state so a test can change all of it at once. */
    private class Inputs {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        var shortcuts by mutableStateOf(ShortcutMap.DEFAULT)
        var frozen by mutableStateOf(false)
        var vlcAvailable by mutableStateOf(true)
        var vlcArch by mutableStateOf(false)
        var vlcFailed by mutableStateOf(false)
        var generation by mutableIntStateOf(0)
        var output by mutableStateOf<FakeSlidesOutput?>(FakeSlidesOutput())
        var presentationItem by mutableStateOf<ScheduleItem.PresentationItem?>(null)
        var pictureItem by mutableStateOf<ScheduleItem.PictureItem?>(null)

        fun changeEverything() {
            theme = ThemeMode.DARK
            shortcuts = ShortcutMap.from(KeyboardShortcutSettings())
            frozen = !frozen
            vlcAvailable = !vlcAvailable
            vlcArch = !vlcArch
            vlcFailed = !vlcFailed
            generation++
            output = FakeSlidesOutput()
        }
    }

    private fun presentation(
        inputs: Inputs = Inputs(),
        block: ComposeUiTest.(PresentationViewModel, Inputs) -> Unit,
    ) {
        val settings = AppSettings()
        val vm = PresentationViewModel(settings)
        try {
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = inputs.theme) {
                        CompositionLocalProvider(LocalShortcuts provides inputs.shortcuts) {
                            val gen = inputs.generation
                            PresentationTab(
                                modifier = Modifier,
                                appSettings = settings,
                                onAddToSchedule = { _, _, _, _ -> gen },
                                onSavePreset = { _, _, _, _ -> gen },
                                onInstanceLinkSendProject = { gen },
                                onInstanceLinkSendNextSlide = { gen },
                                onInstanceLinkSendPreviousSlide = { gen },
                                instanceLinkFetchPresentationSlideBytes = { _, _ -> null },
                                selectedPresentationItem = inputs.presentationItem,
                                selectedPresentationItemVersion = gen,
                                presenterManager = inputs.output,
                                onSlidesLoaded = { _, _, _, _, _, _ -> gen },
                                onSettingsChange = { gen },
                                viewModel = vm,
                                remoteDialog = { gen },
                                presentationFrozen = inputs.frozen,
                                onFreezeToggle = { gen },
                                onClearPresentation = { gen },
                                vlcAvailable = inputs.vlcAvailable,
                                vlcArchMismatch = inputs.vlcArch,
                                vlcLoadFailed = inputs.vlcFailed,
                            )
                        }
                    }
                }
                block(vm, inputs)
            }
        } finally {
            runCatching { vm.dispose() }
        }
    }

    private fun pictures(
        folder: File?,
        inputs: Inputs = Inputs(),
        block: ComposeUiTest.(PicturesViewModel, Inputs) -> Unit,
    ) {
        val settings = AppSettings(pictureSettings = PictureSettings(storageDirectory = folder?.absolutePath ?: ""))
        val vm = PicturesViewModel(settings)
        folder?.let(vm::selectFolder)
        try {
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = inputs.theme) {
                        CompositionLocalProvider(LocalShortcuts provides inputs.shortcuts) {
                            val gen = inputs.generation
                            PicturesTab(
                                modifier = Modifier,
                                appSettings = settings,
                                onAddToSchedule = { _, _, _ -> gen },
                                onSavePreset = { _, _, _ -> gen },
                                onInstanceLinkSendProject = { gen },
                                onInstanceLinkSendNextPicture = { gen },
                                onInstanceLinkSendPreviousPicture = { gen },
                                instanceLinkFetchPictureImageBytes = { _, _ -> null },
                                selectedPictureItem = inputs.pictureItem,
                                selectedPictureItemVersion = gen,
                                presenterManager = inputs.output,
                                onSettingsChange = { gen },
                                viewModel = vm,
                            )
                        }
                    }
                }
                block(vm, inputs)
            }
        } finally {
            runCatching { vm.dispose() }
        }
    }

    private fun picturesFolder(count: Int = 4): File = File(dir, "Pics").apply {
        mkdirs()
        repeat(count) { solidImage(this, "p$it.png", Color(25 * it, 80, 120)) }
    }

    @Test
    fun `an empty presentation tab redraws when everything it is given changes`() = presentation { _, inputs ->
        waitForIdle()
        inputs.changeEverything()
        waitForIdle()
        assertTrue(onAllNodesWithText(PresentationLabel.NO_FILE).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a loaded presentation tab redraws when everything it is given changes`() = presentation { vm, inputs ->
        vm.addPresentation(pdfDeck(dir, 3))
        awaitDeck(vm)
        inputs.changeEverything()
        waitForIdle()
        inputs.changeEverything()
        waitForIdle()
        assertEquals(3, vm.slideFiles.size)
    }

    @Test
    fun `the presentation tab copes with its output being taken away`() = presentation { vm, inputs ->
        vm.addPresentation(pdfDeck(dir, 2))
        awaitDeck(vm)
        inputs.output = null
        waitForIdle()
        inputs.output = FakeSlidesOutput()
        waitForIdle()
        assertEquals(2, vm.slideFiles.size)
    }

    @Test
    fun `with every shortcut unbound the presentation tab shows no key hint`() =
        presentation(Inputs().apply { shortcuts = unbound }) { vm, _ ->
            vm.addPresentation(pdfDeck(dir, 2))
            awaitDeck(vm)
            assertTrue(onAllNodesWithText("play/pause", substring = true).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the key hint names the bound keys`() = presentation { vm, _ ->
        vm.addPresentation(pdfDeck(dir, 2))
        awaitDeck(vm)
        assertTrue(onAllNodesWithText("play/pause", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a build in progress on the live slide is counted`() {
        val output = FakeSlidesOutput()
        presentation(Inputs().apply { this.output = output }) { vm, _ ->
            vm.addPresentation(pdfDeck(dir, 2))
            awaitDeck(vm)
            output.presentationFrame.value = PresentationFrame(0, 100, 100, 1f, emptyList(), 1, 3)
            waitForIdle()
            onNodeWithText("Build 1 of 3").assertExists()
            output.presentationFrame.value = PresentationFrame(1, 100, 100, 1f, emptyList(), 1, 3)
            waitForIdle()
            onNodeWithText("Build 1 of 3").assertDoesNotExist()
        }
    }

    @Test
    fun `two open decks can be switched between from their tabs`() = presentation { vm, _ ->
        val first = pdfDeck(dir, 1, "First.pdf")
        val second = pdfDeck(dir, 2, "Second.pdf")
        vm.addPresentation(first)
        awaitDeck(vm)
        val before = vm.loadGeneration
        vm.addPresentation(second)
        waitUntil("the second deck", 10_000) { vm.loadGeneration > before && !vm.isLoading }
        onNodeWithText("First").performClick()
        waitUntil("back on the first deck", 10_000) { vm.selectedPresentation?.name == "First.pdf" && !vm.isLoading }
        assertEquals("First.pdf", vm.selectedPresentation?.name)
    }

    @Test
    fun `an open deck's close button removes it`() = presentation { vm, _ ->
        val first = pdfDeck(dir, 1, "Keep.pdf")
        val second = pdfDeck(dir, 1, "Drop.pdf")
        vm.addPresentation(first)
        awaitDeck(vm)
        val before = vm.loadGeneration
        vm.addPresentation(second)
        waitUntil("the second deck", 10_000) { vm.loadGeneration > before && !vm.isLoading }
        onAllNodesWithContentDescription("Remove")[1].performClick()
        waitForIdle()
        assertEquals(listOf("Keep.pdf"), vm.presentations.map { it.name })
    }

    @Test
    fun `a picture tab redraws when everything it is given changes`() = pictures(picturesFolder()) { vm, inputs ->
        waitUntil("the pictures listed", 5_000) { vm.images.size == 4 }
        inputs.changeEverything()
        waitForIdle()
        inputs.changeEverything()
        waitForIdle()
        assertEquals(4, vm.images.size)
    }

    @Test
    fun `a picture tab with nothing open redraws as well`() = pictures(null) { vm, inputs ->
        inputs.changeEverything()
        waitForIdle()
        assertTrue(vm.images.isEmpty())
    }

    @Test
    fun `the picture tab copes with its output being taken away`() = pictures(picturesFolder()) { vm, inputs ->
        inputs.output?.setPresentingMode(Presenting.PICTURES)
        waitForIdle()
        inputs.output = null
        waitForIdle()
        vm.selectImage(2)
        waitForIdle()
        assertEquals(2, vm.selectedImageIndex)
    }

    @Test
    fun `with every shortcut unbound the picture tab drops its key hint`() =
        pictures(picturesFolder(), Inputs().apply { shortcuts = unbound }) { _, _ ->
            assertTrue(onAllNodesWithText("next/prev image", substring = true).fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `the picture key hint names the bound keys`() = pictures(picturesFolder()) { _, _ ->
        assertTrue(onAllNodesWithText("next/prev image", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `the up and down keys move a whole row of pictures`() = pictures(picturesFolder(8)) { vm, _ ->
        waitUntil("the pictures listed", 5_000) { vm.images.size == 8 }
        onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        val afterDown = vm.selectedImageIndex
        assertTrue(afterDown > 0, "down moves a row")
        onRoot().performKeyInput { pressKey(Key.DirectionUp) }
        waitForIdle()
        assertEquals(0, vm.selectedImageIndex)
    }
}
