package org.churchpresenter.profiles

import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The arithmetic behind the preview's box handles and the margin fields: moving, resizing and
 * snapping a box, how far a margin may go, and a background remembered while it follows.
 */
class BoxDragMathTest {

    private val box = TextBox(enabled = true, xPercent = 10f, yPercent = 10f, widthPercent = 40f, heightPercent = 20f)

    @Test
    fun `a moved box stays inside its area`() {
        assertEquals(20f, box.movedBy(10f, 0f).xPercent)
        assertEquals(60f, box.movedBy(90f, 0f).xPercent, "its right edge stops at the area's")
        assertEquals(0f, box.movedBy(0f, -50f).yPercent)
    }

    @Test
    fun `a corner resizes the two edges it touches and no others`() {
        val resized = box.resizedBy(BoxGrip.BOTTOM_RIGHT, 10f, 5f)
        assertEquals(10f, resized.xPercent)
        assertEquals(50f, resized.widthPercent)
        assertEquals(25f, resized.heightPercent)
        val fromTop = box.resizedBy(BoxGrip.TOP, 0f, 5f)
        assertEquals(15f, fromTop.yPercent)
        assertEquals(15f, fromTop.heightPercent)
        assertEquals(box.widthPercent, fromTop.widthPercent)
    }

    @Test
    fun `a box cannot be resized smaller than a sliver or past its area`() {
        val squashed = box.resizedBy(BoxGrip.LEFT, 100f, 0f)
        assertEquals(TextBox.MIN_SIZE_PERCENT, squashed.widthPercent, 0.001f)
        assertEquals(TextBox.FULL_PERCENT, box.resizedBy(BoxGrip.RIGHT, 500f, 0f).rightPercent)
    }

    @Test
    fun `a box close to a guide snaps onto it, and one far from every guide stays`() {
        val (across, down) = snapLines(emptyList())
        val near = box.copy(xPercent = 0.8f).snappedTo(across, down)
        assertEquals(0f, near.xPercent, "its left edge is pulled onto the area's")
        val centred = box.copy(xPercent = 30.5f).snappedTo(across, down)
        assertEquals(50f, centred.xPercent + centred.widthPercent / 2, 0.001f)
        assertEquals(box.copy(xPercent = 20f), box.copy(xPercent = 20f).snappedTo(across, down))
    }

    @Test
    fun `boxes snap to each other's edges`() {
        val other = TextBox(xPercent = 60f, yPercent = 0f, widthPercent = 20f, heightPercent = 20f)
        val (across, down) = snapLines(listOf(other))
        val moved = box.copy(xPercent = 19.5f).snappedTo(across, down)
        assertEquals(60f, moved.rightPercent, 0.001f)
    }

    @Test
    fun `a margin may take up to nine tenths of the room less the one opposite`() {
        val margins = Margins(top = 0, bottom = 100, left = 0, right = 960)
        assertEquals(1728 - 960, MarginRoom.FULL_SCREEN.maxFor(MarginSide.LEFT, margins))
        assertEquals(1728, MarginRoom.FULL_SCREEN.maxFor(MarginSide.RIGHT, margins.copy(left = 0)))
        assertEquals(972 - 100, MarginRoom.FULL_SCREEN.maxFor(MarginSide.TOP, margins))
        assertEquals(0, MarginRoom.FULL_SCREEN.maxFor(MarginSide.LEFT, margins.copy(right = 1900)))
    }

    @Test
    fun `on a lower third the margins are taken from the band`() {
        val band = MarginRoom.reference(bandPercent = 50)
        assertEquals(486, band.maxFor(MarginSide.TOP, Margins(0, 0, 0, 0)))
        assertEquals(MarginRoom.FULL_SCREEN, MarginRoom.reference(null))
    }

    @Test
    fun `a surface following its default keeps its own type aside, and gets it back`() {
        val own = BackgroundConfig(backgroundType = Constants.BACKGROUND_IMAGE, backgroundImage = "worship.jpg")
        val following = own.followingDefault(BackgroundScope.BIBLE)
        assertEquals(BackgroundScope.BIBLE.inheritType, following.backgroundType)
        assertEquals(Constants.BACKGROUND_IMAGE, following.ownBackgroundType)
        val back = following.ownAgain(BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR))
        assertEquals(Constants.BACKGROUND_IMAGE, back.backgroundType)
        assertEquals("worship.jpg", back.backgroundImage)
        assertEquals("", back.ownBackgroundType)
    }

    @Test
    fun `a surface that never had its own starts from what was showing`() {
        val following = BackgroundConfig(backgroundType = BackgroundScope.BIBLE.inheritType.orEmpty())
        val shown = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#123456")
        val own = following.ownAgain(shown)
        assertEquals(Constants.BACKGROUND_COLOR, own.backgroundType)
        assertEquals("#123456", own.backgroundColor)
        assertEquals(following, following.followingDefault(BackgroundScope.BIBLE), "following already, nothing changes")
    }
}
