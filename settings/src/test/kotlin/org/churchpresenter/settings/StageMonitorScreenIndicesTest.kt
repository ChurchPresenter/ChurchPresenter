package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which screens are stage monitors: what Escape releases a Stage Monitor lock on, and which screens
 * the Announcements tab can send to. Releasing the wrong indices would either leave a stage monitor
 * stuck on stale content or unlock a screen that was never locked.
 */
class StageMonitorScreenIndicesTest {

    // ── stageMonitorScreenIndices ────────────────────────────────────────────────

    /** One profile per [modes] entry, and one screen assignment pointing at each in order. */
    private fun projectionOf(vararg modes: String): ProjectionSettings {
        val profiles = modes.mapIndexed { index, mode -> OutputProfile(id = "p$index", displayMode = mode) }
        return ProjectionSettings(
            outputProfiles = profiles,
            screenAssignments = profiles.map { ScreenAssignment(activeProfileId = it.id) },
        )
    }

    @Test
    fun `no screens configured releases nothing`() {
        val empty = ProjectionSettings(outputProfiles = emptyList(), screenAssignments = emptyList())
        assertEquals(emptyList(), stageMonitorScreenIndices(empty))
    }

    @Test
    fun `no screen in stage-monitor mode releases nothing`() {
        val proj = projectionOf(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)
        assertEquals(emptyList(), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `only the screens actually in stage-monitor mode are returned, by their own index`() {
        val proj = projectionOf(
            Constants.DISPLAY_MODE_FULLSCREEN,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
            Constants.DISPLAY_MODE_LOWER_THIRD_VERTICAL,
            Constants.DISPLAY_MODE_STAGE_MONITOR,
        )
        assertEquals(listOf(1, 3), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `every screen in stage-monitor mode is released`() {
        val proj = projectionOf(*Array(3) { Constants.DISPLAY_MODE_STAGE_MONITOR })
        assertEquals(listOf(0, 1, 2), stageMonitorScreenIndices(proj))
    }

    @Test
    fun `the indices resolve against the real projection settings shape used by MainDesktop`() {
        val settings = AppSettings(
            projectionSettings = projectionOf(
                Constants.DISPLAY_MODE_STAGE_MONITOR,
                Constants.DISPLAY_MODE_FULLSCREEN,
            ),
        )
        assertEquals(listOf(0), stageMonitorScreenIndices(settings.projectionSettings))
    }
}
