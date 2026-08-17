@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.SceneSource
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Image source's own controls: the file path field, the Browse button and the Scale dropdown.
 *
 * The dropdown is the part worth pinning. It stores `"FIT"`/`"FILL"`/`"STRETCH"`/`"NONE"` but shows
 * translated labels, and it does the mapping twice — once each way, through two hand-written maps. A
 * missing entry in either is silent: the panel would show the wrong scale, or write a scale the
 * renderer does not recognise. Both directions are walked over all four values, and the fallback for
 * a stored value this build has no label for is pinned too.
 *
 * The Browse button defaults to `FileChooser.platformInstance`, a real native dialog — but
 * `sourcePanel` accepts a `fileChooser` to swap in, so the button-click path is driven end to end
 * with a [FakeFileChooser] standing in for the dialog, same as [FileImagePickerTest] does for the
 * unrelated picker row used elsewhere in the app.
 */
class SourcePropertiesImageTest {

    /** Records the call it received and returns [answer] without ever touching a real dialog. */
    private class FakeFileChooser(private val answer: Path?) : FileChooser() {
        var lastPath: Path? = null
        var lastFilters: List<FileNameExtensionFilter>? = null
        var callCount = 0

        override suspend fun chooseImpl(
            path: Path,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<Path>? {
            callCount++
            lastPath = path
            lastFilters = filters
            return answer?.let { listOf(it) }
        }

        override suspend fun saveImpl(
            location: Path,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): Path? = error("not used by the image source's Browse button")
    }

    /** Ordinal of the file path field among the panel's fields — the header owns the first six. */
    private val filePathField = 6

    private val scaleLabels = listOf("Fit", "Fill", "Stretch", "None")

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every control captioned`() = sourcePanel(Fixture.image()) { _ ->
        onNodeWithText(Label.IMAGE).assertIsDisplayed()
        onNodeWithText("FILE PATH").assertIsDisplayed()
        onNodeWithText("SCALE").assertIsDisplayed()
    }

    @Test
    fun `the image panel adds one field to the header's six`() = sourcePanel(Fixture.image()) { _ ->
        textFields().assertCountEquals(7)
    }

    @Test
    fun `the file path field shows the stored path`() = sourcePanel(Fixture.image()) { _ ->
        assertFieldShows("/tmp/logo.png", "the file path field")
    }

