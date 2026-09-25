@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.settings.LowerThirdPlacement
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.PlaceableContent
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Placement row: one Full screen / In band choice per kind of band-less content a profile shows. */
class ProfilePlacementRowTest {

    private val lowerThird = OutputProfile(displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)

    @Test
    fun `only the content a profile shows is offered a placement`() {
        assertEquals(PlaceableContent.entries, lowerThird.placeableShown())
        assertEquals(
            listOf(PlaceableContent.WEBSITE, PlaceableContent.CANVAS),
            lowerThird.copy(showMedia = false, showPictures = false).placeableShown(),
            "pictures and presentations share one switch, so both go with it",
        )
    }

    @Test
    fun `picking In band puts that content in the band and nothing else`() = runComposeUiTest {
        var profile by mutableStateOf(lowerThird.copy(showPictures = false, showWebsite = false, showCanvas = false))
        setContent { ProfilePlacementRow(profile) { profile = it } }
        onAllNodesWithText("In band")[0].performClick()
        waitForIdle()

        assertEquals(mapOf(PlaceableContent.MEDIA to LowerThirdPlacement.IN_BAND), profile.lowerThirdPlacements)
    }

    @Test
    fun `picking Full screen again puts it back`() = runComposeUiTest {
        var profile by mutableStateOf(
            lowerThird.copy(
                showPictures = false, showWebsite = false, showCanvas = false,
                lowerThirdPlacements = mapOf(PlaceableContent.MEDIA to LowerThirdPlacement.IN_BAND),
            ),
        )
        setContent { ProfilePlacementRow(profile) { profile = it } }
        onAllNodesWithText("Full screen")[0].performClick()
        waitForIdle()

        assertEquals(LowerThirdPlacement.FULL_SCREEN, profile.placementFor(PlaceableContent.MEDIA))
    }
}
