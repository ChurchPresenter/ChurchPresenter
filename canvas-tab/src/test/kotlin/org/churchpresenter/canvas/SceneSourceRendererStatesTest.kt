@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The states a source renders in that are not its happy path: the QR payloads it encodes, and the
 * three different reasons a video or a camera can show nothing.
 *
 * The QR half matters because the payload is a wire format, not a label. A wifi code with the wrong
 * encryption token, or a password appended to an open network, is a code that scans cleanly and then
 * fails to join — which is discovered by a room full of people, not by looking at the screen.
 *
 * The video and camera half matters because all of those placeholders look alike at a glance and are
 * the only thing telling an operator *which* problem they have: a missing VLC, a broken VLC, an
 * unpicked file, a file that has moved.
 */
class SceneSourceRendererStatesTest {

    private fun ComposeUiTest.render(source: SceneSource, video: CanvasVideoSupport? = null) {
        setContent {
            MaterialTheme {
                if (video == null) {
                    SceneSourceRenderer(source, modifier = Modifier.size(120.dp).testTag("renderer"))
                } else {
                    CompositionLocalProvider(LocalCanvasVideoSupport provides video) {
                        SceneSourceRenderer(source, modifier = Modifier.size(120.dp).testTag("renderer"))
                    }
                }
            }
        }
    }

    private fun qr(
        contentType: String = "url",
        content: String = "https://example.org",
        ssid: String = "Sanctuary",
        password: String = "hunter2",
        encryption: String = "WPA",
        hidden: Boolean = false,
        errorCorrection: String = "M",
        transparent: Boolean = false,
    ) = SceneSource.QRCodeSource(
        id = "qr", name = "QR",
        contentType = contentType, content = content,
        wifiSsid = ssid, wifiPassword = password, wifiEncryption = encryption, wifiHidden = hidden,
        errorCorrection = errorCorrection, transparentBackground = transparent,
    )

    /** Renders [source] and returns how many pixels differ from the top-left one. */
    private fun ComposeUiTest.inkedPixels(source: SceneSource): Int {
        render(source)
        val map = onNodeWithTag("renderer").captureToImage().toPixelMap()
        val background = map[0, 0]
        var different = 0
        for (x in 0 until map.width) for (y in 0 until map.height) if (map[x, y] != background) different++
        return different
    }

    // ── The payload a wifi code carries ────────────────────────────────────────

    @Test
    fun `every encryption the picker offers encodes to a code with something in it`() {
        // WPA2 and WPA3 both go on the wire as "WPA" — the spec has no separate token for them, and
        // a code that says WPA3 is one no phone will join.
        listOf("WPA", "WPA2", "WPA3", "WEP", "nopass", "").forEach { encryption ->
            runComposeUiTest {
                val inked = inkedPixels(qr(contentType = "wifi", encryption = encryption))
                assertTrue(inked > 0, "a $encryption network must still produce a code")
            }
        }
    }

    @Test
    fun `an open network encodes differently from a secured one`() {
        var secured = 0
        var open = 0
        runComposeUiTest { secured = inkedPixels(qr(contentType = "wifi", encryption = "WPA")) }
        runComposeUiTest { open = inkedPixels(qr(contentType = "wifi", encryption = "nopass")) }

        assertNotEquals(secured, open, "the password must not be in an open network's code")
    }

    @Test
    fun `a hidden network encodes differently from a broadcast one`() {
        var broadcast = 0
        var hidden = 0
        runComposeUiTest { broadcast = inkedPixels(qr(contentType = "wifi", hidden = false)) }
        runComposeUiTest { hidden = inkedPixels(qr(contentType = "wifi", hidden = true)) }

        assertNotEquals(broadcast, hidden, "a hidden network needs its H:true flag or it will not join")
    }

    @Test
    fun `a url code does not carry the wifi fields`() {
        var url = 0
        var withCredentials = 0
        runComposeUiTest { url = inkedPixels(qr(contentType = "url", content = "https://example.org")) }
        runComposeUiTest {
            withCredentials = inkedPixels(
                qr(contentType = "url", content = "https://example.org", ssid = "Other", password = "secret")
            )
        }

        assertEquals(url, withCredentials, "a url code must encode the url and nothing else")
    }

    // ── Error correction ───────────────────────────────────────────────────────

    @Test
    fun `every error-correction level the picker offers renders`() {
        listOf("L", "M", "Q", "H", "unknown").forEach { level ->
            runComposeUiTest {
                assertTrue(inkedPixels(qr(errorCorrection = level)) > 0, "level $level produced no code")
            }
        }
    }

    @Test
    fun `a higher error-correction level makes a denser code`() {
        // More redundancy means more modules for the same payload. If the level were being ignored,
        // these two would come out identical.
        var low = 0
        var high = 0
        runComposeUiTest { low = inkedPixels(qr(errorCorrection = "L")) }
        runComposeUiTest { high = inkedPixels(qr(errorCorrection = "H")) }

        assertNotEquals(low, high, "the error-correction level is not reaching the encoder")
    }

    @Test
    fun `an unrecognised level falls back to the middle one rather than failing`() {
        var medium = 0
        var nonsense = 0
        runComposeUiTest { medium = inkedPixels(qr(errorCorrection = "M")) }
        runComposeUiTest { nonsense = inkedPixels(qr(errorCorrection = "banana")) }

        assertEquals(medium, nonsense)
    }

    @Test
    fun `a code with content too long to encode shows nothing rather than crashing`() {
        runComposeUiTest {
            render(qr(content = "x".repeat(10_000)))

            onNodeWithTag("renderer").assertExists()
        }
    }

    // ── Why a video is not playing ─────────────────────────────────────────────

    @Test
    fun `a broken VLC says so, rather than blaming the file`() {
        runComposeUiTest {
            render(
                SceneSource.VideoSource(id = "v", name = "V", filePath = "/nonexistent/clip.mp4"),
                video = CanvasVideoSupport(available = false, loadFailed = true),
            )

            onNodeWithText("VLC", substring = true).assertExists()
        }
    }

    @Test
    fun `a missing VLC is reported differently from a broken one`() {
        var broken: String? = null
        var missing: String? = null
        runComposeUiTest {
            render(
                SceneSource.VideoSource(id = "v", name = "V", filePath = ""),
                video = CanvasVideoSupport(available = false, loadFailed = true),
            )
            broken = renderedText().joinToString("|")
        }
        runComposeUiTest {
            render(
                SceneSource.VideoSource(id = "v", name = "V", filePath = ""),
                video = CanvasVideoSupport(available = false, loadFailed = false),
            )
            missing = renderedText().joinToString("|")
        }

        assertNotEquals(broken, missing, "install VLC and reinstall VLC are different instructions")
    }

    @Test
    fun `no file picked is reported differently from a file that has moved`() {
        var unpicked: String? = null
        var moved: String? = null
        runComposeUiTest {
            render(
                SceneSource.VideoSource(id = "v", name = "V", filePath = ""),
                video = CanvasVideoSupport(available = true, loadFailed = false),
            )
            unpicked = renderedText().joinToString("|")
        }
        runComposeUiTest {
            render(
                SceneSource.VideoSource(id = "v", name = "V", filePath = "/nonexistent/clip.mp4"),
                video = CanvasVideoSupport(available = true, loadFailed = false),
            )
            moved = renderedText().joinToString("|")
        }

        assertNotEquals(unpicked, moved)
        assertTrue(moved!!.contains("clip.mp4"), "the operator needs to be told which file is missing")
    }
}
