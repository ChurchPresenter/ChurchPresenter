package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainDesktopAutoFollowGateTest {

    @Test
    fun `the main window applies a detection while the operator is on another tab`() {
        assertTrue(
            shouldMainHandleAutoFollow(
                activeTabIndex = 4,
                bibleTabIndex = 1,
                slideContent = Presenting.BIBLE,
            ),
        )
    }

    @Test
    fun `the bible tab owns the detection while it is the active tab`() {
        assertFalse(
            shouldMainHandleAutoFollow(
                activeTabIndex = 1,
                bibleTabIndex = 1,
                slideContent = Presenting.BIBLE,
            ),
            "handling it here too would put the verse live twice and log it twice",
        )
    }

    @Test
    fun `auto-follow never takes the screen from other live content`() {
        listOf(
            Presenting.NONE,
            Presenting.LYRICS,
            Presenting.PRESENTATION,
            Presenting.MEDIA,
            Presenting.ANNOUNCEMENTS,
        ).forEach { mode ->
            assertFalse(
                shouldMainHandleAutoFollow(activeTabIndex = 4, bibleTabIndex = 1, slideContent = mode),
                "$mode is live, so a detected verse must not replace it",
            )
        }
    }

    @Test
    fun `with the bible tab hidden the main window handles it`() {
        assertTrue(
            shouldMainHandleAutoFollow(
                activeTabIndex = 0,
                bibleTabIndex = -1,
                slideContent = Presenting.BIBLE,
            ),
            "a hidden bible tab is not in the composition, so there is no handler to defer to",
        )
    }

    @Test
    fun `a hidden bible tab still does not override other live content`() {
        assertFalse(
            shouldMainHandleAutoFollow(
                activeTabIndex = 0,
                bibleTabIndex = -1,
                slideContent = Presenting.LYRICS,
            ),
        )
    }

    @Test
    fun `the bible tab being first does not make every tab look like it`() {
        assertTrue(
            shouldMainHandleAutoFollow(activeTabIndex = 0, bibleTabIndex = 1, slideContent = Presenting.BIBLE),
        )
        assertFalse(
            shouldMainHandleAutoFollow(activeTabIndex = 0, bibleTabIndex = 0, slideContent = Presenting.BIBLE),
        )
    }
}
