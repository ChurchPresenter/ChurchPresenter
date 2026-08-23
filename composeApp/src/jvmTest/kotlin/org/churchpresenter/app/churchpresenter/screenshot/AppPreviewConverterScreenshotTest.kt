@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.TestSingletons
import org.churchpresenter.converter.ui.App
import org.churchpresenter.converter.ui.ConverterTheme
import java.io.File
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import org.churchpresenter.ui.screenshot.captureTo

/**
 * The bundled format converter, reached from the Help menu. It ships its own colour scheme rather
 * than following the app theme, so each state is one image instead of a light/dark pair.
 *
 * The Songs tab is shot several times over because its whole layout is driven by which format is
 * selected in the rail — the destination step alone is a plain "same as input" line for SongBeamer
 * and a required-folder warning for SoftProjector, and only the rail tells you which you are
 * looking at.
 */
class AppPreviewConverterScreenshotTest {

    private fun converter(name: String, drive: ComposeUiTest.() -> Unit = {}) {
        TestSingletons.latchSkikoHostOs()
        TestSingletons.latchToTestHome()
        runSkikoComposeUiTest(size = Size(1100f, 800f), density = Density(1f)) {
            setContent {
                ConverterTheme {
                    Box(Modifier.size(1100.dp, 800.dp)) { App() }
                }
            }
            waitForIdle()
            drive()
            waitForIdle()
            captureTo(File("$SCREENSHOT_ROOT/previewApp/converter_$name.png"))
        }
    }

    private fun ComposeUiTest.tab(title: String) {
        onNodeWithText(title).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.format(name: String) {
        onNodeWithText(name).performClick()
        waitForIdle()
    }

    @Test
    fun bibles() = converter("bibles")

    /** The rail's default selection: one .song per input, so the destination may be left alone. */
    @Test
    fun songs() = converter("songs") { tab("Songs") }

    @Test
    fun `songs free worship`() = converter("songs_freeworship") {
        tab("Songs")
        format("Free Worship")
    }

    /** A song book fans out into a folder, so this state carries the output-folder warning. */
    @Test
    fun `songs soft projector`() = converter("songs_softprojector") {
        tab("Songs")
        format("SoftProjector")
    }

    @Test
    fun `songs documents`() = converter("songs_documents") {
        tab("Songs")
        format("Documents")
    }

    /** Searching narrows the rail; the note about requesting a format stays put beneath it. */
    @Test
    fun `songs search filtered`() = converter("songs_search") {
        tab("Songs")
        onNodeWithText("Search formats…").performTextInput("sp")
    }

    @Test
    fun `songs search with no match`() = converter("songs_search_empty") {
        tab("Songs")
        onNodeWithText("Search formats…").performTextInput("propresenter")
    }

    @Test
    fun duplicates() = converter("duplicates") { tab("Duplicates") }

    @Test
    fun rename() = converter("rename") { tab("Rename") }

    /** The example line is computed from the options, so it has to be shot with them changed. */
    @Test
    fun `rename with options changed`() = converter("rename_options") {
        tab("Rename")
        onNodeWithText("Rename to the first line of verse 1").performClick()
        onNodeWithText("Title Case").performClick()
    }
}
