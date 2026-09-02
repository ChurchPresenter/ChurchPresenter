@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import org.churchpresenter.app.churchpresenter.dialogs.tabs.FfmpegCard
import org.churchpresenter.app.churchpresenter.dialogs.tabs.FfmpegStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test

/**
 * The Camera Capture card, in each of the three states it can report.
 *
 * Shot on its own rather than through the Projection tab because the tab's images are clipped well
 * above it — the card sits below the NDI one on a scrolling page, so a reviewer would never see it
 * there. Its states are also the whole point of it: the app ships an ffmpeg, so "which binary is
 * this using" is the question the card exists to answer, and each answer reads differently.
 */
class ProjectionFfmpegCardScreenshotTest {

    private companion object {
        const val SECTION = "projectionFfmpegCard"
    }

    @Test
    fun `the bundled ffmpeg`() {
        captureComponent(SECTION, "bundled") {
            FfmpegCard(
                settings = AppSettings(),
                onSettingsChange = {},
                onProbe = { FfmpegStatus(available = true, path = "/app/ffmpeg", bundled = true) },
            )
        }
    }

    @Test
    fun `an operators own ffmpeg`() {
        captureComponent(SECTION, "overridden") {
            FfmpegCard(
                settings = AppSettings(
                    projectionSettings = ProjectionSettings(ffmpegPath = "/opt/homebrew/bin/ffmpeg"),
                ),
                onSettingsChange = {},
                onProbe = {
                    FfmpegStatus(available = true, path = "/opt/homebrew/bin/ffmpeg", bundled = false)
                },
            )
        }
    }

    @Test
    fun `no ffmpeg anywhere`() {
        captureComponent(SECTION, "missing") {
            FfmpegCard(
                settings = AppSettings(),
                onSettingsChange = {},
                onProbe = { FfmpegStatus(available = false, path = "ffmpeg", bundled = false) },
            )
        }
    }
}
