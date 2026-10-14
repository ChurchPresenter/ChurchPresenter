package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SongBackgroundPanelControlsTest {

    private val custom = SongBackground(type = SongBackgroundType.COLOR, color = "#2a1130")

    private fun panel(
        scopes: List<String> = emptyList(),
        body: ComposeUiTest.(full: List<SongBackground>, lower: List<SongBackground>) -> Unit,
    ) = runComposeUiTest {
        val full = mutableListOf<SongBackground>()
        val lower = mutableListOf<SongBackground>()
        setContent {
            MaterialTheme {
                Box(Modifier.size(SONG_BACKGROUND_PANEL_WIDTH, SONG_BACKGROUND_PANEL_HEIGHT)) {
                    SongBackgroundPanel(
                        background = custom,
                        lowerThirdBackground = custom,
                        onBackgroundChange = { full += it },
                        onLowerThirdBackgroundChange = { lower += it },
                        sampleLine = "Amazing grace",
                        onApplyToSongbook = null,
                        onDismiss = {},
                        scopes = scopes,
                    )
                }
            }
        }
        waitForIdle()
        body(full, lower)
    }

    @Test
    fun `Inherit after going to the lower third and back writes the full-screen half`() = panel { full, lower ->
        onNodeWithText("Lower third").performClick()
        waitForIdle()
        onNodeWithText("Full screen").performClick()
        waitForIdle()
        onNodeWithText("Inherit").performClick()
        waitForIdle()

        assertEquals(SongBackgroundType.INHERIT, full.single().type)
        assertTrue(lower.isEmpty())
    }

    @Test
    fun `a tap on the panel's own surface changes nothing`() = panel { full, lower ->
        onAllNodesWithText("BACKGROUND").onFirst().performClick()
        waitForIdle()

        assertTrue(full.isEmpty())
        assertTrue(lower.isEmpty())
    }

    @Test
    fun `a scope picked with no handler leaves the panel on the scope it was given`() =
        panel(scopes = listOf("Whole song", "Chorus")) { full, _ ->
            onNodeWithTag(SONG_BACKGROUND_SCOPE_TAG).performClick()
            waitForIdle()
            onAllNodesWithText("Chorus").onLast().performClick()
            waitForIdle()

            onAllNodesWithText("Whole song").onFirst().assertExists()
            assertTrue(full.isEmpty())
        }
}
