@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Go Live key on the Pictures tab: Enter on the tab root puts the selected picture on the
 * output, as the Go Live button does — and not while the pictures are already up, where the arrow
 * keys step them instead.
 */
class PicturesGoLiveKeyTest {

    private fun ComposeUiTest.press(key: Key) {
        onRoot().performKeyInput { pressKey(key) }
        waitForIdle()
    }

    @Test
    fun `enter on the tab root puts the selected picture live`() {
        val presenter = FakeSlidesOutput()
        picturesTab(presenterManager = presenter) { vm, _ ->
            press(Key.DirectionRight)
            val selected = vm.images[vm.selectedImageIndex]

            press(Key.Enter)

            assertEquals(Presenting.PICTURES, presenter.onAir.value)
            assertEquals(selected.absolutePath, presenter.selectedImagePath.value)
        }
    }

    @Test
    fun `enter after clicking a thumbnail puts that picture live`() {
        val presenter = FakeSlidesOutput()
        picturesTab(presenterManager = presenter) { vm, _ ->
            waitUntil("two thumbnails drawn", WAIT_MS) { drawnThumbnails().size >= 2 }
            val name = drawnThumbnails()[1]
            onNodeWithContentDescription(name).performClick()
            waitForIdle()

            press(Key.Enter)

            assertEquals(Presenting.PICTURES, presenter.onAir.value)
            assertEquals(vm.images[vm.selectedImageIndex].absolutePath, presenter.selectedImagePath.value)
        }
    }

    @Test
    fun `enter does nothing while the pictures are already live`() {
        val presenter = FakeSlidesOutput()
        picturesTab(presenterManager = presenter) { _, _ ->
            presenter.setPresentingMode(Presenting.PICTURES)
            waitForIdle()

            press(Key.Enter)

            assertNull(presenter.selectedImagePath.value, "already up — the key must not resend")
        }
    }

    @Test
    fun `enter does nothing in a folder with no pictures`() {
        val presenter = FakeSlidesOutput()
        val empty = Files.createTempDirectory("cp-pictures-empty").toFile()
        picturesTab(folder = empty, presenterManager = presenter) { _, _ ->
            press(Key.Enter)

            assertEquals(Presenting.NONE, presenter.onAir.value)
        }
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
