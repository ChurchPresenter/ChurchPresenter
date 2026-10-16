@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.presenter

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.viewmodel.LocalMediaViewModel
import org.churchpresenter.media.viewmodel.MediaViewModel
import org.churchpresenter.settings.MediaSettings
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaPresenterDefaultsTest {

    @Test
    fun `an output on its own defaults shows the clip without pausing it`() = runComposeUiTest {
        val vm = MediaViewModel().apply { loadMedia("https://example.org/clip.mp4", "url"); play() }
        setContent { CompositionLocalProvider(LocalMediaViewModel provides vm) { MediaPresenter() } }
        waitForIdle()
        assertTrue(vm.isPlaying)
    }

    @Test
    fun `an output that changes its settings keeps showing, and hiding it pauses`() = runComposeUiTest {
        val vm = MediaViewModel().apply { loadMedia("https://example.org/clip.mp4", "url"); play() }
        var visible by mutableStateOf(true)
        var alpha by mutableStateOf(1f)
        var subtitles by mutableStateOf(true)
        var profile by mutableStateOf("")
        var settings by mutableStateOf(MediaSettings())
        var scale by mutableStateOf(ContentScale.Fit)
        setContent {
            CompositionLocalProvider(LocalMediaViewModel provides vm) {
                MediaPresenter(
                    modifier = Modifier,
                    isVisible = visible,
                    transitionAlpha = alpha,
                    showSubtitles = subtitles,
                    profileId = profile,
                    mediaSettings = settings,
                    contentScale = scale,
                )
            }
        }
        waitForIdle()
        alpha = 0.5f
        subtitles = false
        profile = "lobby"
        settings = MediaSettings(textColor = "#FF0000")
        scale = ContentScale.Crop
        waitForIdle()
        assertTrue(vm.isPlaying)
        visible = false
        waitForIdle()
        assertFalse(vm.isPlaying)
    }
}
