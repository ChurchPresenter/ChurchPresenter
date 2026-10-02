@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.data.StockMediaClient
import org.churchpresenter.app.churchpresenter.dialogs.LocalLibraryDialogContent
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.awt.Color
import java.awt.GradientPaint
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import org.churchpresenter.sharedui.screenshot.captureTo
import org.churchpresenter.sharedui.screenshot.stackedThemes

/**
 * The downloaded library the Background tab's image row opens; the stock search beside it is
 * `:media`'s `StockMediaBrowserScreenshotTest`.
 *
 * Shot through its `…Content` composable, since the real one is a `DialogWindow`.
 *
 * A bundled entry is listed by name and its artwork is loaded from the app's own resources, so the
 * fixture names below arrive as placeholder tiles rather than pictures. That is what these images are
 * for: which entries are listed, in what order, and that a bundled name already downloaded is not
 * listed twice — not what the bundled artwork looks like.

 */
class BackgroundMediaDialogsScreenshotTest {

    @AfterTest
    fun cleanUp() {
        FIXTURES.deleteRecursively()
    }

    // ── The downloaded library ──────────────────────────────────────────────────────────────────

    /** Nothing downloaded and nothing bundled — the library is empty. */
    @Test
    fun `an empty library`() = library("library_empty", downloaded = emptyList(), bundled = emptyList())

    /** What ships with the app, before anything has been downloaded. */
    @Test
    fun `the bundled backgrounds`() = library("library_bundled", downloaded = emptyList(), bundled = BUNDLED)

    /** Downloaded files come first, and a bundled name already downloaded is not listed twice. */
    @Test
    fun `downloaded and bundled together`() = library("library_mixed", downloaded = photos(), bundled = BUNDLED)

    @Test
    fun `the library searched`() = library("library_search", downloaded = photos(), bundled = BUNDLED) {
        type("mountain")
    }

    @Test
    fun `a library search that finds nothing`() =
        library("library_search_empty", downloaded = photos(), bundled = BUNDLED) { type("zzzz") }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun library(
        name: String,
        downloaded: List<File>,
        bundled: List<String>,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        render(mode, file, drive) {
            LocalLibraryDialogContent(
                mediaType = StockMediaClient.StockMediaType.PHOTO,
                downloadedFiles = downloaded,
                bundledFileNames = bundled,
                onDismiss = {},
                onMediaSelected = {},
            )
        }
    }

    private fun render(
        mode: ThemeMode,
        file: File,
        drive: ComposeUiTest.() -> Unit,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) { content() }
                }
            }
        }
        drive()
        waitForIdle()
        captureTo(file)
    }

    private fun ComposeUiTest.type(text: String) {
        onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        waitForIdle()
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    /**
     * Real, decodable images under a neutral root.
     *
     * Real because the grid decodes each one into a thumbnail; neutral because the library lists
     * them by name and a repo-relative `build/` fixture would resolve through the developer's home
     * directory.
     */
    private fun photos(): List<File> {
        FIXTURES.mkdirs()
        return DOWNLOADED.mapIndexed { index, name ->
            File(FIXTURES, name).also { file ->
                if (!file.exists()) {
                    val image = BufferedImage(480, 270, BufferedImage.TYPE_INT_RGB)
                    val canvas = image.createGraphics()
                    val hue = (index * 0.16f) % 1f
                    canvas.paint = GradientPaint(
                        0f, 0f, Color.getHSBColor(hue, 0.5f, 0.85f),
                        480f, 270f, Color.getHSBColor((hue + 0.1f) % 1f, 0.8f, 0.35f),
                    )
                    canvas.fillRect(0, 0, 480, 270)
                    canvas.dispose()
                    ImageIO.write(image, "png", file)
                }
            }
        }
    }

    private companion object {
        const val SECTION = "backgroundMediaDialogs"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/library") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/library")

        val DOWNLOADED = listOf("mountain-sunrise.png", "still-water.png", "city-lights.png")

        /** Bundled names, one of which is also downloaded — so the de-duplication is visible. */
        val BUNDLED = listOf("mountain-sunrise.png", "abstract-waves.jpg", "stained-glass.jpg", "wheat-field.jpg")
    }
}
