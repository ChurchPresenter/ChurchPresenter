@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import org.churchpresenter.sharedui.models.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals

internal class MainDesktopEffectsScopeTest : WiringScopeHarness() {

    @Test
    fun `a remembered tab index past the visible tabs is clamped and kept`() {
        val inputs = ScopeInputs(settings(), visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS), selectedTabIndex = 7)
        scoped(inputs, content = { LeadingEffects() }) { scope ->
            assertEquals(1, scope.state.selectedTabIndex)
            assertEquals(Tabs.SONGS, scope.currentTab)
        }
    }

    @Test
    fun `a tab index in range is left as it is`() {
        val inputs = ScopeInputs(settings(), visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS), selectedTabIndex = 0)
        scoped(inputs, content = { LeadingEffects() }) { scope ->
            assertEquals(0, scope.state.selectedTabIndex)
        }
    }

    @Test
    fun `every tab change publishes the index and the tab it lands on`() {
        val indexes = mutableListOf<Int>()
        val tabs = mutableListOf<Tabs>()
        val inputs = ScopeInputs(
            settings(),
            publish = MainDesktopPublishers(onTabChange = { indexes += it }, onCurrentTabChange = { tabs += it }),
            visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA),
        )
        scoped(inputs, content = {
            Box(Modifier.focusRequester(mainFocusRequester).focusable())
            TrailingEffects()
        }) { scope ->
            runOnIdle { scope.selectTab(Tabs.MEDIA) }
            waitForIdle()
            assertEquals(listOf(0, 2), indexes)
            assertEquals(listOf(Tabs.BIBLE, Tabs.MEDIA), tabs)
        }
    }
}
