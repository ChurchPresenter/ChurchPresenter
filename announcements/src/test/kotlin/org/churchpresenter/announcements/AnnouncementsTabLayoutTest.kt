@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.announcements

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import org.churchpresenter.sharedui.composables.screenPositionTag
import org.churchpresenter.sharedui.utils.OutputSize
import org.churchpresenter.sharedui.utils.PreviewOutput
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the tab puts things: the text card over the timer in the left column, the preview filling
 * the right with the shared position, background and animation cards stacked under it, and the
 * divider that trades height between the text box and the timer. See
 * `AnnouncementsTabTestSupport.kt` for the harness.
 */
class AnnouncementsTabLayoutTest {

    private fun ComposeUiTest.bounds(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.dragDivider(byPx: Float) {
        onNodeWithTag(ANNOUNCEMENTS_TEXT_DIVIDER_TAG).performTouchInput {
            down(center)
            moveBy(Offset(0f, byPx))
            up()
        }
        waitForIdle()
    }

    // ── The left column ─────────────────────────────────────────────────────────

    @Test
    fun `the text card sits over the timer, with its buttons above the text`() = announcementsTab { _, _ ->
        typeAnnouncement("Welcome")
        val text = bounds(ANNOUNCEMENTS_TEXT_BOX_TAG)
        val goLive = annButton(AnnouncementLabel.GO_LIVE).fetchSemanticsNode().boundsInRoot
        val start = timerButton(AnnouncementLabel.START).fetchSemanticsNode().boundsInRoot

        assertTrue(goLive.bottom <= text.top, "the text's buttons are above it: $goLive vs $text")
        assertTrue(text.bottom <= start.top, "the timer is under the text: $text vs $start")
        assertTrue(start.left < text.right, "and in the same column: $start vs $text")
    }

    @Test
    fun `dragging the divider down makes the text box taller and remembers it`() = announcementsTab { _, reports ->
        val before = bounds(ANNOUNCEMENTS_TEXT_BOX_TAG).height

        dragDivider(DRAG_PX)

        val after = bounds(ANNOUNCEMENTS_TEXT_BOX_TAG).height
        assertTrue(after > before, "taller: $before -> $after")
        val savedDp = reports.appSettings?.maximizedLayout?.announcementsTextHeightDp ?: 0
        assertEquals(after / density.density, savedDp.toFloat(), SIZE_TOLERANCE, "and saved at its new height")
    }

    @Test
    fun `a saved text height is what the box opens at`() =
        announcementsTab(settings = textHeight(SAVED_TEXT_HEIGHT_DP)) { _, _ ->
            val height = bounds(ANNOUNCEMENTS_TEXT_BOX_TAG).height / density.density
            assertEquals(SAVED_TEXT_HEIGHT_DP.toFloat(), height, SIZE_TOLERANCE)
        }

    @Test
    fun `the divider always leaves the timer card its minimum`() = announcementsTab { _, _ ->
        dragDivider(onRoot().fetchSemanticsNode().boundsInRoot.height)

        val timerCardDp = bounds(ANNOUNCEMENTS_TIMER_CARD_TAG).height / density.density
        assertTrue(timerCardDp >= MIN_TIMER_CARD_DP - SIZE_TOLERANCE, "timer card kept $timerCardDp dp")
    }

    // ── The right column ────────────────────────────────────────────────────────

    @Test
    fun `position, background and animation sit under the preview, beside the timer`() = announcementsTab { _, _ ->
        val preview = bounds(ANNOUNCEMENTS_PREVIEW_TAG)
        val position = bounds(screenPositionTag(Constants.CENTER))
        val text = bounds(ANNOUNCEMENTS_TEXT_BOX_TAG)
        val loopCount = loopCountIncrement().fetchSemanticsNode().boundsInRoot

        assertTrue(position.top >= preview.bottom, "position is under the preview: $position vs $preview")
        assertTrue(loopCount.top > position.bottom, "animation is under position: $loopCount vs $position")
        assertTrue(position.left > text.right, "and in the right column: $position vs $text")
    }

    @Test
    fun `a landscape output's preview is wider than it is tall`() = announcementsTab { _, _ ->
        val preview = bounds(ANNOUNCEMENTS_PREVIEW_TAG)
        assertTrue(preview.width > preview.height, "landscape: $preview")
    }

    @Test
    fun `a portrait output's preview fits above its controls instead of running off the bottom`() =
        announcementsTab(previewOutput = portraitOutput) { _, _ ->
            val preview = bounds(ANNOUNCEMENTS_PREVIEW_TAG)
            val position = bounds(screenPositionTag(Constants.CENTER))
            val loopCount = loopCountIncrement().fetchSemanticsNode().boundsInRoot
            val root = onRoot().fetchSemanticsNode().boundsInRoot

            assertTrue(preview.height > preview.width, "portrait: $preview")
            assertTrue(preview.bottom <= position.top, "the preview ends above the controls: $preview vs $position")
            assertTrue(loopCount.bottom <= root.bottom, "and the controls stay on screen: $loopCount vs $root")
        }

    private fun textHeight(dp: Int): (AppSettings) -> AppSettings = { s ->
        s.copy(
            maximizedLayout = s.maximizedLayout.copy(announcementsTextHeightDp = dp),
            windowedLayout = s.windowedLayout.copy(announcementsTextHeightDp = dp),
        )
    }

    /** A 1080x1920 browser source, as the app would pick it for this tab's preview. */
    private val portraitOutput = PreviewOutput(
        key = "browser-source-0",
        label = "Browser Source 1",
        size = OutputSize(1080, 1920),
        showsMode = true,
        assignment = ScreenAssignment(browserSourceWidth = 1080, browserSourceHeight = 1920),
    )
}

private const val DRAG_PX = 120f
private const val SAVED_TEXT_HEIGHT_DP = 160
private const val MIN_TIMER_CARD_DP = 200f

/** Measured sizes land on whole pixels and the saved height on whole dp. */
private const val SIZE_TOLERANCE = 1.5f
