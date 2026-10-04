package org.churchpresenter.canvas

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A canvas text layer saved with a font size of 0 (Sentry CHURCH-PRESENTER-DESKTOP-8Z).
 *
 * The layer's line height is its font size times its spacing, and Compose hands Skia the height as
 * line height over font size -- 0 ÷ 0, which Skia refuses with `IllegalStateException: Check
 * failed.` on the event thread, taking the whole app down mid-service. A 0 could be typed straight
 * into the Text and Bible layers' size fields, and a scene already saved with one, or sent over
 * Instance Link, still has to draw.
 */
@OptIn(ExperimentalTestApi::class)
class SceneZeroFontSizeTest {

    private fun drawsWithoutCrashing(source: SceneSource) = runComposeUiTest {
        setContent {
            SceneSourceRenderer(source = source, modifier = Modifier.size(320.dp, 180.dp).testTag("layer"))
        }
        waitForIdle()
        val bounds = onNodeWithTag("layer").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0f, "the layer was laid out: $bounds")
    }

    @Test
    fun `a text layer with a font size of 0 still draws`() =
        drawsWithoutCrashing(SceneSource.TextSource(id = "t", name = "Title", text = "Welcome", fontSize = 0))

    @Test
    fun `a Bible layer with verse and reference sizes of 0 still draws`() = drawsWithoutCrashing(
        SceneSource.BibleSource(
            id = "b",
            name = "Verse",
            verseText = "For God so loved the world",
            referenceText = "John 3:16",
            fontSize = 0,
            referenceFontSize = 0,
        ),
    )

    @Test
    fun `a clock with a font size of 0 still draws`() =
        drawsWithoutCrashing(SceneSource.ClockSource(id = "c", name = "Clock", fontSize = 0))

    @Test
    fun `a negative font size is treated the same way`() =
        drawsWithoutCrashing(SceneSource.TextSource(id = "t", name = "Title", text = "Welcome", fontSize = -4))
}
