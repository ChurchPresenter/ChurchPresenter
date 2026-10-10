@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** What Wick asks of the main screen: a tab switched to, and a verse found through the Bible tab. */
class MainDesktopHelperFlowsTest : MainDesktopComposeHarness() {

    @Test
    fun `Wick switches tabs, and a verse it shows opens the Bible tab first`() {
        val flows = Flows()
        val wiring = Wiring()
        root(flows = flows, wiring = wiring) {
            val before = wiring.tabChanges.size
            flows.selectTab.tryEmit(Tabs.SONGS)
            waitForIdle()
            assertTrue(wiring.tabChanges.size > before, "the tab Wick named was selected")
            val songs = wiring.tabChanges.last()
            flows.showReference.tryEmit("John 3:16")
            waitForIdle()
            assertNotEquals(songs, wiring.tabChanges.last(), "the Bible tab was opened for the verse")
        }
    }
}
