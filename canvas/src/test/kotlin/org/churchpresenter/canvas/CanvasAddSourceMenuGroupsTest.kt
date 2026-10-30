@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.graphics.toPixelMap
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.canvas_layer_group_content
import org.churchpresenter.strings.generated.resources.canvas_layer_group_live
import org.churchpresenter.strings.generated.resources.canvas_layer_group_sources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CanvasAddSourceMenuGroupsTest {

    private val names = CanvasSourceNames(
        strImage = "Image", strText = "Text", strColor = "Color", strVideo = "Video", strTimer = "Timer",
        strQrCode = "QR Code", strCamera = "Camera", strScreenCapture = "Screen Capture", strNdi = "NDI",
        strOmt = "OMT", strBrowser = "Browser", strBible = "Bible",
    )

    private val groups = names.newSourceGroups()

    @Test
    fun `the menu is three groups of four, titled content, live info and sources`() {
        assertEquals(
            listOf(
                Res.string.canvas_layer_group_content,
                Res.string.canvas_layer_group_live,
                Res.string.canvas_layer_group_sources,
            ),
            groups.map { it.title },
        )
        assertEquals(
            listOf(
                listOf("Image", "Text", "Color", "Video"),
                listOf("Timer", "QR Code", "Bible", "Browser"),
                listOf("Camera", "Screen Capture", "NDI", "OMT"),
            ),
            groups.map { group -> group.items.map { it.label } },
        )
    }

    @Test
    fun `every kind of layer has an icon of its own`() {
        val icons = groups.flatMap { group -> group.items.map { it.icon } }

        assertEquals(icons.size, icons.toSet().size)
    }

    private fun ComposeUiTest.topOf(text: String): Float =
        onAllNodesWithText(text).fetchSemanticsNodes().first().boundsInRoot.top

    @Test
    fun `the open menu shows each group's title above its layers, in order`() =
        canvasTab(seed = { addScene("Scene") }) { _, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performClick()
            waitForIdle()

            val order = listOf(
                "CONTENT", CanvasLabel.IMAGE, CanvasLabel.VIDEO,
                "LIVE INFO", CanvasLabel.CLOCK, CanvasLabel.BROWSER,
                "SOURCES", CanvasLabel.CAMERA, CanvasLabel.OMT,
            ).map { topOf(it) }

            assertEquals(order.sorted(), order)
        }

    @Test
    fun `a layer under the pointer is lit`() =
        canvasTab(seed = { addScene("Scene") }) { _, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performClick()
            waitForIdle()
            val item = onNodeWithText(CanvasLabel.CAMERA)
            val before = item.captureToImage().toPixelMap()
            val corner = before[before.width - 1, before.height / 2]

            item.performMouseInput { moveTo(center) }
            waitForIdle()
            val after = onNodeWithText(CanvasLabel.CAMERA).captureToImage().toPixelMap()

            assertTrue(onAllNodesWithText(CanvasLabel.CAMERA).fetchSemanticsNodes().isNotEmpty())
            assertNotEquals(corner, after[after.width - 1, after.height / 2])
        }

    @Test
    fun `resting the pointer on the plus button names it`() =
        canvasTab(seed = { addScene("Scene") }) { _, _ ->
            canvasButton(CanvasLabel.ADD_SOURCE).performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
            waitForIdle()

            assertTrue(
                onAllNodesWithText(CanvasLabel.ADD_SOURCE).fetchSemanticsNodes(atLeastOneRootRequired = false)
                    .isNotEmpty(),
            )
        }
}

private const val TOOLTIP_WAIT_MS = 1_000L
