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

    /** The canvas's bounds inside a 800×400 area, and that area's own. */
    private fun canvasIn(content: @Composable (Modifier) -> Unit): Pair<Rect, Rect> {
        var canvas = Rect.Zero
        var area = Rect.Zero
        runComposeUiTest {
            setContent {
                Box(Modifier.size(800.dp, 400.dp).testTag(AREA_TAG)) { content(Modifier.fillMaxSize()) }
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
}
