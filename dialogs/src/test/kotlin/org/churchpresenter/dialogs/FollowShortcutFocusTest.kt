@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.models.ShortcutAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Wick naming a shortcut opens its category in the Keyboard Shortcuts window, every filter dropped. */
class FollowShortcutFocusTest {

    private val take = ShortcutAction.TAKE

    @Test
    fun `a named shortcut selects its category and drops the search, once`() = runComposeUiTest {
        val session = GuideSession().apply { shortcutFocus = take.name }
        val filter = ShortcutFilter().apply {
            query = "lyrics"
            selectedScope = null
        }
        setContent { CompositionLocalProvider(LocalGuideSession provides session) { FollowShortcutFocus(filter) } }
        waitForIdle()
        assertEquals(take.scope, filter.selectedScope)
        assertEquals("", filter.query)
        assertNull(session.shortcutFocus)
    }

    @Test
    fun `a name this build does not have is let go and changes nothing`() = runComposeUiTest {
        val session = GuideSession().apply { shortcutFocus = "GONE" }
        val filter = ShortcutFilter().apply { query = "lyrics" }
        setContent { CompositionLocalProvider(LocalGuideSession provides session) { FollowShortcutFocus(filter) } }
        waitForIdle()
        assertEquals("lyrics", filter.query)
        assertNull(session.shortcutFocus)
    }

    @Test
    fun `with nothing named, or no helper running, nothing changes`() = runComposeUiTest {
        val filter = ShortcutFilter().apply { query = "lyrics" }
        setContent {
            CompositionLocalProvider(LocalGuideSession provides GuideSession()) { FollowShortcutFocus(filter) }
            FollowShortcutFocus(filter)
        }
        waitForIdle()
        assertEquals("lyrics", filter.query)
    }
}
