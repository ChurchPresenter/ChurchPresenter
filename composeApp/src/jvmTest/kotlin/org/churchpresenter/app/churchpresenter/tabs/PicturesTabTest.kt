@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * The Pictures tab: the image grid, the transport controls under it, and what the tab hands its
 * host.
 *
 * The tab is a slideshow remote — an operator picks a folder before the service and then drives it
 * with three buttons, so what matters is that the counter and the buttons agree with each other and
 * with what is on screen, and that the folder handed to the schedule is the one being viewed.
 *
 * See `PicturesTabTestSupport.kt` for the harness.
 */
class PicturesTabTest {

    // ── With no folder chosen ───────────────────────────────────────────────────

    @Test
    fun `with no folder chosen the tab says so and offers to pick one`() =
        picturesTab(folder = null) { vm, _ ->
            assertTrue(vm.images.isEmpty())
            assertTrue(showsExactly(PictureLabel.NO_FOLDER), "the folder line is a placeholder")
            assertTrue(showsExactly(PictureLabel.EMPTY_GRID), "and the grid explains itself")
            assertTrue(showsExactly(PictureLabel.SELECT_FOLDER), "with the way out on screen")
        }

    // ── Loading a folder ────────────────────────────────────────────────────────

    @Test
    fun `choosing a folder loads its images and nothing else`() = picturesTab { vm, _ ->
        assertEquals(
            listOf("one.png", "three.jpg", "two.png"),
            vm.images.map { it.name },
            "every image, sorted by name — and the text file is not one",
        )
    }

    @Test
    fun `the chosen folder's path is shown, not a placeholder`() = picturesTab { vm, _ ->
        assertTrue(
            showsExactly(vm.selectedFolder!!.absolutePath),
            "the operator can see which folder is loaded: ${renderedText().take(6)}",
        )
        assertFalse(showsExactly(PictureLabel.NO_FOLDER))
    }

    @Test
    fun `every image gets a thumbnail`() = picturesTab { _, _ ->
        awaitThumbnails("one.png", "two.png", "three.jpg")

        assertEquals(
            3,
            drawnThumbnails().distinct().size,
            "one thumbnail per image: ${drawnThumbnails()}",
        )
    }

    // ── The counter ─────────────────────────────────────────────────────────────

    @Test
    fun `the counter reads one-based, so it matches what an operator would say`() =
        picturesTab { vm, _ ->
            assertEquals(0, vm.selectedImageIndex, "internally the first image is index 0")
            assertTrue(showsExactly("Image 1 of 3"), "but it reads as image 1: ${renderedText()}")
        }

    @Test
    fun `stepping forward moves the counter and the selection together`() = picturesTab { vm, _ ->
        pictureButton(PictureLabel.NEXT).performClick()
        waitForIdle()

        assertEquals(1, vm.selectedImageIndex)
        assertTrue(showsExactly("Image 2 of 3"), "got ${renderedText()}")
    }

    @Test
    fun `stepping back from the first image wraps to the last`() = picturesTab { vm, _ ->
        // Looping is on by default, so the slideshow never dead-ends mid-service.
        assertTrue(vm.isLooping)

        pictureButton(PictureLabel.PREVIOUS).performClick()
        waitForIdle()

        assertEquals(2, vm.selectedImageIndex)
        assertTrue(showsExactly("Image 3 of 3"))
    }

    @Test
    fun `stepping past the last image wraps to the first`() = picturesTab { vm, _ ->
        repeat(3) {
            pictureButton(PictureLabel.NEXT).performClick()
            waitForIdle()
        }

        assertEquals(0, vm.selectedImageIndex, "three steps through three images comes home")
        assertTrue(showsExactly("Image 1 of 3"))
    }

    // ── Play and pause ──────────────────────────────────────────────────────────

    @Test
    fun `the play button becomes a pause button once it is running`() = picturesTab { vm, _ ->
        assertTrue(hasPictureButton(PictureLabel.PLAY), "stopped to begin with")

        pictureButton(PictureLabel.PLAY).performClick()
        waitForIdle()

        assertTrue(vm.isPlaying)
        assertTrue(hasPictureButton(PictureLabel.PAUSE), "the same button now offers to stop")
        assertFalse(hasPictureButton(PictureLabel.PLAY), "and no longer offers to start")
    }

    @Test
    fun `pausing stops it again`() = picturesTab { vm, _ ->
        pictureButton(PictureLabel.PLAY).performClick()
        waitForIdle()
        pictureButton(PictureLabel.PAUSE).performClick()
        waitForIdle()

        assertFalse(vm.isPlaying)
        assertTrue(hasPictureButton(PictureLabel.PLAY))
    }

    // ── Handing the folder on ───────────────────────────────────────────────────

    @Test
    fun `adding to the schedule hands over the folder, not the current image`() =
        picturesTab { vm, reports ->
            // A schedule item is the whole slideshow — stepping first must not narrow it.
            pictureButton(PictureLabel.NEXT).performClick()
            waitForIdle()
            pictureButton(PictureLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            val folder = vm.selectedFolder!!
            assertEquals(
                listOf(Triple(folder.absolutePath, folder.name, 3)),
                reports.scheduled,
            )
        }

    @Test
    fun `with no folder chosen there is nothing to add to the schedule`() =
        picturesTab(folder = null) { _, reports ->
            pictureButton(PictureLabel.ADD_TO_SCHEDULE).performClick()
            waitForIdle()

            assertTrue(reports.scheduled.isEmpty(), "got ${reports.scheduled}")
        }

    // ── Selecting from the grid ─────────────────────────────────────────────────

    @Test
    fun `clicking a thumbnail selects that image`() = picturesTab { vm, _ ->
        awaitThumbnails("two.png")

        // "two.png" sorts last of the three, so this also pins that the grid is in sorted order
        // rather than in whatever order the filesystem listed.
        onNodeWithContentDescription("two.png").performClick()
        waitForIdle()

        assertEquals(2, vm.selectedImageIndex)
        assertTrue(showsExactly("Image 3 of 3"))
    }
}
