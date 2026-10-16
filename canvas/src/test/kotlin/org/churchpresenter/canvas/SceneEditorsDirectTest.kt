@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.scene.ClockModes
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneEditorsDirectTest {

    @AfterTest
    fun stopTimers() = clearSceneTimers()

    private fun ComposeUiTest.clickOutline(index: Int = 0) {
        onAllNodes(hasText("O") and hasClickAction())[index].performScrollTo().performClick()
        waitForIdle()
    }

    @Test
    fun `the text editor's outline button turns the outline on`() = sourcePanel(Fixture.text()) { get ->
        clickOutline()

        assertTrue((get() as SceneSource.TextSource).outline.enabled)
    }

    @Test
    fun `the clock editor's outline button turns the outline on`() =
        sourcePanel(SceneSource.ClockSource(id = "clk-o", name = "Clock", mode = ClockModes.CLOCK)) { get ->
            clickOutline()

            assertTrue((get() as SceneSource.ClockSource).outline.enabled)
        }

    @Test
    fun `the Bible verse and reference each have their own outline`() = sourcePanel(Fixture.bible()) { get ->
        clickOutline(0)
        val afterVerse = get() as SceneSource.BibleSource
        assertTrue(afterVerse.outline.enabled)
        assertTrue(!afterVerse.referenceOutline.enabled)

        clickOutline(1)
        assertTrue((get() as SceneSource.BibleSource).referenceOutline.enabled)
    }

    @Test
    fun `a gradient shape's second colour can be changed`() {
        val shape = SceneSource.ShapeSource(id = "shp-g", name = "Box", isGradient = true, gradientColor2 = "#123456")
        sourcePanel(shape) { get ->
            recolor(fromHex = "#123456", toHex = "#0044CC")

            assertEquals("#0044CC", (get() as SceneSource.ShapeSource).gradientColor2)
        }
    }

    @Test
    fun `the Bible verse font is chosen from the font list`() = sourcePanel(Fixture.bible()) { get ->
        val chosen = uniquelyNamedFont()
        chooseFont(showing = Fixture.bible().fontFamily, option = chosen)

        assertEquals(chosen, (get() as SceneSource.BibleSource).fontFamily)
    }

    @Test
    fun `the camera privacy hint and the region editor work with their defaults`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Column {
                    CameraPrivacyHint()
                    ScreenCaptureProperties(Fixture.capture(), onUpdate = {})
                }
            }
        }
        waitForIdle()

        onNodeWithText("Screen Region").assertExists()
    }
}
