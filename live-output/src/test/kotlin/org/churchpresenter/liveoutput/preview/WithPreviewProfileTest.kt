package org.churchpresenter.liveoutput.preview

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.getBrowserSourceOutput
import org.churchpresenter.settings.getNdiOutput
import org.churchpresenter.settings.getOmtOutput
import org.churchpresenter.sharedui.utils.OutputKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** The header's profile picker points exactly the output it sits on at the picked profile. */
class WithPreviewProfileTest {

    private val settings = AppSettings(
        projectionSettings = ProjectionSettings(
            browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
            ndiOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
            omtOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
        ),
    )

    @Test
    fun `each kind of output takes the picked profile at its own index, and only there`() {
        val ndi = withPreviewProfile(OutputKind.NDI, 1, "stage")(settings).projectionSettings
        assertEquals("stage", ndi.getNdiOutput(1).activeProfileId)
        assertEquals(ScreenAssignment().activeProfileId, ndi.getNdiOutput(0).activeProfileId)

        val omt = withPreviewProfile(OutputKind.OMT, 0, "stream")(settings).projectionSettings
        assertEquals("stream", omt.getOmtOutput(0).activeProfileId)
        assertEquals(ScreenAssignment().activeProfileId, omt.getOmtOutput(1).activeProfileId)

        val browser = withPreviewProfile(OutputKind.BROWSER_SOURCE, 1, "web")(settings).projectionSettings
        assertEquals("web", browser.getBrowserSourceOutput(1).activeProfileId)
        assertEquals(ScreenAssignment().activeProfileId, omt.getNdiOutput(1).activeProfileId, "other kinds untouched")
    }
}
