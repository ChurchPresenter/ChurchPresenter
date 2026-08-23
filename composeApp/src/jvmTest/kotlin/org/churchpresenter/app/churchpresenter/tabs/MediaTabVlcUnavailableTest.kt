@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * What the Media tab says when VLC cannot be used — which is the entire tab in that state, and the
 * state a CI runner is always in.
 *
 * There are three distinct reasons playback can be unavailable and each gets its own instruction,
 * because the fix differs: install it, install the *right architecture* of it, or repair a broken
 * install. Showing "please install VLC" to someone who has VLC installed but on the wrong architecture
 * sends them round a loop they cannot get out of — they already did the thing the message asks for.
 *
 * Reachable deterministically only because the tab takes the VLC state as parameters; the underlying
 * globals cache a process-wide answer off the host machine. See `MediaTabTestSupport.kt`.
 */
class MediaTabVlcUnavailableTest {

    @Test
    fun `with VLC missing the tab explains that playback needs it`() =
        mediaTab(vlcAvailable = false) { _, _ ->
            onNodeWithText(MediaLabel.VLC_REQUIRED).assertIsDisplayed()
        }

    @Test
    fun `a plain missing install is told where to get it`() =
        mediaTab(vlcAvailable = false) { _, _ ->
            onNodeWithText(MediaLabel.VLC_INSTALL).assertIsDisplayed()
        }

    @Test
    fun `an architecture mismatch says so instead of asking for a reinstall`() =
        mediaTab(vlcAvailable = false, vlcArchMismatch = true) { _, _ ->
            // Someone on Apple Silicon with an Intel-only VLC has already installed it — repeating
            // "please install VLC" would send them round the same loop.
            assertTrue(
                showsContainingText("Apple Silicon"),
                "the arch mismatch must be named: ${renderedText()}",
            )
            onNodeWithText(MediaLabel.VLC_INSTALL).assertDoesNotExist()
        }

    @Test
    fun `a failed load asks for a repair rather than an install`() =
        mediaTab(vlcAvailable = false, vlcLoadFailed = true) { _, _ ->
            assertTrue(
                showsContainingText("could not be loaded"),
                "a broken install must be distinguished from a missing one: ${renderedText()}",
            )
            onNodeWithText(MediaLabel.VLC_INSTALL).assertDoesNotExist()
        }

    @Test
    fun `an architecture mismatch takes precedence over a load failure`() =
        mediaTab(vlcAvailable = false, vlcArchMismatch = true, vlcLoadFailed = true) { _, _ ->
            // A wrong-architecture VLC also fails to load, so both flags are set at once and the
            // order of the `when` decides which advice is given. The arch message is the actionable
            // one; "try reinstalling" would not fix it.
            assertTrue(showsContainingText("Apple Silicon"), renderedText().toString())
            assertTrue(!showsContainingText("could not be loaded"), renderedText().toString())
        }

    @Test
    fun `the media controls are not offered at all while VLC is unusable`() =
        mediaTab(vlcAvailable = false) { _, _ ->
            // The whole tab is replaced by the message — offering a Load button that cannot work
            // would just produce a second, more confusing failure.
            onNodeWithText(MediaLabel.LOCAL_FILE).assertDoesNotExist()
            onNodeWithText(MediaLabel.NETWORK_URL).assertDoesNotExist()
            assertTrue(!hasMediaButton(MediaLabel.ADD_TO_SCHEDULE))
        }

    @Test
    fun `with VLC present the tab shows its controls instead of the warning`() =
        mediaTab(vlcAvailable = true) { _, _ ->
            // The mirror of the test above — together they prove the parameter selects a branch
            // rather than the tab looking the same either way.
            onNodeWithText(MediaLabel.VLC_REQUIRED).assertDoesNotExist()
            onNodeWithText(MediaLabel.LOCAL_FILE).assertExists()
        }
}
