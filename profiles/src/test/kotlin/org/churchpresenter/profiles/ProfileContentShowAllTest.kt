@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileContentShowAllTest {

    @Test
    fun `Show all keeps the language picked, Hide all turns it off, and on again is every language`() =
        runSkikoComposeUiTest(size = Size(1000f, 3000f), density = Density(1f)) {
            var profile by mutableStateOf(
                OutputProfile(
                    id = "p",
                    displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR,
                    bibleMode = Constants.SONG_LANG_PRIMARY,
                    songMode = Constants.SONG_LANG_SECONDARY,
                    songLookAhead = false,
                ),
            )
            setContent {
                MaterialTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        ProfileContentPage(AppSettings(), profile) { profile = it }
                    }
                }
            }
            onNodeWithText("Show all").performClick()
            waitForIdle()
            assertEquals(Constants.SONG_LANG_PRIMARY, profile.bibleMode)
            assertEquals(Constants.SONG_LANG_SECONDARY, profile.songMode)
            assertTrue(profile.songLookAhead)

            onNodeWithText("Hide all").performClick()
            waitForIdle()
            assertEquals(Constants.SONG_LANG_OFF, profile.bibleMode)
            assertEquals(Constants.SONG_LANG_OFF, profile.songMode)
            assertFalse(profile.songLookAhead)

            onNodeWithText("Show all").performClick()
            waitForIdle()
            assertEquals(Constants.SONG_LANG_BOTH, profile.bibleMode)
            assertEquals(Constants.SONG_LANG_BOTH, profile.songMode)
            assertFalse(profile.songLookAhead, "look-ahead was not offered while songs were off")
        }
}
