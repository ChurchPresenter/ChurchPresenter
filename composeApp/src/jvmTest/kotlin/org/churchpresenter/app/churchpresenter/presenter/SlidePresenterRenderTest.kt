@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test

class SlidePresenterRenderTest {

    private val screen = Modifier.size(1920.dp, 1080.dp)
    private val presentedSlide = "Presented Slide"

    @Test
    fun `a bitmap slide is put on screen`() = runComposeUiTest {
        setContent { Box(screen) { SlidePresenter(slide = ImageBitmap(8, 8)) } }
        onNodeWithContentDescription(presentedSlide).assertExists()
    }

    @Test
    fun `a null slide shows nothing but does not crash`() = runComposeUiTest {
        setContent { Box(screen) { SlidePresenter(slide = null) } }
        onNodeWithContentDescription(presentedSlide).assertDoesNotExist()
    }

    @Test
    fun `key mode shows no slide content`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SlidePresenter(slide = ImageBitmap(8, 8), outputRole = Constants.OUTPUT_ROLE_KEY)
            }
        }
        onNodeWithContentDescription(presentedSlide).assertDoesNotExist()
    }

    @Test
    fun `crossfade with a previous slide renders both slides at once`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SlidePresenter(
                    slide = ImageBitmap(8, 8),
                    previousSlide = ImageBitmap(8, 8),
                    animationType = AnimationType.CROSSFADE,
                    transitionAlpha = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedSlide).assertCountEquals(2)
    }

    @Test
    fun `crossfade with no previous slide falls back to a single slide`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SlidePresenter(slide = ImageBitmap(8, 8), animationType = AnimationType.CROSSFADE)
            }
        }
        onAllNodesWithContentDescription(presentedSlide).assertCountEquals(1)
    }

    @Test
    fun `slide left with a previous slide renders both slides at once`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SlidePresenter(
                    slide = ImageBitmap(8, 8),
                    previousSlide = ImageBitmap(8, 8),
                    animationType = AnimationType.SLIDE_LEFT,
                    slideOffset = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedSlide).assertCountEquals(2)
    }

    @Test
    fun `slide right with a previous slide renders both slides at once`() = runComposeUiTest {
        setContent {
            Box(screen) {
                SlidePresenter(
                    slide = ImageBitmap(8, 8),
                    previousSlide = ImageBitmap(8, 8),
                    animationType = AnimationType.SLIDE_RIGHT,
                    slideOffset = 0.5f,
                )
            }
        }
        onAllNodesWithContentDescription(presentedSlide).assertCountEquals(2)
    }

    @Test
    fun `with no known window size, slide offsets and image scaling fall back to 1920x1080`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(LocalWindowInfo provides zeroSizeWindowInfo) {
                Box(screen) {
                    SlidePresenter(
                        slide = ImageBitmap(8, 8),
                        previousSlide = ImageBitmap(8, 8),
                        animationType = AnimationType.SLIDE_LEFT,
                        slideOffset = 0.5f,
                    )
                }
            }
        }
        onAllNodesWithContentDescription(presentedSlide).assertCountEquals(2)
    }
}
