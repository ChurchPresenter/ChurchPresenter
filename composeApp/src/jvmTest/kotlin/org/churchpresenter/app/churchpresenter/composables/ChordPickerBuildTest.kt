@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChordPickerBuildTest {

    private fun ComposeUiTest.tap(text: String) {
        onAllNodesWithText(text)[0].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.has(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `folded until asked, then a root, a type and a spelling build the chord inserted`() = runComposeUiTest {
        val inserted = mutableListOf<String>()
        setContent { MaterialTheme { ChordPicker(songKey = "G", flats = false) { inserted += it } } }
        waitForIdle()
        assertTrue(!has("maj"), "the picker starts folded")

        tap("BUILD A CHORD")
        tap("Insert G")
        tap("A")
        tap("m7♭5")
        tap("Insert Am7b5")
        tap("♭")
        tap("Bb")
        tap("maj")
        tap("Insert Bb")
        tap("♯")
        tap("Insert A#")

        assertEquals(listOf("G", "Am7b5", "Bb", "A#"), inserted)

        tap("BUILD A CHORD")
        assertTrue(!has("Insert A#"), "folded away again")
    }

    @Test
    fun `a new key starts the picker on that key's root and spelling`() = runComposeUiTest {
        var key by mutableStateOf("G")
        val inserted = mutableListOf<String>()
        setContent { MaterialTheme { ChordPicker(songKey = key, flats = key == "Eb") { inserted += it } } }
        waitForIdle()
        tap("BUILD A CHORD")

        key = "Eb"
        waitForIdle()
        onNodeWithText("Insert Eb").performClick()
        waitForIdle()
        key = "not a key"
        waitForIdle()
        tap("Insert C")

        assertEquals(listOf("Eb", "C"), inserted)
    }

    @Test
    fun `a chord type reads as music writes it`() {
        assertEquals("maj", qualityLabel("", "maj"))
        assertEquals("m7♭5", qualityLabel("m7b5", "maj"))
        assertEquals("sus4", qualityLabel("sus4", "maj"))
    }
}
