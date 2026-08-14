@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

class PresentationPresenterBranchTest {

    private val screen = Modifier.size(200.dp, 200.dp)
    private val presentedSlide = "Presented Slide"

    @Test
    fun `a null frame falls back to the static slide`() = runComposeUiTest {
        setContent {
            Box(screen) { PresentationPresenter(frame = null, slide = ImageBitmap(8, 8)) }
        }
        onNodeWithContentDescription(presentedSlide).assertExists()
    }

    @Test
    fun `frozen forces the fallback to blank even when a frame and slide are both present`() = runComposeUiTest {
        setContent {
            Box(screen) {
                PresentationPresenter(
                    frame = presentationFrame(listOf(placedLayer(Color.Red))),
                    slide = ImageBitmap(8, 8),
                    frozen = true,
                )
            }
        }
        onNodeWithContentDescription(presentedSlide).assertDoesNotExist()
    }

    @Test
    fun `key output is a plain white box with no slide or canvas content`() = runComposeUiTest {
        setContent {
            Box(screen) {
                PresentationPresenter(
                    frame = presentationFrame(listOf(placedLayer(Color.Red))),
                    slide = ImageBitmap(8, 8),
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                )
            }
        }
        onNodeWithContentDescription(presentedSlide).assertDoesNotExist()
    }

    @Test
    fun `a live frame renders the canvas path, not the static slide`() = runComposeUiTest {
        setContent {
            Box(screen) {
                PresentationPresenter(frame = presentationFrame(listOf(placedLayer(Color.Red))),
                    slide = ImageBitmap(8, 8))
            }
        }
        onNodeWithContentDescription(presentedSlide).assertDoesNotExist()
    }

    @Test
    fun `a zero-size canvas does not crash`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(0.dp)) {
                PresentationPresenter(frame = presentationFrame(listOf(placedLayer(Color.Red))), slide = null)
            }
        }
    }
}
