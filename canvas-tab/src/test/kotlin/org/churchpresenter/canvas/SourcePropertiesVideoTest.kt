@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.scene.SceneSource
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Video source: a file path with a Browse button, a Loop flag and a volume slider.
 *
 * Browse defaults to the platform's own file dialog, driven end to end here the same way
 * [SourcePropertiesImageTest] does via a [FakeFileChooser] passed to `sourcePanel`. Everything else
 * here is a plain read-one-field/write-one-field control, and what each test pins is that it writes
 * the field it belongs to and leaves its two neighbours alone — the panel's three controls are close
 * enough in shape that a mis-wired `copy(...)` would otherwise go unnoticed.
 */
class SourcePropertiesVideoTest {

    /** Records the call it received and returns [answer] without ever touching a real dialog. */
    private class FakeFileChooser(private val answer: Path?) : CanvasFilePicker {
        var callCount: Int = 0
            private set
        var lastPath: Path? = null
            private set
        var lastTitle: String? = null
            private set
        var lastFilters: List<javax.swing.filechooser.FileNameExtensionFilter> = emptyList()
            private set

        override suspend fun chooseSingle(
            path: Path?,
            filters: List<javax.swing.filechooser.FileNameExtensionFilter>,
            title: String,
        ): Path? {
            callCount++
            lastPath = path
            lastFilters = filters
            lastTitle = title
            return answer
        }
    }

    /** Ordinal of the file path field — the header owns the first six. */
    private val filePathField = 6

    // ── What the panel displays ───────────────────────────────────────────────

    @Test
    fun `the section is headed and every control captioned`() = sourcePanel(Fixture.video()) { _ ->
        onNodeWithText(Label.VIDEO).assertIsDisplayed()
        onNodeWithText("FILE PATH").assertIsDisplayed()
        onNodeWithText("Loop").assertIsDisplayed()
        onNodeWithText("Volume").assertIsDisplayed()
    }

    @Test
    fun `the video panel adds one field and one checkbox to the header`() = sourcePanel(Fixture.video()) { _ ->
        textFields().assertCountEquals(7)
        checkboxes().assertCountEquals(1)
    }

    @Test
    fun `the file path field shows the stored path`() = sourcePanel(Fixture.video()) { _ ->
        assertFieldShows("/tmp/bumper.mp4", "the file path field")
    }

    @Test
    fun `the volume slider reads out its stored level`() {
        sourcePanel(Fixture.video().copy(volume = 0.35f)) { _ ->
            onNodeWithText("35%").assertExists("the volume slider reads out whole percent")
        }
    }

