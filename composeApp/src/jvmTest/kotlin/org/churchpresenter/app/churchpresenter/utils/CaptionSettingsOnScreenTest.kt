package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.STTSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which captions the STT tab's own transcript follows, now that captions are styled per profile:
 * a profile an output is using that shows captions, else any that shows them, else the document.
 */
class CaptionSettingsOnScreenTest {

    private val idle = OutputProfile(id = "idle", sttSettings = STTSettings(maxSegments = 1))
    private val live = OutputProfile(id = "live", sttSettings = STTSettings(maxSegments = 2))
    private val silent = OutputProfile(id = "silent", showSTT = false, sttSettings = STTSettings(maxSegments = 3))

    private fun doc(profiles: List<OutputProfile>, assigned: String? = null) = AppSettings(
        sttSettings = STTSettings(maxSegments = 9, serverUrl = "http://stt.local"),
        projectionSettings = ProjectionSettings(
            outputProfiles = profiles,
            screenAssignments = listOfNotNull(assigned?.let { ScreenAssignment(activeProfileId = it) }),
        ),
    )

    @Test
    fun `the profile on an output wins over one that is not`() {
        assertEquals(2, doc(listOf(idle, live), assigned = "live").captionSettingsOnScreen().maxSegments)
    }

    @Test
    fun `with none on an output the first captioning profile is used`() {
        assertEquals(1, doc(listOf(idle, live)).captionSettingsOnScreen().maxSegments)
    }

    @Test
    fun `a profile that shows no captions is passed over, even on an output`() {
        assertEquals(1, doc(listOf(silent, idle), assigned = "silent").captionSettingsOnScreen().maxSegments)
    }

    @Test
    fun `with no profile showing captions the document's copy is used`() {
        assertEquals(9, doc(listOf(silent)).captionSettingsOnScreen().maxSegments)
    }

    @Test
    fun `the server is always the install's`() {
        assertEquals("http://stt.local", doc(listOf(live), assigned = "live").captionSettingsOnScreen().serverUrl)
    }
}
