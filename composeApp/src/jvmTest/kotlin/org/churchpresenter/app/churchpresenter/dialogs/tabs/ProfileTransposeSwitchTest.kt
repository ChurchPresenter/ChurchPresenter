@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Profiles → Content switch that puts the musicians' transpose buttons on a Browser Source
 * page (issue #649): offered only where there are chords to move, and off until turned on.
 */
class ProfileTransposeSwitchTest {

    private val label = "Transpose buttons on Browser Source page"

    private val stage = OutputProfile(id = "stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)

    private fun page(profile: OutputProfile, block: ComposeUiTest.(() -> OutputProfile) -> Unit) {
        var current = profile
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    Column { ProfileContentPage(AppSettings(), current, onProfileChange = { current = it }) }
                }
            }
            block { current }
        }
    }

    private fun ComposeUiTest.offered(): Boolean =
        onAllNodesWithText(label).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    @Test
    fun `a stage monitor drawing chords offers the switch, off to begin with`() = page(stage) { current ->
        assertTrue(offered())
        assertEquals(false, current().showTransposeControls)
    }

    @Test
    fun `turning it on is stored on the profile`() = page(stage) { current ->
        onAllNodesWithText(label)[0].performClick()
        waitForIdle()
        assertTrue(current().showTransposeControls, "the switch should turn the transpose buttons on")
    }

    @Test
    fun `with chords off there is nothing to transpose, so no switch`() = page(stage.copy(showChords = false)) { _ ->
        assertTrue(!offered())
    }

    @Test
    fun `a full-screen profile has no chord chart and no switch`() =
        page(OutputProfile(id = "main", displayMode = Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            assertTrue(!offered())
        }
}
