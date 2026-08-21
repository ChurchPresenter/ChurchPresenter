@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.scene.SourceTransform
import org.churchpresenter.app.churchpresenter.tabs.CanvasLabel
import org.churchpresenter.app.churchpresenter.tabs.canvasButton
import org.churchpresenter.app.churchpresenter.tabs.canvasButtonAt
import org.churchpresenter.app.churchpresenter.tabs.canvasTab
import org.churchpresenter.app.churchpresenter.viewmodel.SceneViewModel
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

/**
 * Every state of the Canvas tab, in both themes.
 *
 * The tab is three panels that change together — the scene and source lists on the left, the canvas
 * in the middle, the selected source's properties on the right — so most states here are "this kind
 * of source, selected", which is the only way to see both what it draws and what it can be given.
 *
 * Sources are seeded onto a real [SceneViewModel] rather than added through the menu: the add button
 * keeps its hover tooltip after the first click, so a second one surfaces that instead of the menu
 * and only one source per test could be added that way.
 */
class CanvasTabScreenshotTest {

    private fun shoot(
        name: String,
        settings: (AppSettings) -> AppSettings = { it },
        width: Dp? = null,
        rootIndex: Int = 0,
        seed: SceneViewModel.() -> Unit = {},
        drive: ComposeUiTest.(SceneViewModel) -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        // Every state starts from a scene: with none the tab has nothing to draw into and no source
        // panel at all, and a shot of that is the one state `no_scene` already covers.
        canvasTab(
            seed = { addScene("Sunday Morning"); seed() },
            settings = settings,
            width = width,
            themeMode = mode,
        ) { vm, _ ->
            drive(vm)
            waitForIdle()
            captureTo(file, rootIndex)
        }
    }

    /** Seeds [source], selects it, and shoots — the shape almost every state below wants. */
    private fun selected(name: String, source: SceneSource) = shoot(
        name,
        seed = { addSource(source) },
        drive = { vm -> vm.selectSource(source.id); waitForIdle() },
    )

    // ── The scene and source lists ──────────────────────────────────────────────────────────────

    @Test
    fun `a new scene with nothing in it`() = shoot("empty_scene")

    /** Before any scene exists — the tab offers the New button and nothing else. */
    @Test
    fun `no scene at all`() = stackedThemes(SECTION, "no_scene") { mode, file ->
        canvasTab(themeMode = mode) { _, _ ->
            waitForIdle()
            captureTo(file)
        }
    }

    @Test
    fun `several scenes, one chosen`() = shoot("scene_list", seed = {
        addScene("Pre-Service Loop")
        addScene("Sermon Lower Third")
        addScene("Announcements Wall")
    })

    @Test
    fun `a stack of sources`() = shoot("source_stack", seed = { stack() })

    @Test
    fun `a source renaming in place`() = shoot("scene_renaming") { _ ->
        canvasButton(CanvasLabel.RENAME_SCENE).performClick()
        waitForIdle()
    }

    @Test
    fun `a hidden source`() = shoot(
        "source_hidden",
        seed = { stack() },
    ) { _ ->
        canvasButtonAt(CanvasLabel.TOGGLE_VISIBILITY, 0).performClick()
        waitForIdle()
    }

    @Test
    fun `a locked source`() = shoot(
        "source_locked",
        seed = { stack() },
    ) { _ ->
        canvasButtonAt(CanvasLabel.TOGGLE_LOCK, 0).performClick()
        waitForIdle()
    }

    @Test
    fun `the add-source menu`() = shoot("add_source_menu", rootIndex = 1) { _ ->
        canvasButton(CanvasLabel.ADD_SOURCE).performClick()
        waitForIdle()
    }

    // ── One state per kind of source, selected so its properties show ───────────────────────────

    @Test
    fun `a text source`() = selected(
        "source_text",
        SceneSource.TextSource(
            id = "text-1",
            name = "Welcome",
            text = "Welcome to the 10:30 service",
            transform = BANNER,
            fontSize = 64,
        ),
    )

    @Test
    fun `a colour source`() = selected(
        "source_color",
        SceneSource.ColorSource(id = "color-1", name = "Backdrop", color = "#1B2A5B"),
    )

    @Test
    fun `a gradient colour source`() = selected(
        "source_gradient",
        SceneSource.ColorSource(
            id = "color-2",
            name = "Backdrop",
            color = "#1B2A5B",
            isGradient = true,
            gradientColor2 = "#7B3FA6",
            gradientAngle = 45f,
        ),
    )

    // Not shot: the clock source. It draws the wall clock, so its image would differ on every
    // recording — the same reason the Announcements tab's two clock modes are not shot either.

    @Test
    fun `a QR code source`() = selected(
        "source_qr",
        SceneSource.QRCodeSource(
            id = "qr-1",
            name = "QR Code",
            content = "https://example.church/give",
            transform = SourceTransform(x = 0.35f, y = 0.3f, width = 0.3f, height = 0.4f),
        ),
    )

