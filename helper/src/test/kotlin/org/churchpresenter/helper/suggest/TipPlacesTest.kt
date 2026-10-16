package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.HelperText
import org.churchpresenter.sharedui.guide.SettingsPage
import org.churchpresenter.sharedui.guide.WickPlace
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TipPlacesTest {

    private val tips = allTips(ShortcutMap.DEFAULT)
    private val general = tips.filter { it.tabs.isEmpty() }

    private fun stepping(place: WickPlace, day: Int = 0) =
        (0 until tips.size * 2).mapNotNull { tipFor(tips, place, day, it) }

    @Test
    fun `a tab's own tip is the day's tip there, whichever day it is`() {
        (0..20).forEach { day ->
            assertTrue(Tabs.BIBLE in assertNotNull(tipFor(tips, WickPlace.Main(Tabs.BIBLE), day, 0)).tabs)
        }
    }

    @Test
    fun `stepping on a tab goes on through the general tips, never another tab's`() {
        val seen = stepping(WickPlace.Main(Tabs.BIBLE))
        assertTrue(seen.toSet().containsAll(general))
        assertTrue(seen.all { it.tabs.isEmpty() || Tabs.BIBLE in it.tabs })
    }

    @Test
    fun `a tab with no tips of its own rotates the general ones`() {
        (0..5).forEach { day ->
            assertEquals(tipAt(general, day + 2), tipFor(tips, WickPlace.Main(Tabs.CANVAS), day, 2))
            assertEquals(tipAt(general, day), tipFor(tips, WickPlace.Main(null), day, 0))
        }
    }

    @Test
    fun `a Settings page offers only its own tips`() {
        val seen = stepping(WickPlace.Settings(SettingsPage.PROFILES))
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { SettingsPage.PROFILES in it.pages })
    }

    @Test
    fun `a place without tips offers none`() {
        assertNull(tipFor(tips, WickPlace.Settings(SettingsPage.ATEM), 0, 0))
        assertNull(tipFor(tips, WickPlace.Settings(null), 0, 0))
        assertNull(tipFor(tips, WickPlace.Other, 0, 0))
    }

    @Test
    fun `an untagged tip, as a pack's are, is for the main window only`() {
        val packTip = Tip(HelperText.Plain("from a pack"))
        assertEquals(packTip, tipFor(listOf(packTip), WickPlace.Main(Tabs.SONGS), 0, 0))
        assertNull(tipFor(listOf(packTip), WickPlace.Settings(SettingsPage.PROFILES), 0, 0))
    }
}
