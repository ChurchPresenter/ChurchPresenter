@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import kotlin.test.Test

/**
 * The main window's tab bar in each of its three label styles and at both ends of its spacing, with
 * the second tab selected so the indicator and the selected weight are in every shot. Wide enough
 * that the full tab list needs the overflow arrow, which is how the bar looks on a real display.
 */
class TabSectionScreenshotTest {

    private fun shoot(
        name: String,
        labelStyle: TabLabelStyle,
        visibleTabs: List<Tabs> = Tabs.entries,
        labelMargin: TabLabelMargin = TabLabelMargin.NORMAL,
    ) =
        captureComponent(SECTION, name) {
            Box(Modifier.width(WIDTH)) {
                TabSection(
                    visibleTabs = visibleTabs,
                    selectedTabIndex = 1,
                    labelStyle = labelStyle,
                    labelMargin = labelMargin,
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

    // The two ends of the spacing, over the same few tabs so the difference is the spacing alone.

    @Test
    fun `text with small spacing`() =
        shoot("text_spacing_small", TabLabelStyle.TEXT, SPACING_TABS, TabLabelMargin.SMALL)

    @Test
    fun `text with large spacing`() =
        shoot("text_spacing_large", TabLabelStyle.TEXT, SPACING_TABS, TabLabelMargin.LARGE)

    @Test
    fun `icons only with small spacing`() =
        shoot("icons_spacing_small", TabLabelStyle.ICONS, SPACING_TABS, TabLabelMargin.SMALL)

    @Test
    fun `icons only with large spacing`() =
        shoot("icons_spacing_large", TabLabelStyle.ICONS, SPACING_TABS, TabLabelMargin.LARGE)

    private companion object {
        val SPACING_TABS = listOf(Tabs.BIBLE, Tabs.SONGS, Tabs.PICTURES, Tabs.MEDIA, Tabs.WEB)
        const val SECTION = "tabSection"
        val WIDTH = 1100.dp
    }
}
