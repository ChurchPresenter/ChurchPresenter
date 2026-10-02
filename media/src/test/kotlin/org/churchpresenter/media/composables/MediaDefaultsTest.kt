@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.composables

import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.media.utils.mediaDurationSeconds
import org.churchpresenter.media.viewmodel.MediaViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class MediaDefaultsTest {

    @Test
    fun `detection on this machine is detection for this machine's platform`() {
        val os = System.getProperty("os.name", "").lowercase()
        assertEquals(detectVlcInstallPathFor(os), detectVlcInstallPath())
    }

    @Test
    fun `a clip that is not there has no duration with the bundled ffmpeg either`() {
        assertNull(mediaDurationSeconds("/no/such/clip.mp4"))
    }

    @Test
    fun `a cue for a clip that has not loaded waits`() {
        val vm = MediaViewModel()
        vm.cue.requestPlayback(plays = 1, url = "")
        assertFalse(vm.isPlaying)
    }

    @Test
    fun `the shared output stands up on its own defaults`() = runComposeUiTest {
        SharedVideoOutput.frame.value = null
        setContent { SharedVideoOutputDisplay() }
        waitForIdle()
    }
}
