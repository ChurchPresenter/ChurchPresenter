package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The on-disk shape of a canvas scene.
 *
 * `SceneViewModel` writes every scene the user has built to `~/.churchpresenter/scenes.json` and
 * reads it back on the next launch, so these classes are a file format, not just in-memory state.
 * A renamed field, a changed default or a changed polymorphic discriminator all compile perfectly
 * and lose the user's scenes silently — the sources come back missing, or the whole file fails to
 * parse and the scene list is empty.
 *
 * So what these pin is: every source type survives a round trip with its own fields intact, the
 * discriminator each source is stored under does not move, and unknown keys from a newer build are
 * tolerated rather than fatal.
 */
class SceneModelsSerializationTest {

    /** Configured exactly as `SceneViewModel` configures its own encoder. */
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private inline fun <reified T : SceneSource> roundTrip(source: T): T {
        val encoded = json.encodeToString(SceneSource.serializer(), source)
        val decoded = json.decodeFromString(SceneSource.serializer(), encoded)
        return assertIs<T>(decoded, "a ${T::class.simpleName} came back as ${decoded::class.simpleName}")
    }

    private fun discriminatorOf(source: SceneSource): String =
        json.decodeFromString(JsonObject.serializer(), json.encodeToString(SceneSource.serializer(), source))
            .getValue("type").jsonPrimitive.content

    // ── Transforms ──────────────────────────────────────────────────────────────

    @Test
    fun `a source with no transform of its own fills the canvas`() {
        val default = SourceTransform()

        assertEquals(0f, default.x)
        assertEquals(0f, default.y)
        assertEquals(1f, default.width, "a new source covers the whole canvas rather than being invisible")
        assertEquals(1f, default.height)
        assertEquals(0f, default.rotation)
        assertEquals(1f, default.opacity, "a new source is opaque, not blank")
    }

    @Test
    fun `a transform survives being written and read back`() {
        val moved = SourceTransform(x = 0.25f, y = 0.1f, width = 0.5f, height = 0.4f, rotation = 45f, opacity = 0.6f)

        val encoded = json.encodeToString(SourceTransform.serializer(), moved)

        assertEquals(moved, json.decodeFromString(SourceTransform.serializer(), encoded))
    }

    @Test
    fun `a transform saved by an older build reads back at its defaults`() {
        val old = json.decodeFromString(SourceTransform.serializer(), """{"x":0.5,"y":0.5}""")

        assertEquals(0.5f, old.x)
        assertEquals(1f, old.width, "a source from before width was stored must not collapse to nothing")
        assertEquals(1f, old.opacity)
    }

