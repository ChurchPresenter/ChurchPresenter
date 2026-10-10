package org.churchpresenter.app.churchpresenter

import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.liveoutput.settings.DetectedScreen
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.screenKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class HelperScreensTest {

    private val laptop =
        DetectedScreen(0, isPrimary = true, boundsX = 0, boundsY = 0, boundsW = 1440, boundsH = 900)
    private val projector =
        DetectedScreen(1, isPrimary = false, boundsX = 1440, boundsY = 0, boundsW = 1920, boundsH = 1080)

    private fun settings(vararg assignments: ScreenAssignment, names: Map<String, String> = emptyMap()) =
        AppSettings(
            projectionSettings = ProjectionSettings(screenAssignments = assignments.toList(), screenNames = names),
        )

    private fun onProjector(type: String = Constants.TARGET_TYPE_SCREEN) = ScreenAssignment(
        targetType = type,
        targetBoundsX = projector.boundsX,
        targetBoundsY = projector.boundsY,
        targetBoundsW = projector.boundsW,
        targetBoundsH = projector.boundsH,
    )

    @Test
    fun `each detected screen is reported with its bounds and the name the operator gave it`() {
        val names = mapOf(screenKey(1440, 0, 1920, 1080) to "Projector")
        assertEquals(
            listOf(
                HelperScreen(0, isPrimary = true, x = 0, y = 0, width = 1440, height = 900),
                HelperScreen(1, isPrimary = false, x = 1440, y = 0, width = 1920, height = 1080, name = "Projector"),
            ),
            helperScreens(settings(names = names), listOf(laptop, projector)),
        )
    }

    @Test
    fun `the screen output 1 drives is marked as the audience`() {
        val screens = helperScreens(settings(onProjector(), ScreenAssignment()), listOf(laptop, projector))
        assertEquals(listOf(false, true), screens.map { it.isAudience })
    }

    @Test
    fun `an output 1 sent to a DeckLink device marks no screen as the audience`() {
        val screens = helperScreens(settings(onProjector(type = "decklink")), listOf(laptop, projector))
        assertEquals(listOf(false, false), screens.map { it.isAudience })
    }

    @Test
    fun `with no displays found there are no screens`() {
        assertEquals(emptyList(), helperScreens(settings(onProjector()), emptyList()))
    }
}