    @Test
    fun `a shape source`() = selected(
        "source_shape",
        SceneSource.ShapeSource(
            id = "shape-1",
            name = "Rectangle",
            transform = BANNER,
            strokeColor = "#FFD54F",
            fillColor = "#331B2A5B",
        ),
    )

    @Test
    fun `a Bible source`() = selected(
        "source_bible",
        SceneSource.BibleSource(
            id = "bible-1",
            name = "Bible",
            verseText = "In the beginning God created the heaven and the earth.",
            referenceText = "Genesis 1:1",
            transform = BANNER,
        ),
    )

    @Test
    fun `an image source`() = selected(
        "source_image",
        SceneSource.ImageSource(id = "image-1", name = "Image", filePath = photo().absolutePath),
    )

    /** A path that is not there any more — the canvas says so rather than drawing nothing. */
    @Test
    fun `an image whose file has gone`() = selected(
        "source_image_missing",
        SceneSource.ImageSource(id = "image-2", name = "Image", filePath = "/does/not/exist.png"),
    )

    @Test
    fun `a video source`() = selected(
        "source_video",
        SceneSource.VideoSource(id = "video-1", name = "Video", filePath = "/does/not/exist.mp4"),
    )

    @Test
    fun `a browser source with no URL yet`() = selected(
        "source_browser",
        SceneSource.BrowserSource(id = "browser-1", name = "Browser", url = ""),
    )

    @Test
    fun `a camera source with no device chosen`() = selected(
        "source_camera",
        SceneSource.CameraSource(id = "camera-1", name = "Camera"),
    )

    @Test
    fun `a screen capture source`() = selected(
        "source_screen_capture",
        SceneSource.ScreenCaptureSource(id = "screen-1", name = "Screen Capture"),
    )

    // ── The drawing tools ───────────────────────────────────────────────────────────────────────

    /** Choosing a drawing tool opens the stroke and fill colour fields beside the tool row. */
    @Test
    fun `the rectangle tool chosen`() = shoot("tool_rectangle") { _ ->
        toolButton(RECTANGLE_GLYPH).performClick()
        waitForIdle()
    }

    @Test
    fun `the freehand tool chosen`() = shoot("tool_freehand") { _ ->
        toolButton(FREEHAND_GLYPH).performClick()
        waitForIdle()
    }

    // ── Panel widths ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a narrow panel`() = shoot(
        "narrow_panel",
        width = 900.dp,
        seed = { addSource(SceneSource.TextSource(id = "s1", name = "Welcome", text = "Welcome")) },
    )

    @Test
    fun `wider side panels`() = shoot(
        "wide_side_panels",
        settings = {
            it.copy(
                maximizedLayout = it.maximizedLayout.copy(
                    canvasLeftPanelWidthDp = 320,
                    canvasRightPanelWidthDp = 380,
                )
            )
        },
        seed = { addSource(SceneSource.TextSource(id = "s1", name = "Welcome", text = "Welcome")) },
    )

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    /** A backdrop, a heading and a reference — three layers that read as three in the list. */
    private fun SceneViewModel.stack() {
        addSource(SceneSource.ColorSource(id = "s1", name = "Backdrop", color = "#1B2A5B"))
        addSource(SceneSource.TextSource(id = "s2", name = "Welcome", text = "Welcome", transform = BANNER))
        addSource(
            SceneSource.TextSource(
                id = "s3",
                name = "Service Times",
                text = "9:00 and 10:30",
                transform = SourceTransform(x = 0.1f, y = 0.82f, width = 0.8f, height = 0.12f),
                fontSize = 32,
            )
        )
    }

    /** The tool buttons are drawn as the shapes they make, which is also how they are addressed. */
    private fun ComposeUiTest.toolButton(glyph: String) = onAllNodesWithText(glyph)[0]

    /** A real, decodable image: the canvas decodes the file, and one that will not is a different state. */
    private fun photo(): File {
        FIXTURES.mkdirs()
        val file = File(FIXTURES, "backdrop.png")
        val image = BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB)
        val canvas = image.createGraphics()
        canvas.paint = GradientPaint(0f, 0f, Color(0x2B3A67), 640f, 360f, Color(0x8FB3F5))
        canvas.fillRect(0, 0, 640, 360)
        canvas.dispose()
        ImageIO.write(image, "png", file)
        return file
    }

    private companion object {
        const val SECTION = "canvasTab"

        /** A lower-third band, so a text/clock/shape source is seen where one would really sit. */
        val BANNER = SourceTransform(x = 0.1f, y = 0.62f, width = 0.8f, height = 0.22f)

        const val RECTANGLE_GLYPH = "□"
        const val FREEHAND_GLYPH = "✎"

        // Fixed path, not a temp dir: the properties panel prints the file's path, so a random name
        // would make every recording a diff.
        /**
         * A neutral root, not a repo-relative `build/` one.
         *
         * The path is printed into the image, and a repo-relative fixture resolves through the
         * developer's home directory — which on most machines is their name, committed into the PNG
         * for ever. `/tmp` where there is one, the JVM's temp directory otherwise (Windows).
         */
        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/canvas") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/canvas")
    }
}
