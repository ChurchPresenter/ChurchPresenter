@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.settings.AppSettings
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The two things the tab cannot do for itself, and has to be handed.
 *
 * Both default to doing nothing so the module composes on its own, and both were left at that
 * default when the tab moved out of `:composeApp` — which is not a crash, a warning, or anything
 * visible in a screenshot. It is a Browse button that opens no dialog and a Bible source with no
 * verse picker, and neither says so. These are here because the default being *silent* is the whole
 * problem: nothing but a test that goes through `CanvasTab` itself can tell the two apart.
 */
class CanvasTabWiringTest {

    private class RecordingPicker(private val answer: Path?) : CanvasFilePicker {
        val titles = CopyOnWriteArrayList<String>()
        val filters = CopyOnWriteArrayList<List<FileNameExtensionFilter>>()

        override suspend fun chooseSingle(
            path: Path?,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): Path? {
            titles += title
            this.filters += filters
            return answer
        }
    }

    private fun tab(
        source: SceneSource,
        picker: CanvasFilePicker = CanvasFilePicker.None,
        bible: @Composable (SceneSource.BibleSource, (SceneSource) -> Unit) -> Unit = { _, _ -> },
        block: ComposeUiTest.(vm: SceneViewModel) -> Unit,
    ) {
        val realHome = System.getProperty("user.home")
        val tempHome = java.nio.file.Files.createTempDirectory("cp-canvas-wiring").toFile()
        System.setProperty("user.home", tempHome.absolutePath)
        val vm = SceneViewModel()
        try {
            vm.addScene("Scene")
            vm.addSource(source)
            vm.selectSource(source.id)
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        Box(Modifier.fillMaxSize()) {
                            CanvasTab(
                                appSettings = AppSettings(),
                                output = FakeCanvasOutput(),
                                sceneViewModel = vm,
                                onAddToSchedule = { _, _ -> },
                                fileChooser = picker,
                                bibleProperties = bible,
                            )
                        }
                    }
                }
                block(vm)
            }
        } finally {
            realHome?.let { System.setProperty("user.home", it) }
            tempHome.deleteRecursively()
        }
    }

    // ── The file chooser ───────────────────────────────────────────────────────

    @Test
    fun `Browse on an image source opens the chooser the tab was given`() {
        val picker = RecordingPicker(Path(System.getProperty("java.io.tmpdir"), "chosen.png"))
        val image = SceneSource.ImageSource(id = "img", name = "Logo", filePath = "")

        tab(image, picker = picker) {
            onNodeWithContentDescription("Browse").performScrollTo().performClick()
            waitForIdle()

            assertTrue(picker.titles.isNotEmpty(), "Browse must reach the chooser, not a no-op default")
        }
    }

    @Test
    fun `the chosen image lands on the source`() {
        val chosen = Path(System.getProperty("java.io.tmpdir"), "chosen.png")
        val image = SceneSource.ImageSource(id = "img", name = "Logo", filePath = "")

        tab(image, picker = RecordingPicker(chosen)) { vm ->
            onNodeWithContentDescription("Browse").performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                chosen.toString(),
                (vm.currentScene?.sources?.single() as SceneSource.ImageSource).filePath,
            )
        }
    }

    @Test
    fun `Browse on a video source reaches the chooser too`() {
        val picker = RecordingPicker(null)
        val video = SceneSource.VideoSource(id = "vid", name = "Bumper", filePath = "")

        tab(video, picker = picker) {
            onNodeWithContentDescription("Browse").performScrollTo().performClick()
            waitForIdle()

            assertTrue(picker.titles.isNotEmpty())
        }
    }

    @Test
    fun `a cancelled chooser leaves the source alone`() {
        val image = SceneSource.ImageSource(id = "img", name = "Logo", filePath = "/tmp/before.png")

        tab(image, picker = RecordingPicker(null)) { vm ->
            onNodeWithContentDescription("Browse").performScrollTo().performClick()
            waitForIdle()

            assertEquals(
                "/tmp/before.png",
                (vm.currentScene?.sources?.single() as SceneSource.ImageSource).filePath,
            )
        }
    }

    // ── The Bible editor ───────────────────────────────────────────────────────

    @Test
    fun `a Bible source is given the editor the tab was handed`() {
        val verse = SceneSource.BibleSource(id = "bib", name = "Verse")

        tab(verse, bible = { _, _ -> Text("VERSE PICKER") }) {
            onNodeWithText("VERSE PICKER").assertExists(
                "a Bible source with no picker cannot be pointed at a verse at all",
            )
        }
    }

    @Test
    fun `the Bible editor is given the source it is editing`() {
        var seen: SceneSource.BibleSource? = null
        val verse = SceneSource.BibleSource(id = "bib", name = "Verse")

        tab(verse, bible = { source, _ -> seen = source; Text("PICKER") }) {
            assertEquals("bib", seen?.id)
        }
    }

    @Test
    fun `what the Bible editor changes reaches the scene`() {
        var commit: ((SceneSource) -> Unit)? = null
        val verse = SceneSource.BibleSource(id = "bib", name = "Verse")

        tab(verse, bible = { source, onUpdate -> commit = onUpdate; Text("PICKER") }) { vm ->
            commit?.invoke(verse.copy(name = "John 3:16"))
            waitForIdle()

            assertEquals("John 3:16", vm.currentScene?.sources?.single()?.name)
        }
    }
}
