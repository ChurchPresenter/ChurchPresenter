@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import kotlin.test.Test
import kotlin.test.assertEquals

/** Shift-dragging a picture onto another moves it there; a plain drag does not. */
class PicturesShiftDragTest {

    @Test
    fun `a shift-drag moves a picture to where it is dropped`() = picturesTab { vm, _ ->
        awaitThumbnails("one.png", "two.png", "three.jpg")
        val from = onNodeWithContentDescription("one.png").fetchSemanticsNode().boundsInRoot.center
        val to = onNodeWithContentDescription("two.png").fetchSemanticsNode().boundsInRoot.center
        onRoot().performKeyInput { keyDown(Key.ShiftLeft) }
        onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(Offset((from.x + to.x) / 2, (from.y + to.y) / 2))
            moveTo(to)
            release()
        }
        onRoot().performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()
        assertEquals(listOf("three.jpg", "two.png", "one.png"), vm.images.map { it.name })
    }

    @Test
    fun `a drag without shift leaves the order alone`() = picturesTab { vm, _ ->
        awaitThumbnails("one.png", "two.png", "three.jpg")
        val before = vm.images.map { it.name }
        val from = onNodeWithContentDescription("one.png").fetchSemanticsNode().boundsInRoot.center
        val to = onNodeWithContentDescription("two.png").fetchSemanticsNode().boundsInRoot.center
        onRoot().performMouseInput {
            moveTo(from)
            press()
            moveTo(to)
            release()
        }
        waitForIdle()
        assertEquals(before, vm.images.map { it.name })
    }

    @Test
    fun `a shift-click without moving changes nothing`() = picturesTab { vm, _ ->
        awaitThumbnails("one.png", "two.png", "three.jpg")
        val before = vm.images.map { it.name }
        val at = onNodeWithContentDescription("one.png").fetchSemanticsNode().boundsInRoot.center
        onRoot().performKeyInput { keyDown(Key.ShiftLeft) }
        onRoot().performMouseInput {
            moveTo(at)
            press()
            release()
        }
        onRoot().performKeyInput { keyUp(Key.ShiftLeft) }
        waitForIdle()
        assertEquals(before, vm.images.map { it.name })
    }
}