    @Test
    fun `the Browse button is on screen and clickable`() = sourcePanel(Fixture.image()) { _ ->
        onNodeWithContentDescription("Browse").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `clicking Browse opens the chooser with an image filter, at the stored path's directory`() {
        // A real directory, not a literal "/tmp": FileChooser falls back to the home directory for
        // a start path that does not exist, and "/tmp" is not one on Windows.
        val dir = Files.createTempDirectory("cp-image-browse")
        val chooser = FakeFileChooser(answer = null)
        try {
            sourcePanel(Fixture.image(filePath = dir.resolve("logo.png").toString()), fileChooser = chooser) { _ ->
                onNodeWithContentDescription("Browse").performClick()
                waitForIdle()

                assertEquals(1, chooser.callCount)
                assertEquals(dir, chooser.lastPath, "the chooser must open at the stored file's parent directory")
                assertEquals(
                    listOf("png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "svg"),
                    chooser.lastFilters?.single()?.extensions?.toList(),
                )
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `choosing a file from Browse stores its path`() {
        val chosen = Path.of("/media/backdrops/sunset.jpg")
        val chooser = FakeFileChooser(answer = chosen)
        sourcePanel(Fixture.image(), fileChooser = chooser) { get ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            // The panel stores what the chooser answered, absolutised — which on Windows gains a
            // drive letter, so the expectation is derived rather than spelled out.
            assertEquals(chosen.absolutePathString(), (get() as SceneSource.ImageSource).filePath)
        }
    }

    @Test
    fun `canceling Browse leaves the stored path untouched`() {
        val chooser = FakeFileChooser(answer = null)
        sourcePanel(Fixture.image(), fileChooser = chooser) { get ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            assertEquals(
                "/tmp/logo.png",
                (get() as SceneSource.ImageSource).filePath,
                "canceling must not touch the stored path",
            )
        }
    }

    @Test
    fun `with no stored path, Browse falls back to the user's home directory`() {
        val chooser = FakeFileChooser(answer = null)
        sourcePanel(Fixture.image().copy(filePath = ""), fileChooser = chooser) { _ ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            // FileChooser.choose() itself falls back to user.home for a null/nonexistent start path.
            assertEquals(Path.of(System.getProperty("user.home")), chooser.lastPath)
        }
    }

    // ── File path ─────────────────────────────────────────────────────────────

    @Test
    fun `typing a path stores it and nothing else`() = sourcePanel(Fixture.image()) { get ->
        typeField(filePathField, "/media/backdrops/sunrise.jpg")

        val image = get() as SceneSource.ImageSource
        assertEquals("/media/backdrops/sunrise.jpg", image.filePath, "the field writes the source's path")
        assertEquals(Fixture.image().copy(filePath = image.filePath), image, "and touches nothing else")
        assertFieldShows("/media/backdrops/sunrise.jpg", "the file path field after typing")
    }

    @Test
    fun `the path can be cleared`() = sourcePanel(Fixture.image()) { get ->
        typeField(filePathField, "")

        assertEquals("", (get() as SceneSource.ImageSource).filePath, "an empty path is stored as typed")
    }

    // ── Scale ─────────────────────────────────────────────────────────────────

    @Test
    fun `the dropdown names the stored scale`() {
        listOf("FIT" to "Fit", "FILL" to "Fill", "STRETCH" to "Stretch", "NONE" to "None")
            .forEach { (stored, shown) ->
                sourcePanel(Fixture.image().copy(contentScale = stored)) { _ ->
                    assertEquals(1, countOf(shown), "$stored must read as \"$shown\"")
                }
            }
    }

    @Test
    fun `a scale this build does not know falls back to Fit`() {
        sourcePanel(Fixture.image().copy(contentScale = "COVER_EVERYTHING")) { _ ->
            onNodeWithText("Fit").assertExists("an unrecognised stored scale must name a real option")
            assertEquals(0, countOf("COVER_EVERYTHING"), "and must not show itself")
        }
    }

    @Test
    fun `the dropdown offers every scale`() = sourcePanel(Fixture.image()) { _ ->
        openDropdown(showing = "Fit")

        scaleLabels.forEach { option ->
            // Fit is both the closed selector's text and a menu entry; the others appear once.
            val expected = if (option == "Fit") 2 else 1
            assertEquals(expected, countOf(option), "\"$option\" must be offered exactly once")
        }
    }

    @Test
    fun `choosing Fill stores FILL`() = sourcePanel(Fixture.image()) { get ->
        chooseFromDropdown(showing = "Fit", option = "Fill")

        assertEquals("FILL", (get() as SceneSource.ImageSource).contentScale)
        assertEquals(1, countOf("Fill"), "and the closed selector now reads Fill")
    }

    @Test
    fun `choosing Stretch stores STRETCH`() = sourcePanel(Fixture.image()) { get ->
        chooseFromDropdown(showing = "Fit", option = "Stretch")

        assertEquals("STRETCH", (get() as SceneSource.ImageSource).contentScale)
        assertEquals(1, countOf("Stretch"))
    }

    @Test
    fun `choosing None stores NONE`() = sourcePanel(Fixture.image()) { get ->
        chooseFromDropdown(showing = "Fit", option = "None")

        assertEquals("NONE", (get() as SceneSource.ImageSource).contentScale)
        assertEquals(1, countOf("None"))
    }

    @Test
    fun `choosing Fit stores FIT`() {
        sourcePanel(Fixture.image().copy(contentScale = "FILL")) { get ->
            chooseFromDropdown(showing = "Fill", option = "Fit")

            assertEquals("FIT", (get() as SceneSource.ImageSource).contentScale)
            assertEquals(1, countOf("Fit"))
        }
    }

    @Test
    fun `changing the scale leaves the rest of the source alone`() = sourcePanel(Fixture.image()) { get ->
        chooseFromDropdown(showing = "Fit", option = "Fill")

        assertEquals(
            Fixture.image().copy(contentScale = "FILL"), get(),
            "the scale dropdown may write only the scale",
        )
    }
}
