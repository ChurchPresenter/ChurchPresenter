package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationModelTest {

    @Test
    fun `tabs keep their style while it fits, else drop to icons`() {
        assertEquals(TabStyle.IconAndText, fittedTabStyle(TabStyle.IconAndText, 500f, 600f))
        assertEquals(TabStyle.Icon, fittedTabStyle(TabStyle.IconAndText, 700f, 600f))
        assertEquals(TabStyle.Icon, fittedTabStyle(TabStyle.Text, 700f, 600f))
        assertEquals(TabStyle.Icon, fittedTabStyle(TabStyle.Icon, 700f, 600f))
    }

    @Test
    fun `the row scrolls only when even icons do not fit`() {
        assertTrue(tabsScroll(TabStyle.Icon, 400f, 300f))
        assertFalse(tabsScroll(TabStyle.Icon, 200f, 300f))
        assertFalse(tabsScroll(TabStyle.Text, 400f, 300f))
    }

    @Test
    fun `arrows move to the neighbour and wrap`() {
        val keys = listOf("bible", "songs", "media")
        assertEquals("songs", neighbourTab(keys, "bible", forward = true))
        assertEquals("bible", neighbourTab(keys, "media", forward = true))
        assertEquals("media", neighbourTab(keys, "bible", forward = false))
        assertNull(neighbourTab(keys, "web", forward = true))
    }
}
