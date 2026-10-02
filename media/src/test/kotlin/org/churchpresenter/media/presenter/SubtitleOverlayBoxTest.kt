@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.subtitles.SubtitleCue
import org.churchpresenter.settings.MediaSettings
import org.churchpresenter.settings.SUBTITLE_BOX
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertTrue

class SubtitleOverlayBoxTest {

    private fun boxed(position: String) = MediaSettings(
        position = position,
        textBoxes = mapOf(textBoxKey(SUBTITLE_BOX, lowerThird = false) to TextBox(enabled = true)),
    )

    private fun drawsIn(settings: MediaSettings) = runComposeUiTest {
        setContent {
            Box(Modifier.size(300.dp).background(Color.Black)) {
                SubtitleOverlay(cue = SubtitleCue(startMs = 0, endMs = 5_000, text = "In the box"), settings)
            }
        }
        waitForIdle()
        assertTrue(showsExactly("In the box"))
    }

    @Test
    fun `a cue drawn in a box on the left`() = drawsIn(boxed("Bottom Left"))

    @Test
    fun `a cue drawn in a box on the right`() = drawsIn(boxed("Bottom Right"))

    @Test
    fun `a cue drawn in a box in the middle`() = drawsIn(boxed("Bottom Center"))
}
