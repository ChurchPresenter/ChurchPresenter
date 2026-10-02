package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class SlideshowHideToggleTest {

    @Test
    fun `a shown tile offers to hide itself`() = runComposeUiTest {
        setContent { MaterialTheme { SlideshowHideToggle(hidden = false, position = 0, onToggle = {}) } }
        onNodeWithContentDescription("Hide — skipped by Next and the slideshow").assertExists()
    }

    @Test
    fun `a hidden tile offers to come back`() = runComposeUiTest {
        setContent { MaterialTheme { SlideshowHideToggle(hidden = true, position = 0, onToggle = {}) } }
        onNodeWithContentDescription("Show again in the slideshow").assertExists()
    }

    @Test
    fun `the eye is tagged with its tile's position`() = runComposeUiTest {
        setContent { MaterialTheme { SlideshowHideToggle(hidden = false, position = 7, onToggle = {}) } }
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 7).assertExists()
    }

    @Test
    fun `clicking the eye toggles the tile`() = runComposeUiTest {
        var toggles = 0
        setContent { MaterialTheme { SlideshowHideToggle(hidden = true, position = 2, onToggle = { toggles++ }) } }
        onNodeWithTag(SLIDESHOW_HIDE_TOGGLE_TAG + 2).apply {
            performMouseInput { moveTo(center) }
            performClick()
        }
        waitForIdle()
        assertEquals(1, toggles)
    }

    @Test
    fun `a hidden tile is badged as hidden`() = runComposeUiTest {
        setContent { MaterialTheme { HiddenBadge() } }
        onNodeWithText("Hidden").assertExists()
    }
}
