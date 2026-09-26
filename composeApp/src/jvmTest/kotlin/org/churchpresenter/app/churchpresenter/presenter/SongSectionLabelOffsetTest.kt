package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.SongLayoutExtras
import org.churchpresenter.settings.SongSectionLabel
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The section label, once positioned, going where its X and Y say.
 *
 * X is the half that was broken (#656): the label was still laid out the full width of the frame
 * with its text centred inside, so `elementOffset` had no horizontal room to move it through and
 * every X drew it in the middle. Y worked throughout, which is what made it look like a setting that
 * only half applied. Asserted on measured bounds, not on the label merely being on screen.
 */
@OptIn(ExperimentalTestApi::class)
class SongSectionLabelOffsetTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)

    private val section = LyricSection(
        header = "[Verse 1]",
        type = Constants.SECTION_TYPE_VERSE,
        lines = listOf("Amazing grace how sweet the sound"),
    )

    private fun settings(offset: ElementOffset) = AppSettings(
        songSettings = SongSettings(
            layoutExtras = SongLayoutExtras(sectionLabel = SongSectionLabel(enabled = true, offset = offset)),
        ),
    )

    private fun present(offset: ElementOffset, block: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(screen) {
                    SongPresenter(lyricSection = section, appSettings = settings(offset))
                }
            }
        }
        block()
    }

    private fun ComposeUiTest.labelBounds(): Rect = onNodeWithText("Verse 1").fetchSemanticsNode().boundsInRoot

    private fun ComposeUiTest.outputBounds(): Rect = onRoot().fetchSemanticsNode().boundsInRoot

    @Test
    fun `X 0 puts the label on the left and X 100 on the right`() {
        present(ElementOffset(xPercent = 0, yPercent = 0)) {
            val label = labelBounds()
            val output = outputBounds()
            assertTrue(label.right < output.center.x, "X 0 must be left of centre: $label of $output")
        }
        present(ElementOffset(xPercent = 100, yPercent = 0)) {
            val label = labelBounds()
            val output = outputBounds()
            assertTrue(label.left > output.center.x, "X 100 must be right of centre: $label of $output")
        }
    }

    @Test
    fun `Y still moves the label from top to bottom`() {
        present(ElementOffset(xPercent = 50, yPercent = 0)) {
            assertTrue(labelBounds().center.y < outputBounds().center.y, "Y 0 must be in the top half")
        }
        present(ElementOffset(xPercent = 50, yPercent = 100)) {
            assertTrue(labelBounds().center.y > outputBounds().center.y, "Y 100 must be in the bottom half")
        }
    }
}