    @Test
    fun `the Browse button is on screen and clickable`() = sourcePanel(Fixture.video()) { _ ->
        onNodeWithContentDescription("Browse").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun `clicking Browse opens the chooser with a video filter, at the stored path's directory`() {
        // A real directory, not a literal "/tmp": FileChooser falls back to the home directory for
        // a start path that does not exist, and "/tmp" is not one on Windows.
        val dir = Files.createTempDirectory("cp-video-browse")
        val chooser = FakeFileChooser(answer = null)
        try {
            sourcePanel(Fixture.video(filePath = dir.resolve("bumper.mp4").toString()), fileChooser = chooser) { _ ->
                onNodeWithContentDescription("Browse").performClick()
                waitForIdle()

                assertEquals(1, chooser.callCount)
                assertEquals(dir, chooser.lastPath, "the chooser must open at the stored file's parent directory")
                assertEquals(
                    listOf("mp4", "mov", "avi", "mkv", "wmv", "flv", "webm", "m4v"),
                    chooser.lastFilters?.single()?.extensions?.toList(),
                )
            }
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `choosing a file from Browse stores its path`() {
        val chosen = Path.of("/media/clips/outro.mov")
        val chooser = FakeFileChooser(answer = chosen)
        sourcePanel(Fixture.video(), fileChooser = chooser) { get ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            // The panel stores what the chooser answered, absolutised — which on Windows gains a
            // drive letter, so the expectation is derived rather than spelled out.
            assertEquals(chosen.absolutePathString(), (get() as SceneSource.VideoSource).filePath)
        }
    }

    @Test
    fun `canceling Browse leaves the stored path untouched`() {
        val chooser = FakeFileChooser(answer = null)
        sourcePanel(Fixture.video(), fileChooser = chooser) { get ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            assertEquals(
                "/tmp/bumper.mp4",
                (get() as SceneSource.VideoSource).filePath,
                "canceling must not touch the stored path",
            )
        }
    }

    @Test
    fun `with no stored path, Browse asks with no start directory`() {
        val chooser = FakeFileChooser(answer = null)
        sourcePanel(Fixture.video().copy(filePath = ""), fileChooser = chooser) { _ ->
            onNodeWithContentDescription("Browse").performClick()
            waitForIdle()

            // Null, not the home directory. The tab has nowhere to start from and says so; where a
            // null start path lands is the picker's decision, and the app's real one falls back to
            // user.home exactly as it did before. Asserting the home path here would be asserting
            // the implementation's behaviour through a stand-in that does not implement it.
            assertNull(chooser.lastPath)
        }
    }

    // ── File path ─────────────────────────────────────────────────────────────

    @Test
    fun `typing a path stores it and nothing else`() = sourcePanel(Fixture.video()) { get ->
        typeField(filePathField, "/media/clips/welcome.mov")

        assertEquals(
            Fixture.video().copy(filePath = "/media/clips/welcome.mov"), get(),
            "the field writes only the source's path",
        )
        assertFieldShows("/media/clips/welcome.mov", "the file path field after typing")
    }

    @Test
    fun `a network stream URL is accepted as a path`() = sourcePanel(Fixture.video()) { get ->
        typeField(filePathField, "rtsp://10.0.0.9:554/stream")

        assertEquals(
            "rtsp://10.0.0.9:554/stream", (get() as SceneSource.VideoSource).filePath,
            "the field is not restricted to local files",
        )
    }

    @Test
    fun `the path can be cleared`() = sourcePanel(Fixture.video()) { get ->
        typeField(filePathField, "")

        assertEquals("", (get() as SceneSource.VideoSource).filePath)
    }

    // ── Loop ──────────────────────────────────────────────────────────────────

    @Test
    fun `Loop is off out of the box`() = sourcePanel(Fixture.video()) { _ ->
        checkboxes()[0].assertIsOff()
    }

    @Test
    fun `ticking Loop stores the flag and shows it ticked`() = sourcePanel(Fixture.video()) { get ->
        toggleCheckbox(0)

        assertEquals(
            Fixture.video().copy(loop = true), get(),
            "ticking Loop may change only that flag",
        )
        checkboxes()[0].assertIsOn()
    }

    @Test
    fun `unticking Loop turns looping back off`() {
        sourcePanel(Fixture.video().copy(loop = true)) { get ->
            checkboxes()[0].assertIsOn()
            toggleCheckbox(0)

            assertEquals(false, (get() as SceneSource.VideoSource).loop)
            checkboxes()[0].assertIsOff()
        }
    }

    // ── Volume ────────────────────────────────────────────────────────────────

    @Test
    fun `dragging the volume slider to its near end mutes the clip`() = sourcePanel(Fixture.video()) { get ->
        tapSliderUnder("Volume", fraction = 0f, gapDp = Gap.READOUT)

        assertEquals(0f, (get() as SceneSource.VideoSource).volume, "the near end of the track is silence")
        onNodeWithText("0%").assertExists("and the read-out follows immediately")
    }

    @Test
    fun `dragging the volume slider to its far end is full volume`() {
        sourcePanel(Fixture.video().copy(volume = 0.1f)) { get ->
            tapSliderUnder("Volume", fraction = 1f, gapDp = Gap.READOUT)

            assertEquals(1f, (get() as SceneSource.VideoSource).volume)
            // Two read-outs now say 100%: the header's opacity, and the volume just dragged.
            assertEquals(2, countOf("100%"), "the volume read-out joins the header's opacity at 100%")
        }
    }

    @Test
    fun `a mid-track tap on the volume slider lands between the ends`() = sourcePanel(Fixture.video()) { get ->
        tapSliderUnder("Volume", fraction = 0.5f, gapDp = Gap.READOUT)

        val volume = (get() as SceneSource.VideoSource).volume
        assertTrue(volume > 0f && volume < 1f, "a mid-track tap lands inside the range, was $volume")
        assertEquals(
            1, countOf("${(volume * 100).toInt()}%"),
            "and the read-out shows exactly the value that was stored",
        )
    }

    @Test
    fun `changing the volume leaves the path and the loop flag alone`() {
        sourcePanel(Fixture.video().copy(loop = true)) { get ->
            tapSliderUnder("Volume", fraction = 0f, gapDp = Gap.READOUT)

            val source = get() as SceneSource.VideoSource
            assertEquals("/tmp/bumper.mp4", source.filePath)
            assertEquals(true, source.loop)
        }
    }
}
