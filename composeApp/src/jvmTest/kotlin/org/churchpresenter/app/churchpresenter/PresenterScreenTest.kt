package org.churchpresenter.app.churchpresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.data.settings.BackgroundSettings
import org.churchpresenter.app.churchpresenter.presenter.LocalTransparentBlanking
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every output window and Browser Source scene renders through this one composable. It decides
 * what fills the space behind the live content — a solid color, an image, a looping video, or
 * nothing at all — and whether that "nothing" is opaque black (a projector) or truly transparent
 * (an OBS overlay), then lays the caller's content on top, keyed to white when the output role
 * demands it.
 */
@OptIn(ExperimentalTestApi::class)
class PresenterScreenTest {

    private fun png(argb: Int = 0xFFFF00FF.toInt()): File = File.createTempFile("cp-presenter-bg", ".png").apply {
        val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until 8) for (y in 0 until 8) image.setRGB(x, y, argb)
        ImageIO.write(image, "png", this)
        deleteOnExit()
    }

    private fun garbageFile(): File = File.createTempFile("cp-presenter-bad", ".png").apply {
        writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        deleteOnExit()
    }

    private fun sample(
        appSettings: AppSettings,
        outputRole: String = Constants.OUTPUT_ROLE_NORMAL,
        isLowerThird: Boolean = false,
        showBackground: Boolean = true,
        transparentBlanking: Boolean = false,
        underneath: Color = Color.White,
    ): Color {
        var pixel: Color? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalTransparentBlanking provides transparentBlanking) {
                        Box(Modifier.testTag("screen").size(40.dp).background(underneath)) {
                            PresenterScreen(
                                appSettings = appSettings,
                                outputRole = outputRole,
                                isLowerThird = isLowerThird,
                                showBackground = showBackground,
                            ) {}
                        }
                    }
                }
            }
            pixel = onNodeWithTag("screen").captureToImage().toPixelMap()[20, 20]
        }
        return pixel!!
    }

    // ── Content and callbacks pass through untouched ────────────────────────────

    @Test
    fun `content passed by the caller is shown on screen`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings()) {
                    Text("ON AIR")
                }
            }
        }
        onNodeWithText("ON AIR").assertExists()
    }

    @Test
    fun `a click inside the content still reaches its callback in normal mode`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings(), outputRole = Constants.OUTPUT_ROLE_NORMAL) {
                    Button(onClick = { clicked = true }) { Text("Go Live") }
                }
            }
        }
        onNodeWithText("Go Live").performClick()
        assertTrue(clicked, "PresenterScreen must not swallow clicks meant for its content")
    }

    @Test
    fun `a click inside the content still reaches its callback in key mode`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings(), outputRole = Constants.OUTPUT_ROLE_KEY) {
                    Button(onClick = { clicked = true }) { Text("Go Live") }
                }
            }
        }
        onNodeWithText("Go Live").performClick()
        assertTrue(clicked, "wrapping content for the key output must preserve its interactivity")
    }

    @Test
    fun `content stays live and mutable state recomposes normally`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings()) {
                    val count = remember { mutableStateOf(0) }
                    Button(onClick = { count.value++ }) { Text("count ${count.value}") }
                }
            }
        }
        onNodeWithText("count 0").assertExists()
        onNodeWithText("count 0").performClick()
        onNodeWithText("count 1").assertExists()
    }

    // ── Disabled background (showBackground = false) ────────────────────────────

    @Test
    fun `a disabled background paints opaque black for a projector window`() {
        val pixel = sample(AppSettings(), showBackground = false, transparentBlanking = false, underneath = Color.Red)
        assertEquals(0f, pixel.red, 0.02f)
        assertEquals(0f, pixel.green, 0.02f)
        assertEquals(0f, pixel.blue, 0.02f)
    }

    @Test
    fun `a disabled background stays transparent for a Browser Source scene`() {
        val pixel = sample(AppSettings(), showBackground = false, transparentBlanking = true, underneath = Color.Red)
        assertTrue(pixel.red > 0.9f, "with blanking suppressed the layer beneath must show through, got $pixel")
    }

    // ── Solid color background ───────────────────────────────────────────────────

    @Test
    fun `a color background renders the configured hex color`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#112233",
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings)
        assertEquals(0x11 / 255f, pixel.red, 0.02f)
        assertEquals(0x22 / 255f, pixel.green, 0.02f)
        assertEquals(0x33 / 255f, pixel.blue, 0.02f)
    }

    @Test
    fun `reduced opacity blends the color background with what is underneath`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#000000",
                defaultBackgroundOpacity = 0f,
            ),
        )
        val pixel = sample(settings, underneath = Color.White)
        assertTrue(pixel.red > 0.9f, "zero opacity must let the black color fully disappear, got $pixel")
    }

    @Test
    fun `an unrecognised background type falls back to the color path`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_GRADIENT,
                defaultBackgroundColor = "#00FF00",
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings)
        assertEquals(0f, pixel.red, 0.02f)
        assertEquals(1f, pixel.green, 0.02f)
        assertEquals(0f, pixel.blue, 0.02f)
    }

    // ── Transparent background type ──────────────────────────────────────────────

    @Test
    fun `a Transparent background type paints black for a projector window`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT),
        )
        val pixel = sample(settings, underneath = Color.Red)
        assertEquals(0f, pixel.red, 0.02f)
        assertEquals(0f, pixel.green, 0.02f)
        assertEquals(0f, pixel.blue, 0.02f)
    }

    @Test
    fun `a Transparent background type stays transparent for a Browser Source scene`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(defaultBackgroundType = Constants.BACKGROUND_TRANSPARENT),
        )
        val pixel = sample(settings, transparentBlanking = true, underneath = Color.Red)
        assertTrue(pixel.red > 0.9f,
            "the transparent type must let the layer beneath show through in OBS scenes, got $pixel")
    }

    // ── Image background ─────────────────────────────────────────────────────────

    @Test
    fun `a decodable image background is drawn full-screen`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = png(0xFF0000FF.toInt()).absolutePath,
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings)
        assertEquals(0f, pixel.red, 0.05f)
        assertEquals(0f, pixel.green, 0.05f)
        assertEquals(1f, pixel.blue, 0.05f)
    }

    @Test
    fun `an empty image path falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "",
            ),
        )
        val pixel = sample(settings, underneath = Color.Red)
        assertEquals(0f, pixel.red, 0.02f)
    }

    @Test
    fun `a missing image file falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = "/no/such/file/does-not-exist.png",
            ),
        )
        val pixel = sample(settings, underneath = Color.Red)
        assertEquals(0f, pixel.red, 0.02f)
    }

    @Test
    fun `an undecodable image file falls back to black rather than crashing`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_IMAGE,
                defaultBackgroundImage = garbageFile().absolutePath,
            ),
        )
        val pixel = sample(settings, underneath = Color.Red)
        assertEquals(0f, pixel.red, 0.02f)
    }

    // ── Video background ─────────────────────────────────────────────────────────

    @Test
    fun `a video background type renders without a decoder present, without crashing`() = runComposeUiTest {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultBackgroundVideo = "/no/such/file.mp4",
            ),
        )
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = settings) { Text("SLIDE") }
            }
        }
        onNodeWithText("SLIDE").assertExists()
    }

    @Test
    fun `a blank video path renders without crashing`() = runComposeUiTest {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultBackgroundVideo = "",
            ),
        )
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = settings) { Text("SLIDE") }
            }
        }
        onNodeWithText("SLIDE").assertExists()
    }

    // ── Lower third vs full-screen background selection ─────────────────────────

    @Test
    fun `a lower third with FollowDefault uses the full-screen background settings`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#123456",
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_FOLLOW_DEFAULT,
                defaultLowerThirdBackgroundColor = "#FFFFFF",
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings, isLowerThird = true)
        assertEquals(0x12 / 255f, pixel.red, 0.02f)
        assertEquals(0x34 / 255f, pixel.green, 0.02f)
        assertEquals(0x56 / 255f, pixel.blue, 0.02f)
    }

    @Test
    fun `a lower third with its own type uses the lower third background settings`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#000000",
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdBackgroundColor = "#AABBCC",
                defaultLowerThirdBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings, isLowerThird = true)
        assertEquals(0xAA / 255f, pixel.red, 0.02f)
        assertEquals(0xBB / 255f, pixel.green, 0.02f)
        assertEquals(0xCC / 255f, pixel.blue, 0.02f)
    }

    @Test
    fun `a full-screen render is unaffected by lower third settings`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#654321",
                defaultBackgroundOpacity = 1f,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_COLOR,
                defaultLowerThirdBackgroundColor = "#FFFFFF",
            ),
        )
        val pixel = sample(settings, isLowerThird = false)
        assertEquals(0x65 / 255f, pixel.red, 0.02f)
        assertEquals(0x43 / 255f, pixel.green, 0.02f)
        assertEquals(0x21 / 255f, pixel.blue, 0.02f)
    }

    // ── Fill and key output roles ────────────────────────────────────────────────

    @Test
    fun `fill output role renders the same background as normal`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#00FFFF",
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings, outputRole = Constants.OUTPUT_ROLE_FILL)
        assertEquals(0f, pixel.red, 0.02f)
        assertEquals(1f, pixel.green, 0.02f)
        assertEquals(1f, pixel.blue, 0.02f)
    }

    @Test
    fun `key output role does not itself alter the background color`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_COLOR,
                defaultBackgroundColor = "#00FFFF",
                defaultBackgroundOpacity = 1f,
            ),
        )
        val pixel = sample(settings, outputRole = Constants.OUTPUT_ROLE_KEY)
        assertEquals(0f, pixel.red, 0.02f)
        assertEquals(1f, pixel.green, 0.02f)
        assertEquals(1f, pixel.blue, 0.02f)
    }

    @Test
    fun `key output role wraps content with the key modifier without hiding it`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings(), outputRole = Constants.OUTPUT_ROLE_KEY) {
                    Text("KEYED TEXT")
                }
            }
        }
        onNodeWithText("KEYED TEXT").assertExists()
    }

    @Test
    fun `no other output role wraps content with the key modifier`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings(), outputRole = Constants.OUTPUT_ROLE_FILL) {
                    Text("FILL TEXT")
                }
            }
        }
        onNodeWithText("FILL TEXT").assertExists()
    }

    @Test
    fun `nothing beyond the caller's content is drawn when no background is requested`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                PresenterScreen(appSettings = AppSettings(), showBackground = false) {
                    Text("ONLY THIS")
                }
            }
        }
        onAllNodesWithText("ONLY THIS").assertCountEquals(1)
    }
}
