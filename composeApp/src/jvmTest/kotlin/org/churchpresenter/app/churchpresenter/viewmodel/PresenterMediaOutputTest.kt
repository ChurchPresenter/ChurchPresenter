package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.liveoutput.PresenterMediaOutput
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PresenterMediaOutputTest {

    @Test
    fun `the media tab's calls reach the live output unchanged`() {
        val manager = PresenterManager()
        val output = PresenterMediaOutput(manager)

        output.setPresentingMode(Presenting.MEDIA)
        output.setShowPresenterWindow(true)
        output.setCurrentMedia("file:///clip.mp4", "local")

        assertTrue(output.isLive(Presenting.MEDIA))
        assertTrue(output.showPresenterWindow.value)
        assertEquals("file:///clip.mp4", manager.currentMediaUrl.value)
        assertEquals("local", manager.currentMediaType.value)

        output.requestClearDisplay()
        assertTrue(manager.clearDisplayRequested.value)
    }
}
