package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.OutputScaleMode
import org.churchpresenter.settings.ProjectionSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Pictures and Media tabs' scale button over per-profile scaling: what it shows, and that one
 * press moves every profile -- and the document's own copy, which the tabs' previews still draw by.
 */
class OutputScaleModeEverywhereTest {

    private fun doc(vararg modes: OutputScaleMode) = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = modes.mapIndexed { i, mode ->
                OutputProfile(id = "p$i", pictureScaleMode = mode, mediaScaleMode = mode)
            },
        ),
    )

    @Test
    fun `profiles that agree share their mode`() {
        val profiles = doc(OutputScaleMode.FILL, OutputScaleMode.FILL).projectionSettings.outputProfiles
        assertEquals(OutputScaleMode.FILL, sharedScaleMode(profiles) { it.pictureScaleMode })
    }

    @Test
    fun `profiles that differ share nothing`() {
        val profiles = doc(OutputScaleMode.FIT, OutputScaleMode.FILL).projectionSettings.outputProfiles
        assertNull(sharedScaleMode(profiles) { it.mediaScaleMode })
    }

    @Test
    fun `setting pictures everywhere reaches every profile and the document`() {
        val after = doc(OutputScaleMode.FIT, OutputScaleMode.FILL).withPictureScaleEverywhere(OutputScaleMode.STRETCH)
        assertEquals(OutputScaleMode.STRETCH, after.pictureSettings.scaleMode)
        assertEquals(
            listOf(OutputScaleMode.STRETCH, OutputScaleMode.STRETCH),
            after.projectionSettings.outputProfiles.map { it.pictureScaleMode },
        )
        assertEquals(
            listOf(OutputScaleMode.FIT, OutputScaleMode.FILL),
            after.projectionSettings.outputProfiles.map { it.mediaScaleMode },
            "and leaves video alone",
        )
    }

    @Test
    fun `setting media everywhere reaches every profile and the document`() {
        val after = doc(OutputScaleMode.FIT, OutputScaleMode.FILL).withMediaScaleEverywhere(OutputScaleMode.FILL)
        assertEquals(OutputScaleMode.FILL, after.mediaScaleMode)
        assertEquals(
            listOf(OutputScaleMode.FILL, OutputScaleMode.FILL),
            after.projectionSettings.outputProfiles.map { it.mediaScaleMode },
        )
    }
}
