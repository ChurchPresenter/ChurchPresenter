@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rest of the Pictures tab: going live, the animation dropdown reflecting a previously-saved
 * choice, an unhandled key, Instance Link Controller navigation with an empty list, loading a
 * schedule item's folder, and the tab's own bare defaults.
 *
 * Left uncovered: the shift+drag reorder gesture (a test cannot set `keyboardModifiers` on an
 * injected pointer event — see `ScheduleTabReorderDragTest`'s doc comment, which records this same
 * tab as one of the two places that blocks); and anything that reaches `RecentPictureFolders` — see
 * `PicturesTabTestSupport.kt`'s doc comment for why.
 *
 * The loop toggle used to be listed here too — "neither text nor a content description to address it
 * by". It now has one, matching its tooltip, so it is addressable by name like any other button. See
 * `PicturesTabLoopTooltipTest`.
 *
 * See `PicturesTabTestSupport.kt` for the harness.
 */
class PicturesTabExtraTest {

    // ── Going live ────────────────────────────────────────────────────────────────

    @Test
    fun `the go live button pushes the selected image to the presenter`() {
        val presenter = PresenterManager()
        picturesTab(presenterManager = presenter) { vm, _ ->
            pictureButton(PictureLabel.NEXT).performClick()
            waitForIdle()
            val selected = vm.images[vm.selectedImageIndex]

            pictureButton(PictureLabel.GO_LIVE).performClick()
            waitForIdle()

            assertEquals(Presenting.PICTURES, presenter.presentingMode.value)
            assertEquals(selected.absolutePath, presenter.selectedImagePath.value)
        }
    }

    @Test
    fun `there is nowhere to go live without a presenter manager`() = picturesTab { _, _ ->
        assertFalse(hasPictureButton(PictureLabel.GO_LIVE))
    }

    // ── The animation dropdown reflects a previously-saved choice ──────────────────

    @Test
    fun `a saved fade setting is shown as Fade`() =
        picturesTab(
            settings = { it.copy(pictureSettings = it.pictureSettings.copy(animationType = Constants.ANIMATION_FADE)) },
        ) { _, _ ->
            assertTrue(showsContainingText("ANIMATION TYPE:Fade"), renderedText().toString())
        }

    @Test
    fun `a saved slide-right setting is shown as Slide Right`() =
        picturesTab(
            settings =
                { it.copy(pictureSettings = it.pictureSettings.copy(animationType = Constants.ANIMATION_SLIDE_RIGHT)) },
        ) { _, _ ->
            assertTrue(showsContainingText("ANIMATION TYPE:Slide Right"), renderedText().toString())
        }

    @Test
    fun `a saved none setting is shown as None`() =
        picturesTab(
            settings = { it.copy(pictureSettings = it.pictureSettings.copy(animationType = Constants.ANIMATION_NONE)) },
        ) { _, _ ->
            assertTrue(showsContainingText("ANIMATION TYPE:None"), renderedText().toString())
        }

    @Test
    fun `re-choosing Crossfade from the dropdown is a real choice, not a no-op`() =
        picturesTab(
            settings =
                { it.copy(pictureSettings = it.pictureSettings.copy(animationType = Constants.ANIMATION_SLIDE_LEFT)) },
        ) { vm, reports ->
            openAnimationDropdown()
            onNodeWithText("Crossfade").performClick()
            waitForIdle()

            assertEquals(AnimationType.CROSSFADE, vm.animationType)
            assertEquals(Constants.ANIMATION_CROSSFADE, reports.settingsAfterChange?.pictureSettings?.animationType)
        }

    // ── An unhandled key ─────────────────────────────────────────────────────────

    @Test
    fun `a key with no meaning here changes nothing`() = picturesTab { vm, _ ->
        onRoot().performKeyInput { pressKey(Key.A) }
        waitForIdle()

        assertEquals(0, vm.selectedImageIndex)
        assertFalse(vm.isPlaying)
    }

    // ── Instance Link Controller navigation with nothing of its own loaded ─────────

    @Test
    fun `next and previous still reach the primary when this Controller has no images`() {
        var nextCalls = 0
        var previousCalls = 0
        picturesTab(
            folder = null,
            onInstanceLinkSendNextPicture = { nextCalls++ },
            onInstanceLinkSendPreviousPicture = { previousCalls++ },
        ) { vm, _ ->
            onRoot().performKeyInput { pressKey(Key.DirectionRight) }
            waitForIdle()
            onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
            waitForIdle()

            assertEquals(1, nextCalls)
            assertEquals(1, previousCalls)
            assertEquals(0, vm.selectedImageIndex, "there is nothing of this Controller's own to move")
        }
    }

    @Test
    fun `an unhandled key with Instance Link navigation available still does nothing`() {
        var nextCalls = 0
        picturesTab(folder = null, onInstanceLinkSendNextPicture = { nextCalls++ }) { vm, _ ->
            onRoot().performKeyInput { pressKey(Key.A) }
            waitForIdle()

            assertEquals(0, nextCalls)
            assertEquals(0, vm.selectedImageIndex)
        }
    }

    // ── Loading a schedule item ──────────────────────────────────────────────────

    @Test
    fun `selecting a picture schedule item loads its folder`() {
        val folder = pictureFolder()
        try {
            val item = ScheduleItem.PictureItem(
                id = "p1",
                folderPath = folder.absolutePath,
                folderName = folder.name,
                imageCount = 3,
            )
            picturesTab(folder = null, selectedPictureItem = item) { vm, _ ->
                waitForIdle()

                assertEquals(folder.absolutePath, vm.selectedFolder?.absolutePath)
                assertEquals(3, vm.images.size)
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    // ── Bare defaults ─────────────────────────────────────────────────────────────

    @Test
    fun `PicturesTab with no overrides builds its own view model`() = runComposeUiTest {
        setContent { MaterialTheme { PicturesTab() } }

        onNodeWithText(PictureLabel.NO_FOLDER).assertExists()
    }

    // ── The grid against a folder that changes underneath it ────────────────────
    //
    // Not covered: the IndexOutOfBoundsException this pins the fix for. That one needs the list to
    // shrink *between* the grid reading its count and the item provider being asked for a content
    // type, inside a single measure pass — there is no hook to interleave those, and a test that
    // raced it would pass most of the time, which is worse than not having one. What is covered is
    // the property the fix rests on: the grid draws from the list it was handed.

    @Test
    fun `the grid follows the folder when a picture is removed`() = picturesTab { vm, _ ->
        awaitThumbnails("one.png", "two.png", "three.jpg")
        val folder = vm.selectedFolder!!

        // A folder loses a file mid-service: someone tidies up, or a sync client moves it, and the
        // folder is re-read. loadImagesFromFolder only ever adds — dropping a file is what
        // selectFolder's clear-and-reload does, and it is the path the UI takes on a refresh.
        assertTrue(File(folder, "two.png").delete(), "the fixture file was there to delete")
        vm.selectFolder(folder)
        waitForIdle()

        assertEquals(
            listOf("one.png", "three.jpg"),
            vm.images.map { it.name },
            "the view model dropped it",
        )
        waitUntil("the removed tile to go") { "two.png" !in drawnThumbnails() }
        assertEquals(
            listOf("one.png", "three.jpg").sorted(),
            drawnThumbnails().sorted(),
            "and the grid draws exactly what is left, having indexed only the list it was given",
        )
    }

}
