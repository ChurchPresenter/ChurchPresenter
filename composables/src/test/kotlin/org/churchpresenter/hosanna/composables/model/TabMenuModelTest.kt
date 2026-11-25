package org.churchpresenter.hosanna.composables.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabMenuModelTest {

    private val keys = listOf("bible", "songs", "media")

    @Test
    fun `a tab moves up or down and stays put at the ends`() {
        assertEquals(listOf("songs", "bible", "media"), moveTab(keys, "songs", -1))
        assertEquals(listOf("bible", "media", "songs"), moveTab(keys, "songs", 1))
        assertEquals(keys, moveTab(keys, "bible", -1))
        assertEquals(keys, moveTab(keys, "media", 1))
        assertEquals(keys, moveTab(keys, "web", 1))
    }

    @Test
    fun `the last visible tab cannot be hidden, a hidden one can always come back`() {
        assertTrue(canHideTab("bible", keys, emptySet()))
        assertFalse(canHideTab("bible", keys, setOf("songs", "media")))
        assertTrue(canHideTab("songs", keys, setOf("songs", "media")))
    }
}