    @Test
    fun `two transforms with the same numbers are the same transform`() {
        val a = SourceTransform(x = 0.1f, y = 0.2f)
        val b = SourceTransform(x = 0.1f, y = 0.2f)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, a.copy(rotation = 90f), "a rotated copy is a different placement")
    }

    // ── Path points ─────────────────────────────────────────────────────────────

    @Test
    fun `a path point round trips`() {
        val point = PathPoint(x = 0.3f, y = 0.7f)

        assertEquals(point, json.decodeFromString(PathPoint.serializer(), json.encodeToString(PathPoint.serializer(), point)))
    }

    // ── Each kind of source ─────────────────────────────────────────────────────

    @Test
    fun `an image source keeps its file and how it is scaled`() {
        val image = SceneSource.ImageSource(
            id = "i1", name = "Backdrop", filePath = "/media/backdrop.png", contentScale = "CROP",
            transform = SourceTransform(x = 0.1f, width = 0.8f), visible = false, locked = true,
        )

        val back = roundTrip(image)

        assertEquals(image, back)
        assertEquals("/media/backdrop.png", back.filePath, "losing the path leaves a blank rectangle on screen")
        assertEquals("CROP", back.contentScale)
        assertEquals(false, back.visible)
        assertEquals(true, back.locked)
    }

    @Test
    fun `a text source keeps every typographic choice`() {
        val text = SceneSource.TextSource(
            id = "t1", name = "Title", text = "Welcome", fontFamily = "Georgia", fontSize = 96,
            fontColor = "#FF0000", backgroundColor = "#80000000", bold = true, italic = true,
            horizontalAlignment = "left", verticalAlignment = "top", lineSpacing = 140,
        )

        val back = roundTrip(text)

        assertEquals(text, back)
        assertEquals(96, back.fontSize)
        assertEquals("#FF0000", back.fontColor)
        assertTrue(back.bold && back.italic)
        assertEquals(140, back.lineSpacing)
    }

    @Test
    fun `a colour source keeps its gradient`() {
        val gradient = SceneSource.ColorSource(
            id = "c1", name = "Wash", color = "#102030", sourceOpacity = 0.8f, isGradient = true,
            gradientColor2 = "#405060", gradientColor2Opacity = 0.4f, gradientAngle = 135f, gradientPosition = 0.25f,
        )

        val back = roundTrip(gradient)

        assertEquals(gradient, back)
        assertTrue(back.isGradient, "a gradient that reloads as a flat colour is a visible regression")
        assertEquals(135f, back.gradientAngle)
        assertEquals(0.25f, back.gradientPosition)
    }

    @Test
    fun `a video source keeps its loop and volume`() {
        val video = SceneSource.VideoSource(
            id = "v1", name = "Loop", filePath = "/media/loop.mp4", loop = true, volume = 0f,
        )

        val back = roundTrip(video)

        assertEquals(video, back)
        assertTrue(back.loop)
        assertEquals(0f, back.volume, "a muted background video must not come back at full volume mid-service")
    }

    @Test
    fun `a browser source keeps its render size and refresh`() {
        val browser = SceneSource.BrowserSource(
            id = "b1", name = "Giving", url = "https://example.org/give", refreshInterval = 30,
            renderWidth = 1280, renderHeight = 720, customCss = "body{background:transparent}",
            fps = 15, forceTransparent = true,
        )

        val back = roundTrip(browser)

        assertEquals(browser, back)
        assertEquals(1280, back.renderWidth)
        assertEquals(720, back.renderHeight)
        assertEquals(15, back.fps)
        assertTrue(back.forceTransparent)
    }

    @Test
    fun `a shape source keeps its path points in order`() {
        val shape = SceneSource.ShapeSource(
            id = "s1", name = "Arrow", shapeType = "polygon",
            strokeColor = "#00FF00", fillColor = "#FF00FF", strokeWidth = 8f,
            points = listOf(PathPoint(0f, 0f), PathPoint(0.5f, 1f), PathPoint(1f, 0f)),
            fillOpacity = 0.5f, strokeOpacity = 0.9f, showStroke = false, isGradient = true,
            gradientColor2 = "#0000FF", gradientColor2Opacity = 0.3f, gradientAngle = 90f, gradientPosition = 0.75f,
        )

        val back = roundTrip(shape)

        assertEquals(shape, back)
        assertEquals(3, back.points.size)
        assertEquals(PathPoint(0.5f, 1f), back.points[1], "reordered points redraw the shape wrong")
    }

    @Test
    fun `a shape with no points of its own reads back as an empty path`() {
        val rectangle = roundTrip(SceneSource.ShapeSource(id = "s2", name = "Box"))

        assertEquals(emptyList(), rectangle.points)
        assertEquals("rectangle", rectangle.shapeType)
        assertTrue(rectangle.showStroke)
    }

    @Test
    fun `a clock source keeps its mode and target time`() {
        val clock = SceneSource.ClockSource(
            id = "cl1", name = "Countdown", mode = "countdown", timeFormat = "12h",
            showHours = false, showSeconds = false, fontFamily = "Courier", fontSize = 120,
            fontColor = "#FFFF00", backgroundColor = "#FF000000", bold = false,
            targetHour = 10, targetMinute = 30, targetSecond = 15,
        )

        val back = roundTrip(clock)

        assertEquals(clock, back)
        assertEquals("countdown", back.mode)
        assertEquals(10, back.targetHour)
        assertEquals(30, back.targetMinute)
        assertEquals(15, back.targetSecond)
        assertTrue(!back.showHours && !back.showSeconds)
    }

    @Test
    fun `a QR source keeps wifi credentials and colours`() {
        val qr = SceneSource.QRCodeSource(
            id = "q1", name = "Guest wifi", contentType = "wifi", content = "",
            wifiSsid = "ChurchGuest", wifiPassword = "letmein", wifiEncryption = "WPA2", wifiHidden = true,
            foregroundColor = "#123456", backgroundColor = "#654321", transparentBackground = true,
            errorCorrection = "H",
        )

        val back = roundTrip(qr)

        assertEquals(qr, back)
        assertEquals("ChurchGuest", back.wifiSsid, "a lost SSID silently produces a QR code nobody can join")
        assertEquals("letmein", back.wifiPassword)
        assertEquals("H", back.errorCorrection)
        assertTrue(back.wifiHidden && back.transparentBackground)
    }

    @Test
    fun `a camera source keeps which device it was pointed at`() {
        val decklink = SceneSource.CameraSource(
            id = "cam1", name = "Stage cam", devicePath = "/dev/video2", deviceName = "DeckLink Mini",
            videoFormat = "1080p60", videoConnection = 3, isDeckLink = true, deckLinkIndex = 1,
        )

        val back = roundTrip(decklink)

        assertEquals(decklink, back)
        assertTrue(back.isDeckLink)
        assertEquals(1, back.deckLinkIndex)
        assertEquals(3, back.videoConnection)
    }

    @Test
    fun `a camera source that was never configured reads back as no device`() {
        val fresh = roundTrip(SceneSource.CameraSource(id = "cam2", name = "Camera"))

        assertEquals("", fresh.devicePath)
        assertEquals(-1, fresh.deckLinkIndex, "-1, not 0, is what means 'no DeckLink chosen'")
        assertEquals(false, fresh.isDeckLink)
    }

    @Test
    fun `a screen capture source keeps its region and window`() {
        val capture = SceneSource.ScreenCaptureSource(
            id = "sc1", name = "Lyrics window", captureMode = "window",
            captureX = 100, captureY = 200, captureWidth = 800, captureHeight = 600,
            captureInterval = 33, windowTitle = "ProPresenter", windowId = "42",
        )

        val back = roundTrip(capture)

        assertEquals(capture, back)
        assertEquals(100, back.captureX)
        assertEquals(600, back.captureHeight)
        assertEquals(33, back.captureInterval)
        assertEquals("ProPresenter", back.windowTitle)
    }

    @Test
    fun `a bible source keeps verse and reference styled separately`() {
        val verse = SceneSource.BibleSource(
            id = "bs1", name = "Verse", verseText = "For God so loved the world", referenceText = "John 3:16",
            fontFamily = "Times", fontSize = 60, fontColor = "#EEEEEE",
            referenceFontSize = 28, referenceFontColor = "#AAAAAA", backgroundColor = "#40000000",
            bold = true, italic = false, referenceBold = false, referenceItalic = true,
            horizontalAlignment = "right", verticalAlignment = "bottom", lineSpacing = 120,
        )

        val back = roundTrip(verse)

        assertEquals(verse, back)
        assertEquals(60, back.fontSize)
        assertEquals(28, back.referenceFontSize, "the reference is deliberately smaller than the verse")
        assertTrue(back.bold && !back.italic)
        assertTrue(!back.referenceBold && back.referenceItalic)
    }

    // ── The polymorphic contract ────────────────────────────────────────────────

    @Test
    fun `each source type is stored under its own stable discriminator`() {
        // These strings are in every saved scenes.json. Changing a class name or adding @SerialName
        // moves them, and every scene saved by an older build stops loading.
        val prefix = "org.churchpresenter.app.churchpresenter.models.SceneSource."

        assertEquals(prefix + "ImageSource", discriminatorOf(SceneSource.ImageSource(id = "1", name = "n", filePath = "/f")))
        assertEquals(prefix + "TextSource", discriminatorOf(SceneSource.TextSource(id = "1", name = "n")))
        assertEquals(prefix + "ColorSource", discriminatorOf(SceneSource.ColorSource(id = "1", name = "n")))
        assertEquals(prefix + "VideoSource", discriminatorOf(SceneSource.VideoSource(id = "1", name = "n", filePath = "/f")))
        assertEquals(prefix + "BrowserSource", discriminatorOf(SceneSource.BrowserSource(id = "1", name = "n", url = "u")))
        assertEquals(prefix + "ShapeSource", discriminatorOf(SceneSource.ShapeSource(id = "1", name = "n")))
        assertEquals(prefix + "ClockSource", discriminatorOf(SceneSource.ClockSource(id = "1", name = "n")))
        assertEquals(prefix + "QRCodeSource", discriminatorOf(SceneSource.QRCodeSource(id = "1", name = "n")))
        assertEquals(prefix + "CameraSource", discriminatorOf(SceneSource.CameraSource(id = "1", name = "n")))
        assertEquals(prefix + "ScreenCaptureSource", discriminatorOf(SceneSource.ScreenCaptureSource(id = "1", name = "n")))
        assertEquals(prefix + "BibleSource", discriminatorOf(SceneSource.BibleSource(id = "1", name = "n")))
    }

    @Test
    fun `a source saved by a newer build loads with the fields this build knows`() {
        val fromFuture = json.decodeFromString(
            SceneSource.serializer(),
            """{
                "type":"org.churchpresenter.app.churchpresenter.models.SceneSource.TextSource",
                "id":"t9","name":"Title","text":"Hello","someFutureField":true
            }""",
        )

        val text = assertIs<SceneSource.TextSource>(fromFuture)
        assertEquals("Hello", text.text, "one unknown key must not cost the user the whole scene file")
        assertEquals(48, text.fontSize)
    }

    @Test
    fun `every source type loads from a file that stored only its required fields`() {
        // A scene saved by an older build has none of the fields added since, and kotlinx fills each
        // one from its default. If any type gains a field without a default, this stops compiling
        // for that type — which is the point: it would also stop every existing scenes.json loading.
        val prefix = "org.churchpresenter.app.churchpresenter.models.SceneSource."
        val minimal = mapOf(
            "ImageSource" to """"filePath":"/f"""",
            "TextSource" to null,
            "ColorSource" to null,
            "VideoSource" to """"filePath":"/f"""",
            "BrowserSource" to """"url":"https://example.org"""",
            "ShapeSource" to null,
            "ClockSource" to null,
            "QRCodeSource" to null,
            "CameraSource" to null,
            "ScreenCaptureSource" to null,
            "BibleSource" to null,
        )

        minimal.forEach { (type, required) ->
            val body = listOfNotNull(""""type":"$prefix$type"""", """"id":"x"""", """"name":"$type"""", required)
            val source = json.decodeFromString(SceneSource.serializer(), body.joinToString(",", "{", "}"))

            assertEquals(type, source::class.simpleName)
            assertEquals(SourceTransform(), source.transform, "$type would load at an unusable size or position")
            assertTrue(source.visible, "$type would load already hidden")
            assertTrue(!source.locked, "$type would load already locked")
        }
    }

    @Test
    fun `a source written by an encoder that omits defaults still reads back the same`() {
        // Not every path through the app encodes with `encodeDefaults` on — the companion API and
        // instance link both send scene payloads. Dropping a field at its default has to be lossless.
        val terse = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val sources: List<SceneSource> = listOf(
            SceneSource.ImageSource(id = "1", name = "Image", filePath = "/f"),
            SceneSource.TextSource(id = "2", name = "Text", text = "Hi"),
            SceneSource.ColorSource(id = "3", name = "Colour", color = "#123456"),
            SceneSource.VideoSource(id = "4", name = "Video", filePath = "/f", loop = true),
            SceneSource.BrowserSource(id = "5", name = "Browser", url = "https://example.org"),
            SceneSource.ShapeSource(id = "6", name = "Shape", points = listOf(PathPoint(1f, 2f))),
            SceneSource.ClockSource(id = "7", name = "Clock", targetHour = 9),
            SceneSource.QRCodeSource(id = "8", name = "QR", content = "https://example.org/give"),
            SceneSource.CameraSource(id = "9", name = "Camera", deviceName = "Cam"),
            SceneSource.ScreenCaptureSource(id = "10", name = "Capture", captureX = 5),
            SceneSource.BibleSource(id = "11", name = "Verse", verseText = "…"),
        )

        sources.forEach {
            val encoded = terse.encodeToString(SceneSource.serializer(), it)

            assertEquals(it, terse.decodeFromString(SceneSource.serializer(), encoded), "${it::class.simpleName} lost a field")
        }
    }

    // ── Whole scenes ────────────────────────────────────────────────────────────

    @Test
    fun `a new scene is 1080p and empty`() {
        val scene = Scene()

        assertEquals("Scene", scene.name)
        assertEquals(1920, scene.canvasWidth)
        assertEquals(1080, scene.canvasHeight)
        assertEquals(emptyList(), scene.sources)
        assertTrue(scene.id.isNotBlank(), "an id-less scene could not be selected")
    }

    @Test
    fun `two new scenes do not share an id`() {
        assertNotEquals(Scene().id, Scene().id, "scenes are addressed by id — a collision would swap them")
    }

    @Test
    fun `a scene keeps its mixed sources in layer order`() {
        val scene = Scene(
            id = "scene-1", name = "Welcome", canvasWidth = 3840, canvasHeight = 2160,
            sources = listOf(
                SceneSource.ColorSource(id = "back", name = "Background", color = "#000000"),
                SceneSource.ImageSource(id = "logo", name = "Logo", filePath = "/logo.png"),
                SceneSource.TextSource(id = "title", name = "Title", text = "Welcome"),
            ),
        )

        val back = json.decodeFromString(Scene.serializer(), json.encodeToString(Scene.serializer(), scene))

        assertEquals(scene, back)
        assertEquals(3840, back.canvasWidth)
        assertEquals(
            listOf("back", "logo", "title"),
            back.sources.map { it.id },
            "the list order is the stacking order — reordering it puts the background over the text",
        )
    }

    @Test
    fun `a scene saved before the canvas size was stored reads back as 1080p`() {
        val old = json.decodeFromString(Scene.serializer(), """{"id":"s","name":"Old","sources":[]}""")

        assertEquals(1920, old.canvasWidth)
        assertEquals(1080, old.canvasHeight)
    }

    @Test
    fun `a scene written by an encoder that omits defaults still reads back the same`() {
        val terse = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val scene = Scene(
            id = "scene-1", name = "Welcome",
            sources = listOf(SceneSource.TextSource(id = "t", name = "Title", text = "Welcome")),
        )

        val encoded = terse.encodeToString(Scene.serializer(), scene)

        assertEquals(scene, terse.decodeFromString(Scene.serializer(), encoded))
        assertEquals(
            SourceTransform(x = 0.25f),
            terse.decodeFromString(
                SourceTransform.serializer(),
                terse.encodeToString(SourceTransform.serializer(), SourceTransform(x = 0.25f)),
            ),
            "a transform at its defaults must not come back as a zero-sized, transparent source",
        )
    }

    @Test
    fun `every source in a scene is reachable through the base type`() {
        val sources: List<SceneSource> = listOf(
            SceneSource.ImageSource(id = "1", name = "Image", filePath = "/f"),
            SceneSource.TextSource(id = "2", name = "Text"),
            SceneSource.ColorSource(id = "3", name = "Colour"),
            SceneSource.VideoSource(id = "4", name = "Video", filePath = "/f"),
            SceneSource.BrowserSource(id = "5", name = "Browser", url = "u"),
            SceneSource.ShapeSource(id = "6", name = "Shape"),
            SceneSource.ClockSource(id = "7", name = "Clock"),
            SceneSource.QRCodeSource(id = "8", name = "QR"),
            SceneSource.CameraSource(id = "9", name = "Camera"),
            SceneSource.ScreenCaptureSource(id = "10", name = "Capture"),
            SceneSource.BibleSource(id = "11", name = "Verse"),
        )

        // The canvas renderer walks sources through these five properties alone, whatever the type.
        sources.forEach {
            assertTrue(it.id.isNotBlank(), "${it::class.simpleName} has no id")
            assertTrue(it.name.isNotBlank(), "${it::class.simpleName} would show a blank row in the layer list")
            assertTrue(it.visible, "${it::class.simpleName} would be added to a scene already hidden")
            assertTrue(!it.locked, "${it::class.simpleName} would be added already locked and unmovable")
            assertEquals(SourceTransform(), it.transform)
        }
    }
}
