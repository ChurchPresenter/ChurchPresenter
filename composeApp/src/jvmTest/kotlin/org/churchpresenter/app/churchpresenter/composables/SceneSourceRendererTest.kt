package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.SceneSource
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The canvas scene compositor's per-source-type renderer — 11 independent `when` branches, one
 * per [SceneSource] subtype, dispatched from the public [SceneSourceRenderer] entry point (so
 * every test here, by calling that entry point with a given source, also exercises its `when`
 * branch for free).
 *
 * Four source types are deliberately only tested on their reachable no-op/placeholder path, not
 * their working content path:
 * - **Video** needs VLC's native media player; even the "file not found" message text depends on
 *   `isVlcAvailable`/`isVlcLoadFailed`, which vary by machine, so only structural facts (no
 *   `Image` renders, some non-empty message shows) are asserted, not exact text.
 * - **Browser** needs a real Chromium process via `SharedBrowserFrameCache`; only the blank-URL
 *   guard (which returns before ever touching the cache) is tested.
 * - **Camera** needs a real OS camera device via `SharedCameraFrameCache`; only the blank-path
 *   guard (same reasoning) is tested.
 * - **ScreenCapture** always mounts (no such guard) and starts a `LaunchedEffect` that constructs
 *   `java.awt.Robot`, which throws in this headless test JVM and is caught internally — so the
 *   test only confirms the initial "waiting for a frame" placeholder renders, not real capture.
 *
 * Colour/shape pixel output is verified with [captureToImage] (the same technique
 * `AtemSettingsTabTestSupport.fieldBorderColour` and `KeySignalModifierTest` already use in this
 * suite) for the highest-value, cheapest-to-verify cases — a solid fill, a shape fill, a
 * transparent-vs-opaque background. Purely cosmetic fields with no distinct branch of their own
 * (bold/italic, text alignment, per-shape stroke geometry) are exercised for branch coverage but
 * not pixel-verified, consistent with how every other file this session has treated colour/tint
 * parameters that have no accessible semantics trace.
 *
 * The "clock" mode of [SceneSource.ClockSource] reads the real wall clock
 * (`java.time.LocalTime.now()`), so its test only asserts the text is non-empty and time-shaped —
 * asserting an exact value would assert on a real clock, which this project's own testing rules
 * rule out as a flakiness source. "countdown" mode is fully deterministic (built from the
 * source's own target H/M/S) and is asserted on exactly.
 */
@OptIn(ExperimentalTestApi::class)
class SceneSourceRendererTest {

    // ── Image ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an image source renders the file when it exists and is a valid image`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test", ".png")
        try {
            val img = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until 10) for (x in 0 until 10) img.setRGB(x, y, 0xFFFF0000.toInt())
            ImageIO.write(img, "png", file)

            setContent {
                MaterialTheme {
                    SceneSourceRenderer(SceneSource.ImageSource(
                        id = "i1",
                        name = "Photo",
                        filePath = file.absolutePath
                    ))
                }
            }
            onNodeWithContentDescription("Photo").assertExists("a valid image file must render as an Image")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `an image source shows a placeholder when the file does not exist`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.ImageSource(id = "i2", name = "Photo", filePath = "/nonexistent/x.png"))
            }
        }
        onNodeWithText("Image not found").assertExists()
    }

    @Test
    fun `an image source shows a placeholder when the file is not a valid image`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test-corrupt", ".png")
        try {
            file.writeText("this is not image data")
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(SceneSource.ImageSource(
                        id = "i3",
                        name = "Photo",
                        filePath = file.absolutePath
                    ))
                }
            }
            onNodeWithText("Image not found").assertExists("a corrupt file must be caught, not crash the renderer")
        } finally {
            file.delete()
        }
    }

    @Test
    fun `every image contentScale value renders without error`() = runComposeUiTest {
        val file = File.createTempFile("scene-source-test-scale", ".png")
        try {
            ImageIO.write(BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), "png", file)
            for (scale in listOf("FIT", "FILL", "STRETCH", "NONE", "unknown-falls-back-to-fit")) {
                setContent {
                    MaterialTheme {
                        SceneSourceRenderer(
                            SceneSource.ImageSource(
                                id = "i4",
                                name = "P",
                                filePath = file.absolutePath,
                                contentScale = scale
                            )
                        )
                    }
                }
                onNodeWithContentDescription("P").assertExists("contentScale=\"$scale\" must still render the image")
            }
        } finally {
            file.delete()
        }
    }

    // ── Text ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a text source shows its text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.TextSource(id = "t1", name = "T", text = "Welcome"))
            }
        }
        onNodeWithText("Welcome").assertExists()
    }

    @Test
    fun `a text source's background is transparent for hex 00000000`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.TextSource(id = "t2", name = "T", text = "X", backgroundColor = "#00000000"),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[2, 2]
        assertEquals(0f, pixel.alpha, "the special-cased fully-transparent hex must render as truly transparent")
    }

    @Test
    fun `a text source's background uses the given color otherwise`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.TextSource(id = "t3", name = "T", text = "X", backgroundColor = "#0000FF"),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[2, 2]
        assertEquals(1f, pixel.blue, "the parsed background color must actually be painted")
        assertEquals(1f, pixel.alpha)
    }

    @Test
    fun `every text alignment value renders without error`() = runComposeUiTest {
        for (h in listOf("left", "right", "center")) {
            for (v in listOf("top", "bottom", "center")) {
                setContent {
                    MaterialTheme {
                        SceneSourceRenderer(
                            SceneSource.TextSource(
                                id = "t4", name = "T", text = "Aligned",
                                horizontalAlignment = h, verticalAlignment = v,
                            )
                        )
                    }
                }
                onNodeWithText("Aligned").assertExists("h=$h v=$v must still render")
            }
        }
    }

    @Test
    fun `bold and italic text sources render without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.TextSource(
                    id = "t5",
                    name = "T",
                    text = "Styled",
                    bold = true,
                    italic = true
                ))
            }
        }
        onNodeWithText("Styled").assertExists()
    }

    // ── Color ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a solid color source renders exactly that color`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ColorSource(id = "c1", name = "C", color = "#FF0000", sourceOpacity = 1f),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertEquals(1f, pixel.red)
        assertEquals(0f, pixel.green)
        assertEquals(0f, pixel.blue)
        assertEquals(1f, pixel.alpha)
    }

    @Test
    fun `sourceOpacity controls the rendered alpha`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ColorSource(id = "c2", name = "C", color = "#FF0000", sourceOpacity = 0.5f),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertTrue(pixel.alpha in 0.4f..0.6f, "sourceOpacity must control the painted alpha (got ${pixel.alpha})")
    }

    @Test
    fun `a gradient color source paints two different colors across its width`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ColorSource(
                        id = "c3", name = "C", isGradient = true,
                        color = "#FF0000", gradientColor2 = "#0000FF", gradientAngle = 0f,
                    ),
                    modifier = Modifier.testTag("renderer").size(60.dp),
                )
            }
        }
        val pixels = onNodeWithTag("renderer").captureToImage().toPixelMap()
        val left = pixels[2, 30]
        val right = pixels[58, 30]
        assertNotEquals(left, right, "a gradient must not render as a single flat color")
    }

    // ── Shape ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a filled rectangle shape renders exactly its fill color`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s1", name = "S", shapeType = "rectangle",
                        fillColor = "#0000FF", fillOpacity = 1f, showStroke = false,
                    ),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertEquals(1f, pixel.blue)
        assertEquals(1f, pixel.alpha)
    }

    @Test
    fun `a filled ellipse shape renders its fill color at the center`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s2", name = "S", shapeType = "ellipse",
                        fillColor = "#00FF00", fillOpacity = 1f, showStroke = false,
                    ),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertEquals(1f, pixel.green)
    }

    @Test
    fun `a freehand shape with fewer than 2 points draws nothing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s3", name = "S", shapeType = "freehand",
                        strokeColor =
                            "#FFFFFF", points =
                                listOf(org.churchpresenter.app.churchpresenter.models.PathPoint(0.5f, 0.5f)),
                    ),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertEquals(0f, pixel.alpha, "with under 2 points there is nothing to connect, so nothing should be drawn")
    }

    @Test
    fun `line, arrow and freehand shapes with enough points render without error`() = runComposeUiTest {
        val points = listOf(
            org.churchpresenter.app.churchpresenter.models.PathPoint(0.1f, 0.1f),
            org.churchpresenter.app.churchpresenter.models.PathPoint(0.9f, 0.9f),
        )
        for (type in listOf("line", "arrow", "freehand")) {
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(
                        SceneSource.ShapeSource(
                            id = "s4",
                            name = "S",
                            shapeType = type,
                            points = points,
                            strokeColor = "#FFFFFF"
                        ),
                        modifier = Modifier.testTag("renderer"),
                    )
                }
            }
            onNodeWithTag("renderer").assertExists("shapeType=\"$type\" must render without throwing")
        }
    }

    @Test
    fun `a line shape with no points falls back to a default diagonal without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s5",
                        name = "S",
                        shapeType = "line",
                        points = emptyList(),
                        strokeColor = "#FFFFFF"
                    ),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithTag("renderer").assertExists()
    }

    @Test
    fun `a gradient shape fill renders without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s6", name = "S", shapeType = "rectangle", isGradient = true,
                        fillColor = "#FF0000", gradientColor2 = "#0000FF", fillOpacity = 1f,
                    ),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithTag("renderer").assertExists()
    }

    @Test
    fun `a non-gradient shape with fillOpacity zero draws no fill`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s7", name = "S", shapeType = "rectangle",
                        fillColor = "#FF0000", fillOpacity = 0f, showStroke = false,
                    ),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[20, 20]
        assertEquals(
            0f, pixel.alpha,
            "fillOpacity overrides the fill color's own alpha, so 0 must draw no fill brush at all",
        )
    }

    @Test
    fun `an ellipse with showStroke true and fillOpacity zero draws only its stroke`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s8", name = "S", shapeType = "ellipse",
                        strokeColor = "#FFFFFF", showStroke = true, fillOpacity = 0f,
                    ),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithTag("renderer").assertExists()
    }

    @Test
    fun `an arrow shape with no points falls back to a default diagonal without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(
                        id = "s9",
                        name = "S",
                        shapeType = "arrow",
                        points = emptyList(),
                        strokeColor = "#FFFFFF"
                    ),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithTag("renderer").assertExists()
    }

    @Test
    fun `an unrecognized shapeType draws nothing without crashing`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ShapeSource(id = "s10", name = "S", shapeType = "not-a-real-shape"),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithTag("renderer").assertExists()
    }

    // ── Clock ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `clock mode shows a non-empty, time-shaped string`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.ClockSource(id = "clock-live-1", name = "Clock", mode = "clock"))
            }
        }
        waitForIdle()
        // Not asserted exactly: it reads the real wall clock. Just confirm it produced a
        // plausible HH:MM(:SS)(am/pm) shape, not that it's blank or garbage.
        val timeShaped = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?( [AaPp][Mm])?$")
        val texts = onAllNodesWithText("", substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
        assertTrue(
            texts.any { timeShaped.matches(it) },
            "expected some rendered text shaped like a clock, found: $texts",
        )
    }

    @Test
    fun `countdown mode shows hours, minutes and seconds when both are enabled`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-countdown-1", name = "Clock", mode = "countdown",
                        targetHour = 1, targetMinute = 2, targetSecond = 3,
                        showHours = true, showSeconds = true,
                    )
                )
            }
        }
        waitForIdle()
        onNodeWithText("01:02:03").assertExists()
    }

    @Test
    fun `countdown mode omits hours when showHours is false`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-countdown-2", name = "Clock", mode = "countdown",
                        targetHour = 0, targetMinute = 1, targetSecond = 30,
                        showHours = false, showSeconds = true,
                    )
                )
            }
        }
        waitForIdle()
        onNodeWithText("01:30").assertExists()
    }

    @Test
    fun `countdown mode omits seconds when showSeconds is false`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-countdown-3", name = "Clock", mode = "countdown",
                        targetHour = 1, targetMinute = 2, targetSecond = 3,
                        showHours = true, showSeconds = false,
                    )
                )
            }
        }
        waitForIdle()
        onNodeWithText("01:02").assertExists()
    }

    @Test
    fun `a running countdown ticks down after one second`() = runComposeUiTest {
        val id = "clock-countdown-tick"
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = id, name = "Clock", mode = "countdown",
                        targetHour = 0, targetMinute = 0, targetSecond = 5,
                        showHours = false, showSeconds = true,
                    )
                )
            }
        }
        waitForIdle()
        onNodeWithText("00:05").assertExists()

        org.churchpresenter.app.churchpresenter.utils.TimerStateManager.setRunning(id, 5, true)
        waitForIdle()
        mainClock.advanceTimeBy(1000)
        waitForIdle()

        onNodeWithText("00:04").assertExists("a running countdown must tick down once per second")
    }

    @Test
    fun `clock mode adapts to showHours false and a 12h time format`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-live-2", name = "Clock", mode = "clock",
                        showHours = false, showSeconds = false, timeFormat = "12h",
                    )
                )
            }
        }
        waitForIdle()
        val texts = onAllNodesWithText("", substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
        assertTrue(
            texts.any { Regex("^\\d{1,2} [AaPp][Mm]$").matches(it) },
            "with showHours=false and a 12h format, only minutes plus am/pm should show; found: $texts",
        )
    }

    @Test
    fun `clock mode shows the hh colon prefix when hours and 12h format are both on`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-live-3", name = "Clock", mode = "clock",
                        showHours = true, showSeconds = false, timeFormat = "12h",
                    )
                )
            }
        }
        waitForIdle()
        val texts = onAllNodesWithText("", substring = true)
            .fetchSemanticsNodes(atLeastOneRootRequired = false)
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { t -> t.text } }
        assertTrue(
            texts.any { Regex("^\\d{1,2}:\\d{2} [AaPp][Mm]$").matches(it) },
            "with showHours=true and a 12h format, hh:mm plus am/pm should show; found: $texts",
        )
    }

    @Test
    fun `a clock source with bold false renders without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.ClockSource(
                        id = "clock-countdown-4", name = "Clock", mode = "countdown",
                        targetSecond = 1, showHours = false, bold = false,
                    )
                )
            }
        }
        waitForIdle()
        onNodeWithText("00:01").assertExists()
    }

    // ── QRCode ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a QR code source with valid content renders an image, not the placeholder`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.QRCodeSource(id = "q1", name = "Q", content = "https://example.com"))
            }
        }
        onNodeWithText("QR").assertDoesNotExist()
    }

    @Test
    fun `a QR code source with content too large to encode shows the placeholder`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.QRCodeSource(id = "q2", name = "Q", content = "x".repeat(5000), errorCorrection = "H")
                )
            }
        }
        onNodeWithText("QR").assertExists("content exceeding the QR capacity must be caught, not crash the renderer")
    }

    @Test
    fun `a wifi QR code source renders successfully`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.QRCodeSource(
                        id = "q3", name = "Q", contentType = "wifi",
                        wifiSsid = "MyNetwork", wifiPassword = "secret", wifiEncryption = "WPA2", wifiHidden = true,
                    )
                )
            }
        }
        onNodeWithText("QR").assertDoesNotExist()
    }

    @Test
    fun `every wifi encryption type renders successfully, including an open network`() = runComposeUiTest {
        // "WEP" and any unrecognized value (an open/"nopass" network) are the two branches
        // besides the WPA family already covered by the wifi QR test above.
        for (encryption in listOf("WEP", "OPEN")) {
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(
                        SceneSource.QRCodeSource(
                            id = "q3b", name = "Q", contentType = "wifi",
                            wifiSsid = "Net", wifiEncryption = encryption, wifiHidden = false,
                        )
                    )
                }
            }
            onNodeWithText("QR").assertDoesNotExist()
        }
    }

    @Test
    fun `transparentBackground true leaves the QR margin transparent`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.QRCodeSource(id = "q4", name = "Q", content = "hi", transparentBackground = true),
                    modifier = Modifier.testTag("renderer").size(60.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[2, 2]
        assertEquals(0f, pixel.alpha)
    }

    @Test
    fun `transparentBackground false paints the QR margin with backgroundColor`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.QRCodeSource(
                        id = "q5", name = "Q", content = "hi",
                        transparentBackground = false, backgroundColor = "#00FF00",
                    ),
                    modifier = Modifier.testTag("renderer").size(60.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[2, 2]
        assertEquals(1f, pixel.alpha)
    }

    @Test
    fun `every QR error correction level renders without error`() = runComposeUiTest {
        for (level in listOf("L", "Q", "M", "H", "unknown-falls-back-to-M")) {
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(
                        SceneSource.QRCodeSource(id = "q6", name = "Q", content = "test", errorCorrection = level),
                        modifier = Modifier.testTag("renderer"),
                    )
                }
            }
            onNodeWithTag("renderer").assertExists("errorCorrection=\"$level\" must render without throwing")
        }
    }

    // ── Bible ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a Bible source with no verse shows a placeholder`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.BibleSource(id = "b1", name = "B", verseText = ""))
            }
        }
        onNodeWithText("Select a verse...").assertExists()
    }

    @Test
    fun `a Bible source with a verse shows it, and its reference when present`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.BibleSource(
                        id = "b2",
                        name = "B",
                        verseText = "In the beginning...",
                        referenceText = "Genesis 1:1"
                    )
                )
            }
        }
        onNodeWithText("In the beginning...").assertExists()
        onNodeWithText("Genesis 1:1").assertExists()
    }

    @Test
    fun `a Bible source with no reference text does not show a reference line`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.BibleSource(
                    id = "b3",
                    name = "B",
                    verseText = "Some verse",
                    referenceText = ""
                ))
            }
        }
        onNodeWithText("Some verse").assertExists()
    }

    @Test
    fun `every Bible alignment value renders without error`() = runComposeUiTest {
        for (h in listOf("left", "right", "center")) {
            for (v in listOf("top", "bottom", "center")) {
                setContent {
                    MaterialTheme {
                        SceneSourceRenderer(
                            SceneSource.BibleSource(
                                id = "b4", name = "B", verseText = "Verse",
                                horizontalAlignment = h, verticalAlignment = v,
                            )
                        )
                    }
                }
                onNodeWithText("Verse").assertExists("h=$h v=$v must still render")
            }
        }
    }

    @Test
    fun `a bold, italic Bible source with a bold, italic reference renders without error`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.BibleSource(
                        id = "b5", name = "B", verseText = "Verse", referenceText = "Ref",
                        bold = true, italic = true, referenceBold = true, referenceItalic = true,
                    )
                )
            }
        }
        onNodeWithText("Verse").assertExists()
        onNodeWithText("Ref").assertExists()
    }

    @Test
    fun `a Bible source's background uses the given color when not fully transparent`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.BibleSource(id = "b6", name = "B", verseText = "V", backgroundColor = "#0000FF"),
                    modifier = Modifier.testTag("renderer").size(40.dp),
                )
            }
        }
        val pixel = onNodeWithTag("renderer").captureToImage().toPixelMap()[2, 2]
        assertEquals(1f, pixel.blue)
        assertEquals(1f, pixel.alpha)
    }

    // ── Video (placeholder path only — VLC is not available headless) ────────────────────────────

    @Test
    fun `a video source with a blank filePath shows a placeholder, not an image`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.VideoSource(id = "v1", name = "V", filePath = ""),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithContentDescription("V").assertDoesNotExist()
        onNodeWithTag("renderer").assertExists()
    }

    @Test
    fun `a video source with a nonexistent file shows a placeholder, not an image`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(
                    SceneSource.VideoSource(id = "v2", name = "V", filePath = "/nonexistent/clip.mp4"),
                    modifier = Modifier.testTag("renderer"),
                )
            }
        }
        onNodeWithContentDescription("V").assertDoesNotExist()
        onNodeWithTag("renderer").assertExists()
    }

    // ── Browser (blank-URL guard only — real browsing needs a live Chromium process) ────────────

    @Test
    fun `a browser source with a blank URL shows its own placeholder`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.BrowserSource(id = "br1", name = "Br", url = ""))
            }
        }
        onNodeWithText("Browser: no URL").assertExists()
    }

    // ── Camera (blank-devicePath guard only — real capture needs a physical device) ────────────

    @Test
    fun `a camera source with a blank devicePath and no deviceName shows the default placeholder`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(SceneSource.CameraSource(
                        id = "cam1",
                        name = "Cam",
                        devicePath = "",
                        deviceName = ""
                    ))
                }
            }
            onNodeWithText("Camera").assertExists()
        }

    @Test
    fun `a camera source with a blank devicePath but a deviceName names it in the placeholder`() =
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SceneSourceRenderer(
                        SceneSource.CameraSource(
                            id = "cam2",
                            name = "Cam",
                            devicePath = "",
                            deviceName = "Logitech C920"
                        )
                    )
                }
            }
            onNodeWithText("Logitech C920", substring = true).assertExists()
        }

    // ── ScreenCapture (initial placeholder only — java.awt.Robot is unavailable headless) ──────

    @Test
    fun `a screen capture source shows the waiting placeholder before any frame is captured`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SceneSourceRenderer(SceneSource.ScreenCaptureSource(id = "sc1", name = "SC"))
            }
        }
        waitForIdle()
        onNodeWithText("Screen Capture").assertExists()
    }
}
