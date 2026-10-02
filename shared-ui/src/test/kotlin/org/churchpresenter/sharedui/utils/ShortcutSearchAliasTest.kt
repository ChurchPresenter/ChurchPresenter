package org.churchpresenter.sharedui.utils

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.shortcuts.KeyChord
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShortcutSearchAliasTest {

    private fun searchTextOf(key: Key): String {
        var text = ""
        runComposeUiTest {
            setContent { text = KeyChord(key.keyCode).searchText() }
            waitForIdle()
        }
        return text
    }

    @Test
    fun `the up arrow can be found by typing up`() {
        assertTrue("up" in searchTextOf(Key.DirectionUp))
    }

    @Test
    fun `the down arrow can be found by typing down`() {
        assertTrue("down" in searchTextOf(Key.DirectionDown))
    }

    @Test
    fun `the right arrow can be found by typing right`() {
        assertTrue("right" in searchTextOf(Key.DirectionRight))
    }
}
