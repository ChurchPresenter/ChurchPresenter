package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.presenter.ScenePresenter
import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneAlternateLayout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A scene drawn at its own shape inside whatever area it is given (#608).
 *
 * Both callers hand `SceneCanvas` a `fillMaxSize()` modifier. Applied to the canvas itself, that
 * pinned its minimum size to the whole area, so `aspectRatio` sized a portrait scene from the width
 * and it came out far taller than its area -- over the Canvas tab's buttons, and cropped to a band on
 * a landscape output. It now fits the area, whole, and sits in its middle.
 */
@OptIn(ExperimentalTestApi::class)
class SceneCanvasFitTest {

    private companion object {
        const val AREA_TAG = "fit_area"
    }

    private val portrait = Scene(id = "p", name = "Portrait", canvasWidth = 1080, canvasHeight = 1920)

    /** A landscape scene that also has a portrait layout. */
    private val both = Scene(
        id = "b",
        name = "Both",
        canvasWidth = 1920,
        canvasHeight = 1080,
        alternate = SceneAlternateLayout(),
    )

    /** The canvas's bounds inside a [width]×[height] area (800×400 unless named), and that area's own. */
    private fun canvasIn(
        width: Int = 800,
        height: Int = 400,
        content: @Composable (Modifier) -> Unit,
    ): Pair<Rect, Rect> {
        var canvas = Rect.Zero
        var area = Rect.Zero
        runComposeUiTest {
            setContent {
                Box(Modifier.size(width.dp, height.dp).testTag(AREA_TAG)) { content(Modifier.fillMaxSize()) }
            }
            canvas = onNodeWithTag(SCENE_CANVAS_TAG).fetchSemanticsNode().boundsInRoot
            area = onNodeWithTag(AREA_TAG).fetchSemanticsNode().boundsInRoot
        }
        return canvas to area
    }

    private fun assertFitsAndCentred(canvas: Rect, area: Rect) {
        val within = "within the area: $canvas of $area"
        assertTrue(canvas.top >= area.top - 1f && canvas.bottom <= area.bottom + 1f, within)
        assertTrue(canvas.left >= area.left - 1f && canvas.right <= area.right + 1f, within)
        val ratio = canvas.width / canvas.height
        assertTrue(abs(ratio - 1080f / 1920f) < 0.02f, "kept its 9:16 shape: $ratio")
        assertTrue(abs(canvas.center.x - area.center.x) < 1f, "centred across the area: $canvas of $area")
        assertTrue(canvas.height > area.height * 0.95f, "as large as the area allows: $canvas of $area")
    }

    @Test
    fun `a portrait scene fits inside a landscape area in the Canvas tab`() {
        val (canvas, area) = canvasIn { modifier ->
            SceneCanvas(
                modifier = modifier,
                scene = portrait,
                selectedSourceId = null,
                onSourceSelected = {},
                onTransformChanged = { _, _ -> },
            )
        }
        assertFitsAndCentred(canvas, area)
    }

    @Test
    fun `a portrait scene fits inside a landscape output`() {
        val (canvas, area) = canvasIn { modifier -> ScenePresenter(modifier = modifier, scene = portrait) }
        assertFitsAndCentred(canvas, area)
    }

    private fun shapeOf(canvas: Rect) = canvas.width / canvas.height

    @Test
    fun `a scene with two layouts draws the portrait one on a tall output`() {
        val (canvas, _) = canvasIn(width = 300, height = 600) { modifier ->
            ScenePresenter(modifier = modifier, scene = both)
        }
        assertTrue(abs(shapeOf(canvas) - 1080f / 1920f) < 0.02f, "drew 9:16: ${shapeOf(canvas)}")
    }

    @Test
    fun `a scene with two layouts draws the landscape one on a wide output`() {
        val (canvas, _) = canvasIn { modifier -> ScenePresenter(modifier = modifier, scene = both) }
        assertTrue(abs(shapeOf(canvas) - 1920f / 1080f) < 0.02f, "drew 16:9: ${shapeOf(canvas)}")
    }

    @Test
    fun `the editor can ask for one layout whatever the area's shape`() {
        val (canvas, _) = canvasIn(width = 300, height = 600) { modifier ->
            SceneCanvas(
                modifier = modifier,
                scene = both,
                selectedSourceId = null,
                onSourceSelected = {},
                onTransformChanged = { _, _ -> },
                autoLayout = false,
            )
        }
        assertTrue(abs(shapeOf(canvas) - 1920f / 1080f) < 0.02f, "kept the layout it was given: ${shapeOf(canvas)}")
    }
}
