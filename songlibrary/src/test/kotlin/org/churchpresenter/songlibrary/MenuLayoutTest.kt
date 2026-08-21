package org.churchpresenter.songlibrary

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How tall a dropped menu is allowed to be, which is what stops a long song book list being cut off. */
class MenuLayoutTest {

    @Test
    fun `a menu opened near the top gets the room under its button`() {
        assertEquals(500.dp - 70.dp - MENU_BOTTOM_MARGIN, menuMaxHeight(windowHeight = 500.dp, anchorBottom = 70.dp))
    }

    @Test
    fun `a tall window does not give a menu the run of it`() {
        assertEquals(MENU_MAX_HEIGHT, menuMaxHeight(windowHeight = 1600.dp, anchorBottom = 70.dp))
    }

    @Test
    fun `a menu never reaches below the bottom of the window`() {
        listOf(0.dp, 40.dp, 400.dp, 900.dp).forEach { anchor ->
            listOf(20.dp, 400.dp, 880.dp, 1600.dp).forEach { window ->
                val height = menuMaxHeight(window, anchor)
                assertTrue(height <= MENU_MAX_HEIGHT, "menu of $height")
                if (window > MENU_BOTTOM_MARGIN && anchor <= window) {
                    assertTrue(anchor + height <= window, "menu of $height under $anchor in a window of $window")
                }
            }
        }
    }

    @Test
    fun `an unmeasured window means not known yet, not no room`() {
        assertEquals(MENU_MAX_HEIGHT, menuMaxHeight(windowHeight = 0.dp, anchorBottom = 0.dp))
        assertEquals(MENU_MAX_HEIGHT, menuMaxHeight(windowHeight = MENU_BOTTOM_MARGIN, anchorBottom = 0.dp))
    }

    @Test
    fun `a button lower down leaves a shorter menu, never a taller one`() {
        val heights = listOf(0.dp, 100.dp, 300.dp, 600.dp).map { menuMaxHeight(700.dp, it) }
        assertEquals(heights.sortedDescending(), heights)
    }

    @Test
    fun `a button at the very bottom leaves no menu rather than one off the screen`() {
        assertEquals(0.dp, menuMaxHeight(windowHeight = 700.dp, anchorBottom = 700.dp))
    }
}
