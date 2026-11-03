package org.churchpresenter.slides.tabs

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundContent
import org.churchpresenter.settings.PictureSettings
import org.churchpresenter.sharedui.composables.BackgroundTarget
import org.churchpresenter.sharedui.composables.setAsBackgroundTag
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A thumbnail's right-click menu: Go Live, Add to Schedule for that one picture, and Set as Background. */
@OptIn(ExperimentalTestApi::class)
class PicturesContextMenuTest {

    private val dir = tempDir("cp-pictures-context-menu")

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    private class Calls {
        val scheduled = mutableListOf<Pair<File, Int>>()
        val backgrounds = mutableListOf<Triple<File, String?, BackgroundContent>>()
    }

    private fun tab(
        current: (File, String?, BackgroundContent) -> Boolean = { _, _, _ -> false },
        block: ComposeUiTest.(PicturesViewModel, Calls, FakeSlidesOutput) -> Unit,
    ) {
        val folder = File(dir, "Sunday").apply {
            mkdirs()
            repeat(3) { solidImage(this, "pic$it.png", Color(it * 60, 0, 255 - it * 60)) }
        }
        val settings = AppSettings(pictureSettings = PictureSettings(storageDirectory = folder.absolutePath))
        val vm = PicturesViewModel(settings).apply { selectFolder(folder) }
        val calls = Calls()
        val output = FakeSlidesOutput()
        try {
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                        PicturesTab(
                            modifier = Modifier,
                            appSettings = settings,
                            onAddPictureToSchedule = { image, count -> calls.scheduled += image to count },
                            backgroundTargets = listOf(BackgroundTarget("main", "Screen 1")),
                            onSetAsBackground = { image, id, content ->
                                calls.backgrounds += Triple(image, id, content)
                            },
                            isBackground = current,
                            presenterManager = output,
                            viewModel = vm,
                        )
                    }
                }
                waitUntil("the thumbnails drawn", 5_000) { drawnThumbnails().size >= 2 }
                block(vm, calls, output)
            }
        } finally {
            runCatching { vm.dispose() }
        }
    }

    private fun ComposeUiTest.rightClickThumbnail(
        name: String,
        at: (Float, Float) -> Offset = { w, h -> Offset(w / 2, h / 2) },
    ) {
        onNodeWithContentDescription(name).performMouseInput { rightClick(at(width.toFloat(), height.toFloat())) }
        waitForIdle()
    }

    private fun ComposeUiTest.menuOpen() = onAllNodesWithText("Set as Background").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `right-clicking a thumbnail opens its menu`() = tab { _, _, _ ->
        rightClickThumbnail("pic1.png")
        assertTrue(menuOpen())
        onNodeWithText("Add to Schedule").assertExists()
        onNodeWithText("Go Live").assertExists()
    }

    @Test
    fun `the menu opens at the pointer, not a tile below it`() = tab { _, _, _ ->
        val tile = onNodeWithContentDescription("pic1.png").fetchSemanticsNode().boundsInWindow
        rightClickThumbnail("pic1.png") { w, _ -> Offset(w / 2, 10f) }
        val menuTop = onNodeWithText("Go Live").fetchSemanticsNode().boundsInWindow.top
        val pressedAt = tile.top + 10f
        assertTrue(
            menuTop - pressedAt < tile.height / 2,
            "the menu's first item is near the press ($pressedAt), not a tile's height below it ($menuTop)",
        )
    }

    @Test
    fun `Add to Schedule adds just that picture`() = tab { vm, calls, _ ->
        rightClickThumbnail("pic1.png")
        onNodeWithText("Add to Schedule").performClick()
        waitForIdle()
        val (image, count) = calls.scheduled.single()
        assertEquals("pic1.png", image.name)
        assertEquals(vm.images.size, count)
        assertTrue(!menuOpen(), "the menu closes")
    }

    @Test
    fun `Go Live puts that picture on screen`() = tab { vm, _, output ->
        rightClickThumbnail("pic1.png")
        onNodeWithText("Go Live").performClick()
        waitForIdle()
        assertEquals("pic1.png", vm.images[vm.selectedImageIndex].name)
        assertEquals(Presenting.PICTURES, output.onAir.value)
    }

    @Test
    fun `Set as Background asks for that picture, those outputs and that background`() = tab { _, calls, _ ->
        rightClickThumbnail("pic2.png")
        onNodeWithText("Set as Background").performClick()
        waitForIdle()
        onNodeWithTag(setAsBackgroundTag("main", BackgroundContent.SONG)).performClick()
        waitForIdle()
        val (image, id, content) = calls.backgrounds.single()
        assertEquals("pic2.png", image.name)
        assertEquals("main", id)
        assertEquals(BackgroundContent.SONG, content)
    }

    @Test
    fun `the background a picture already is is marked`() =
        tab(current = { image, id, content ->
            image.name == "pic0.png" && id == null && content == BackgroundContent.DEFAULT
        }) { _, _, _ ->
            rightClickThumbnail("pic0.png")
            onNodeWithText("Set as Background").performClick()
            waitForIdle()
            val selected = onNodeWithTag(setAsBackgroundTag(null, BackgroundContent.DEFAULT))
                .fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)
            assertEquals(true, selected)
        }
}
