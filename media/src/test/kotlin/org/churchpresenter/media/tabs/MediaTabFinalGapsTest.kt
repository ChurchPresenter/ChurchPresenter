@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.media.tabs

import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.testing.showsExactly
import kotlin.test.Test
import kotlin.test.assertEquals

class MediaTabFinalGapsTest {

    @Test
    fun `a scheduled clip missing here is streamed from the primary`() {
        val item = ScheduleItem.MediaItem("m9", "/not/on/this/machine.mp4", "Bumper", Constants.MEDIA_TYPE_LOCAL)
        val stream: (String) -> String = { id -> "http://primary:8080/media/$id" }
        mediaTab(selectedMediaItem = item, instanceLinkMediaStreamUrl = stream) { vm, _ ->
            waitUntil(timeoutMillis = 5_000) { vm.isLoaded }
            assertEquals("http://primary:8080/media/m9", vm.mediaUrl)
        }
    }

    @Test
    fun `the play key names what it will do next`() = mediaTab { vm, _ ->
        vm.loadMedia("https://example.org/clip.mp4", Constants.MEDIA_TYPE_URL)
        waitForIdle()
        mediaButton(MediaLabel.PLAY).performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { showsExactly(MediaLabel.PLAY) }
        vm.play()
        waitForIdle()
        mediaButton(MediaLabel.PAUSE).performMouseInput { moveTo(topLeft) }
        mediaButton(MediaLabel.PAUSE).performMouseInput { moveTo(center) }
        waitUntil(timeoutMillis = 5_000) { showsExactly(MediaLabel.PAUSE) }
    }
}
