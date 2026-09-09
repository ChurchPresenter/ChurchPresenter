@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the Background panel calls the thing it is showing, and what its preview tile paints. The
 * naming functions are `@Composable`, so each is read back out of a composition.
 */
class SongBackgroundNamingTest {

    private fun color(hex: String) = SongBackground(type = SongBackgroundType.COLOR, color = hex)

    /** Reads a `@Composable` String function back out of a composition. */
    private fun readComposed(block: @androidx.compose.runtime.Composable () -> String): String {
        var result = ""
        runComposeUiTest {
            setContent { MaterialTheme { result = block() } }
            waitForIdle()
        }
        return result
    }

    private inline fun PixelMap.count(predicate: (Color) -> Boolean): Int {
        var found = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(this[x, y])) found++
        return found
    }

    private fun ComposeUiTest.pixels(): PixelMap = onNodeWithTag("tile").captureToImage().toPixelMap()

    private fun ComposeUiTest.tile(background: SongBackground) = setContent {
        MaterialTheme {
            SongBackgroundFill(background, Modifier.testTag("tile").size(120.dp))
        }
    }

    private fun near(actual: Color, r: Float, g: Float, b: Float, tolerance: Float = 0.06f) =
        abs(actual.red - r) < tolerance && abs(actual.green - g) < tolerance && abs(actual.blue - b) < tolerance

    /** A solid red PNG on disk, which is what an image background points at. */
    private fun redPng(): File {
        val file = File.createTempFile("song-bg", ".png").also { it.deleteOnExit() }
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 16) for (x in 0 until 16) image.setRGB(x, y, 0xFF0000)
        ImageIO.write(image, "png", file)
        return file
    }

    // ── The name over the preview ─────────────────────────────────────────────

    @Test
    fun `an inherited background says so instead of naming a color`() {
        assertEquals("Inherited", readComposed { songBackgroundName(SongBackground()) })
    }

    @Test
    fun `a camera is named by its device`() {
        val camera = SongBackground(
            type = SongBackgroundType.CAMERA,
            camera = CameraDeviceRef(devicePath = "avfoundation://0", deviceName = "FaceTime HD Camera"),
        )
        assertEquals("FaceTime HD Camera", readComposed { songBackgroundName(camera) })
    }

    @Test
    fun `a camera with no name falls back to its path, not to a file name`() {
        val camera = SongBackground(
            type = SongBackgroundType.CAMERA,
            camera = CameraDeviceRef(devicePath = "avfoundation://0"),
        )
        assertEquals("avfoundation://0", readComposed { songBackgroundName(camera) })
    }

    @Test
    fun `an image is named by its file`() {
        val image = SongBackground(type = SongBackgroundType.IMAGE, image = "/photos/sunrise over hills.jpg")
        assertEquals("sunrise over hills.jpg", readComposed { songBackgroundName(image) })
    }

    @Test
    fun `a clip is named by its file`() {
        val video = SongBackground(type = SongBackgroundType.VIDEO, video = "/clips/loop.mp4")
        assertEquals("loop.mp4", readComposed { songBackgroundName(video) })
    }

    @Test
    fun `a palette color is named`() {
        assertEquals("Solid Black", readComposed { songBackgroundName(color("#000000")) })
    }

    @Test
    fun `a palette color is matched whatever case it was stored in`() {
        assertEquals("Deep Navy", readComposed { songBackgroundName(color("#0D1B2A")) })
    }

    @Test
    fun `a color outside the palette is the operator's own`() {
        assertEquals("Custom color", readComposed { songBackgroundName(color("#123456")) })
    }

    @Test
    fun `a palette gradient is named by both of its ends`() {
        val ember = SongBackground(type = SongBackgroundType.GRADIENT, color = "#3b1408", colorEnd = "#7a2c10")
        assertEquals("Ember", readComposed { songBackgroundName(ember) })
    }

    // ── The line under it ─────────────────────────────────────────────────────

    @Test
    fun `an inherited background's meta line points at the settings`() {
        assertEquals(
            "Follows the Background settings",
            readComposed { songBackgroundMeta(SongBackground()) },
        )
    }

    @Test
    fun `the meta line carries the category, the dim and the blur`() {
        val image = SongBackground(type = SongBackgroundType.IMAGE, image = "/a.jpg", dim = 40, blur = 6)
        assertEquals("Images · dim 40% · blur 6px", readComposed { songBackgroundMeta(image) })
    }

    @Test
    fun `each type reports its own category`() {
        val categories = listOf(
            SongBackgroundType.COLOR to "Colors",
            SongBackgroundType.GRADIENT to "Colors",
            SongBackgroundType.IMAGE to "Images",
            SongBackgroundType.VIDEO to "Videos",
            SongBackgroundType.CAMERA to "Cameras",
        )
        for ((type, label) in categories) {
            val meta = readComposed { songBackgroundMeta(SongBackground(type = type)) }
            assertTrue(meta.startsWith(label), "$type must be filed under $label, got: $meta")
        }
    }

    // ── The badge in the corner ───────────────────────────────────────────────

    @Test
    fun `an inherited background's badge says so`() {
        assertEquals("Inherited", readComposed { songBackgroundBadge(SongBackground()) })
    }

    @Test
    fun `a clip's badge says it loops rather than naming the file`() {
        val video = SongBackground(type = SongBackgroundType.VIDEO, video = "/clips/loop.mp4")
        assertEquals("video loop", readComposed { songBackgroundBadge(video) })
    }

    @Test
    fun `a camera's badge says it is live`() {
        val camera = SongBackground(type = SongBackgroundType.CAMERA, camera = CameraDeviceRef(deviceName = "Cam"))
        assertEquals("live camera", readComposed { songBackgroundBadge(camera) })
    }

    @Test
    fun `every other badge is just the name`() {
        assertEquals("Solid Black", readComposed { songBackgroundBadge(color("#000000")) })
    }

    // ── What the tile paints ──────────────────────────────────────────────────

    @Test
    fun `an inherited background draws black`() = runComposeUiTest {
        tile(SongBackground())
        assertTrue(pixels().count { near(it, 0f, 0f, 0f) } > 0, "nothing overridden shows as black")
    }

    @Test
    fun `a color background draws that color`() = runComposeUiTest {
        tile(color("#FF0000"))
        val map = pixels()
        assertEquals(map.width * map.height, map.count { near(it, 1f, 0f, 0f) }, "the whole tile is the color")
    }

    @Test
    fun `a gradient draws both of its ends`() = runComposeUiTest {
        tile(SongBackground(type = SongBackgroundType.GRADIENT, color = "#FF0000", colorEnd = "#0000FF"))
        val map = pixels()
        assertTrue(near(map[map.width / 2, 1], 1f, 0f, 0f), "the near color is at the top")
        assertTrue(near(map[map.width / 2, map.height - 2], 0f, 0f, 1f), "and the far one at the bottom")
    }

    @Test
    fun `a clip draws black rather than opening VLC for a thumbnail`() = runComposeUiTest {
        tile(SongBackground(type = SongBackgroundType.VIDEO, video = "/clips/loop.mp4"))
        val map = pixels()
        assertEquals(map.width * map.height, map.count { near(it, 0f, 0f, 0f) })
    }

    @Test
    fun `a camera draws black rather than opening the device`() = runComposeUiTest {
        tile(SongBackground(type = SongBackgroundType.CAMERA, camera = CameraDeviceRef(deviceName = "Cam")))
        val map = pixels()
        assertEquals(map.width * map.height, map.count { near(it, 0f, 0f, 0f) })
    }

    @Test
    fun `an image draws the file it points at`() = runComposeUiTest {
        tile(SongBackground(type = SongBackgroundType.IMAGE, image = redPng().absolutePath))
        // The file is read on IO, so the tile is black until that lands — waited for by the
        // picture appearing rather than by a pause.
        waitUntil("the picture must reach the tile") { pixels().count { near(it, 1f, 0f, 0f) } > 0 }
    }

    @Test
    fun `an image that is not there draws black instead of failing`() = runComposeUiTest {
        tile(SongBackground(type = SongBackgroundType.IMAGE, image = "/no/such/picture.png"))
        waitForIdle()
        val map = pixels()
        assertEquals(map.width * map.height, map.count { near(it, 0f, 0f, 0f) })
    }

    @Test
    fun `dim washes black over whatever was drawn`() = runComposeUiTest {
        tile(color("#FF0000").copy(dim = 50))
        val middle = pixels()[60, 60]
        assertTrue(middle.red in 0.35f..0.65f, "half a wash of black over red: ${middle.red}")
        assertTrue(middle.green < 0.1f && middle.blue < 0.1f, "the wash is black, not grey")
    }

    @Test
    fun `full dim leaves nothing of the background`() = runComposeUiTest {
        tile(color("#FF0000").copy(dim = 100))
        val map = pixels()
        assertEquals(map.width * map.height, map.count { near(it, 0f, 0f, 0f) })
    }

    @Test
    fun `opacity below full fades the background out`() = runComposeUiTest {
        tile(color("#FF0000").copy(opacity = 40))
        val middle = pixels()[60, 60]
        // Read on the alpha channel: the tile is captured over nothing, so a faded background
        // arrives as its own color at less than full opacity rather than blended toward a ground.
        assertTrue(middle.alpha < 0.9f, "a faded background must not paint at full strength: $middle")
    }

    @Test
    fun `a fully opaque background lets nothing through`() = runComposeUiTest {
        tile(color("#FF0000"))
        val middle = pixels()[60, 60]
        assertEquals(1f, middle.alpha, "nothing behind it may show: $middle")
    }

    @Test
    fun `a blurred background still draws its color`() = runComposeUiTest {
        tile(color("#FF0000").copy(blur = 12))
        assertTrue(pixels().count { it.red > 0.5f } > 0, "a blur must not blank the tile")
    }
}
