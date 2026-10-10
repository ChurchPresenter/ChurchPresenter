@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SongBackgroundButtonStatesTest {

    private val navy = SongBackground(type = SongBackgroundType.COLOR, color = "#101830")
    private val wine = SongBackground(type = SongBackgroundType.COLOR, color = "#401020")

    private class State {
        var background by mutableStateOf(SongBackground())
        var lowerThird by mutableStateOf(SongBackground())
        var expanded by mutableStateOf(false)
        var scope by mutableIntStateOf(0)
    }

    private fun button(scopes: List<String>, body: ComposeUiTest.(State) -> Unit) = runComposeUiTest {
        val state = State()
        setContent {
            MaterialTheme {
                SongBackgroundButton(
                    background = state.background,
                    lowerThirdBackground = state.lowerThird,
                    expanded = state.expanded,
                    onExpandedChange = { state.expanded = it },
                    onBackgroundChange = { state.background = it },
                    onLowerThirdBackgroundChange = { state.lowerThird = it },
                    sampleLine = "Amazing grace",
                    onApplyToSongbook = {},
                    scopes = scopes,
                    scopeIndex = state.scope,
                    onScopeChange = { state.scope = it },
                )
            }
        }
        waitForIdle()
        body(state)
    }

    @Test
    fun `the chip opens and closes its panel whichever background is the song's own`() = button(emptyList()) { state ->
        listOf(
            SongBackground() to SongBackground(),
            navy to SongBackground(),
            SongBackground() to wine,
            navy to wine,
        ).forEach { (full, band) ->
            state.background = full
            state.lowerThird = band
            waitForIdle()
            onNodeWithTag(SONG_BACKGROUND_BUTTON_TAG).performClick()
            waitForIdle()
            assertTrue(state.expanded, "$full / $band")
            onNodeWithTag(SONG_BACKGROUND_SAVE_TAG).performClick()
            waitForIdle()
            assertFalse(state.expanded)
            assertEquals(full to band, state.background to state.lowerThird, "Save keeps what is there")
        }
    }

    @Test
    fun `Cancel puts back the lower third background too, for the section being edited`() =
        button(listOf("Whole song", "Verse 1")) { state ->
            state.lowerThird = wine
            state.expanded = true
            waitForIdle()
            state.lowerThird = navy
            state.background = navy
            waitForIdle()
            assertEquals(1, onAllNodesWithTag(SONG_BACKGROUND_CANCEL_TAG).fetchSemanticsNodes().size)
            onNodeWithTag(SONG_BACKGROUND_CANCEL_TAG).performClick()
            waitForIdle()
            assertEquals(wine, state.lowerThird)
            assertEquals(SongBackground(), state.background)
            assertFalse(state.expanded)
        }

    @Test
    fun `a colour typed without its hash gets one, and is cut to a hex colour's length`() = button(emptyList()) { state ->
        state.background = navy
        state.expanded = true
        waitForIdle()
        onNode(hasSetTextAction() and hasText("#101830")).performTextReplacement(" abcdef12 ")
        waitForIdle()
        assertEquals(SongBackground(type = SongBackgroundType.COLOR, color = "#abcdef"), state.background)
    }

    @Test
    fun `the panel's scope picker moves the edit to another section`() = button(listOf("Whole song", "Verse 1")) { state ->
        state.expanded = true
        waitForIdle()
        onNodeWithTag(SONG_BACKGROUND_SCOPE_TAG).performClick()
        waitForIdle()
        val verse = onAllNodesWithText("Verse 1")
        verse[verse.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
        assertEquals(1, state.scope)
    }
}
