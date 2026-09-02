@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Camera Capture card of the Projection settings tab.
 *
 * Unlike the VLC and NDI cards beside it, this one usually has nothing to ask for — the app ships
 * its own ffmpeg — so what it must get right is *saying which binary is in use* and offering the
 * download only when the search came up empty. Rendering alone would satisfy JaCoCo; every control
 * here is clicked and the result asserted instead.
 */
class ProjectionFfmpegCardTest {

    private companion object {
        val BUNDLED = FfmpegStatus(available = true, path = "/app/resources/ffmpeg", bundled = true)
        val CUSTOM = FfmpegStatus(available = true, path = "/opt/mine/ffmpeg", bundled = false)
        val MISSING = FfmpegStatus(available = false, path = "ffmpeg", bundled = false)
    }

    /**
     * Renders the card over mutable settings and hands [body] a way to read what it wrote.
     *
     * [status] is pinned, so nothing here depends on whether the machine running the suite has an
     * ffmpeg installed — which this repo's own developer machines do and CI does not.
     */
    private fun card(
        initial: AppSettings = AppSettings(),
        status: FfmpegStatus = BUNDLED,
        // Never the real implementations: a click reaching UrlOpener would launch this machine's
        // browser and one reaching SystemClipboard would take its clipboard, headless or not.
        openUrl: (String) -> Unit = {},
        copyText: (String) -> Unit = {},
        applied: MutableList<String> = mutableListOf(),
        body: ComposeUiTest.(read: () -> AppSettings) -> Unit,
    ) = runComposeUiTest {
        var current = initial
        setContent {
            Surface {
                Box(Modifier.fillMaxSize()) {
                    var state by remember { mutableStateOf(initial) }
                    FfmpegCard(
                        settings = state,
                        onSettingsChange = { transform ->
                            state = transform(state)
                            current = state
                        },
                        onProbe = { status },
                        onApply = { applied += it; status },
                        openUrl = openUrl,
                        copyText = copyText,
                    )
                }
            }
        }
        waitForIdle()
        body { current }
    }

    @Test
    fun `the bundled ffmpeg is named as ours rather than as a path`() {
        card(status = BUNDLED) { _ ->
            onNodeWithText("Using the ffmpeg included with ChurchPresenter").assertExists()
            onNodeWithText("ffmpeg was not found").assertDoesNotExist()
        }
    }

    @Test
    fun `an overridden ffmpeg says which one it is`() {
        card(status = CUSTOM) { _ ->
            onNodeWithText("Using ffmpeg at /opt/mine/ffmpeg").assertExists()
        }
    }

    @Test
    fun `only a failed search offers the download, and it can be copied as well as opened`() {
        var opened: String? = null
        var copied: String? = null
        card(status = MISSING, openUrl = { opened = it }, copyText = { copied = it }) { _ ->
            onNodeWithText("ffmpeg was not found").assertExists()

            onNodeWithText("Get ffmpeg").performClick()
            waitForIdle()
            assertEquals(FFMPEG_DOWNLOAD_URL, opened)

            // The OS decides where a browser opens, and on this app's usual two-screen setup that
            // is regularly the projection output — so the address must be reachable without one.
            onNodeWithContentDescription("Copy link").performClick()
            waitForIdle()
            assertEquals(FFMPEG_DOWNLOAD_URL, copied)
        }
    }

    @Test
    fun `a working ffmpeg offers neither the download nor its copy`() {
        card(status = BUNDLED) { _ ->
            onNodeWithText("Get ffmpeg").assertDoesNotExist()
            onNodeWithContentDescription("Copy link").assertDoesNotExist()
        }
    }

    @Test
    fun `clearing the override writes the empty path back to settings and re-resolves`() {
        val overridden = AppSettings(projectionSettings = ProjectionSettings(ffmpegPath = "/opt/mine/ffmpeg"))
        val applied = mutableListOf<String>()
        card(initial = overridden, status = CUSTOM, applied = applied) { read ->
            onNodeWithText("Use bundled").performClick()

            // The apply runs on Dispatchers.IO, which composition idling does not cover — so wait
            // for the positive signal that it happened rather than for a fixed moment.
            waitUntil { applied.isNotEmpty() }
            assertEquals("", read().projectionSettings.ffmpegPath, "empty means the bundled one")
            assertEquals(listOf(""), applied, "and the resolver is told, not only the settings file")
        }
    }

    @Test
    fun `there is nothing to clear until something has been chosen`() {
        card(status = BUNDLED) { _ ->
            onNodeWithText("Use bundled").assertDoesNotExist()
        }
    }

    @Test
    fun `checking again asks the machine about the saved path without changing it`() {
        val overridden = AppSettings(projectionSettings = ProjectionSettings(ffmpegPath = "/opt/mine/ffmpeg"))
        val applied = mutableListOf<String>()
        card(initial = overridden, status = CUSTOM, applied = applied) { read ->
            onNodeWithText("Check again").performClick()

            waitUntil { applied.isNotEmpty() }
            assertEquals(listOf("/opt/mine/ffmpeg"), applied, "the same binary, asked about again")
            assertEquals(
                "/opt/mine/ffmpeg",
                read().projectionSettings.ffmpegPath,
                "re-probing is not a change",
            )
        }
    }
}
