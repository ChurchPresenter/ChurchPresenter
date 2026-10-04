@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcePropertiesThemeSwitchTest {

    private val flips = 3

    /** Every source editor, opened, then redrawn in the other theme a few times. */
    private fun switchThemeUnder(source: SceneSource) = withOsName(OS_WITHOUT_ENUMERATOR) {
        runComposeUiTest {
            var dark by mutableStateOf(false)
            var current = source
            setContent {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    SourcePropertiesPanel(
                        source = source,
                        appSettings = AppSettings(),
                        cameraHost = NO_CAMERAS,
                        onSourceUpdate = { current = it },
                    )
                }
            }
            waitForIdle()
            val before = renderedText()
            repeat(flips) {
                dark = !dark
                waitForIdle()
            }

            assertEquals(source, current, "a theme change edits nothing")
            assertEquals(before, renderedText(), "and every control still shows what it did")
        }
    }

    @Test
    fun `an image editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.image("img-theme"))

    @Test
    fun `a text editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.text("txt-theme"))

    @Test
    fun `a colour editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.color("col-theme"))

    @Test
    fun `a gradient editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.ColorSource(id = "grad-theme", name = "Wash", isGradient = true))

    @Test
    fun `a video editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.video("vid-theme"))

    @Test
    fun `a browser editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.browser("web-theme"))

    @Test
    fun `a shape editor with a gradient redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.ShapeSource(id = "shp-theme", name = "Box", isGradient = true))

    @Test
    fun `a clock editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.clock("clk-theme"))

    @Test
    fun `a countdown editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.ClockSource(id = "cd-theme", name = "Countdown", mode = "countdown"))

    @Test
    fun `a QR code editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.qr("qr-theme"))

    @Test
    fun `a WiFi QR code editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.QRCodeSource(id = "wifi-theme", name = "Guest WiFi", contentType = "wifi"))

    @Test
    fun `a camera editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.camera("cam-theme"))

    @Test
    fun `a screen capture editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.ScreenCaptureSource(id = "cap-theme", name = "Stage", captureMode = "window"))

    @Test
    fun `a Bible editor redraws in the other theme unchanged`() = switchThemeUnder(Fixture.bible("bib-theme"))

    @Test
    fun `an NDI editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.NdiSource(id = "ndi-theme", name = "NDI"))

    @Test
    fun `an OMT editor redraws in the other theme unchanged`() =
        switchThemeUnder(SceneSource.OmtSource(id = "omt-theme", name = "OMT"))
}
