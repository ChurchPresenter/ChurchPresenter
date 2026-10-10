@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.sharedui.testing.showsContainingText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.sharedui.utils.withPictureScaleEverywhere
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabHostChangesTest {

    @Test
    fun `the blank button follows the host's frozen state as it changes`() {
        val settings = AppSettings()
        val vm = PresentationViewModel(settings)
        var frozen by mutableStateOf(false)
        try {
            runComposeUiTest {
                setContent {
                    MaterialTheme {
                        PresentationTab(
                            appSettings = settings,
                            viewModel = vm,
                            presenterManager = FakeSlidesOutput(),
                            presentationFrozen = frozen,
                        )
                    }
                }
                presentationButton(PresentationLabel.BLANK_OUTPUT).assertExists()
                frozen = true
                waitForIdle()
                presentationButton(PresentationLabel.UNBLANK_OUTPUT).assertExists()
                frozen = false
                waitForIdle()
                presentationButton(PresentationLabel.BLANK_OUTPUT).assertExists()
            }
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun `the scale button follows settings the host changes`() {
        var settings by mutableStateOf(AppSettings().withPictureScaleEverywhere(OutputScaleMode.FIT))
        val vm = PicturesViewModel()
        try {
            runComposeUiTest {
                setContent { MaterialTheme { PicturesTab(appSettings = settings, viewModel = vm) } }
                pictureButton("Picture scale on every output: Fit").assertExists()
                settings = settings.withPictureScaleEverywhere(OutputScaleMode.STRETCH)
                waitForIdle()
                pictureButton("Picture scale on every output: Stretch").assertExists()
            }
        } finally {
            vm.dispose()
        }
    }

    @Test
    fun `a transition changed on its own, then an animation, each reach the output`() {
        val out = FakeSlidesOutput()
        presentationTab(presenterManager = out) { vm, _ ->
            vm.transitionDuration = 900f
            waitForIdle()
            assertEquals(900, out.transitionDuration.value)
            vm.animationType = AnimationType.FADE
            waitForIdle()
            assertEquals(AnimationType.FADE, out.animationType.value)
        }
    }

    @Test
    fun `a picture transition changed on its own, then an animation, each reach the output`() {
        val out = FakeSlidesOutput()
        picturesTab(presenterManager = out) { vm, _ ->
            vm.transitionDuration = 700f
            waitForIdle()
            assertEquals(700, out.transitionDuration.value)
            vm.animationType = AnimationType.SLIDE_RIGHT
            waitForIdle()
            assertEquals(AnimationType.SLIDE_RIGHT, out.animationType.value)
        }
    }

    @Test
    fun `a narrow tab puts the shortcut hint on its own row`() {
        val (dir, files) = fakeSlideFiles(2)
        try {
            presentationTab(width = 360.dp) { vm, _ ->
                vm.slideFiles.addAll(files)
                waitUntil("the thumbnails drawn", 5_000) {
                    onAllNodesWithContentDescription("Slide 1").fetchSemanticsNodes().isNotEmpty()
                }
                waitForIdle()
                assertTrue(showsContainingText("blank screen"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }
}
