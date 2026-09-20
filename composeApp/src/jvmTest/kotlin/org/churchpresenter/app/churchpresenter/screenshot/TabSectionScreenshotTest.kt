@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.settings.TabLabelStyle
import kotlin.test.Test

/**
 * The main window's tab bar in each of its three label styles, with the second tab selected so the
 * indicator and the selected weight are in every shot. Wide enough that the full tab list needs
 * the overflow arrow, which is how the bar looks on a real display.
 */
class TabSectionScreenshotTest {

    private fun shoot(name: String, labelStyle: TabLabelStyle, visibleTabs: List<Tabs> = Tabs.entries) =
        captureComponent(SECTION, name) {
            Box(Modifier.width(WIDTH)) {
                TabSection(
                    visibleTabs = visibleTabs,
                    selectedTabIndex = 1,
                    labelStyle = labelStyle,
                    onTabSelected = {},
                )
            }
        }

    @Test
    fun `text only`() = shoot("text", TabLabelStyle.TEXT)

    @Test
    fun `icons and text`() = shoot("icons_and_text", TabLabelStyle.ICONS_AND_TEXT)

    @Test
    fun `icons only`() = shoot("icons", TabLabelStyle.ICONS)

    /** Few enough tabs to fit: no arrows, and the minimum tab width is what spaces the icons. */
    @Test
    fun `icons only with room to spare`() =
        shoot("icons_short", TabLabelStyle.ICONS, visibleTabs = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.MEDIA))

    private companion object {
        const val SECTION = "tabSection"
        val WIDTH = 1100.dp
    }
}
