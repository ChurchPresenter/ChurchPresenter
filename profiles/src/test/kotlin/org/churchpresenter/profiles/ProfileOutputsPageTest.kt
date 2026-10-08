@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BLANK_OUTPUT_PROFILE_ID
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Profiles → Outputs: every output as a tile, saying which profile it follows; a click gives one to
 * this profile, and a second sets it to Blank. A screen on a monitor marked "Don't use" takes no click.
 * The output's own wiring is never touched.
 */
class ProfileOutputsPageTest {

    private val main = OutputProfile(id = "main", name = "Main")
    private val stage = OutputProfile(id = "stage", name = "Stage")

    @Test
    fun `an output of any kind is moved to a profile and nothing else about it changes`() {
        val proj = ProjectionSettings(
            screenAssignments = listOf(ScreenAssignment(activeProfileId = "main", targetDisplay = 2)),
            ndiOutputs = listOf(ScreenAssignment(activeProfileId = "main", ndiWidth = 1280)),
            omtOutputs = listOf(ScreenAssignment(activeProfileId = "main")),
            browserSourceOutputs = listOf(ScreenAssignment(activeProfileId = "main"), ScreenAssignment()),
        )
        fun tile(kind: OutputKind, index: Int = 0) = OutputTile(kind, index, "x", null, "main")

        val screen = proj.withTileProfile(tile(OutputKind.SCREEN), "stage").screenAssignments.single()
        assertEquals("stage", screen.activeProfileId)
        assertEquals(2, screen.targetDisplay, "its monitor is its own")
        assertEquals(1280, proj.withTileProfile(tile(OutputKind.NDI), "stage").ndiOutputs.single().ndiWidth)
        assertEquals("stage", proj.withTileProfile(tile(OutputKind.NDI), "stage").ndiOutputs.single().activeProfileId)
        assertEquals("stage", proj.withTileProfile(tile(OutputKind.OMT), "stage").omtOutputs.single().activeProfileId)
        val sources = proj.withTileProfile(tile(OutputKind.BROWSER_SOURCE, 1), "stage").browserSourceOutputs
        assertEquals(listOf("main", "stage"), sources.map { it.activeProfileId })
    }

    /** The page's own tile -- the profile rail lists the same output under its profile. */
    private fun ComposeUiTest.screenTile() =
        onAllNodesWithText("Screen 1").let { it[it.fetchSemanticsNodes().size - 1] }

    @Test
    fun `a click gives an output to this profile, and a second sets it to Blank`() = profilesTab(
        AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(main, stage),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "stage")),
            ),
        ),
    ) { get ->
        openProfilePage(ProfilePage.Outputs)
        onNodeWithText("Uses Stage").assertExists()

        screenTile().performScrollTo().performClick()
        waitForIdle()
        assertEquals("main", get().projectionSettings.screenAssignments.single().activeProfileId)
        onNodeWithText("Uses this profile").assertExists()

        screenTile().performScrollTo().performClick()
        waitForIdle()
        assertEquals(BLANK_OUTPUT_PROFILE_ID, get().projectionSettings.screenAssignments.single().activeProfileId)
    }

    private fun ComposeUiTest.screenTileByTag() = onNodeWithTag(outputTileTag(OutputKind.SCREEN, 0))

    @Test
    fun `with one profile a second click sets the output to Blank, and the tile says so`() = profilesTab(
        AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(main),
                screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
            ),
        ),
    ) { get ->
        openProfilePage(ProfilePage.Outputs)
        screenTileByTag().performScrollTo().assert(hasText("Uses this profile"))

        screenTileByTag().performClick()
        waitForIdle()
        assertEquals(BLANK_OUTPUT_PROFILE_ID, get().projectionSettings.screenAssignments.single().activeProfileId)
        screenTileByTag().assert(hasText("Blank"))

        screenTileByTag().performClick()
        waitForIdle()
        assertEquals("main", get().projectionSettings.screenAssignments.single().activeProfileId, "and back again")
        screenTileByTag().assert(hasText("Uses this profile"))
    }

    @Test
    fun `an unused monitor's tile reads Not used, takes no click and is never this profile's`() = profilesTab(
        AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = listOf(main, stage),
                screenAssignments = listOf(
                    ScreenAssignment(
                        activeProfileId = "main",
                        targetDisplay = 1,
                        targetBoundsX = 1920, targetBoundsY = 0, targetBoundsW = 1920, targetBoundsH = 1080,
                    ),
                    ScreenAssignment(activeProfileId = "stage", targetDisplay = 2),
                ),
                unusedScreens = listOf("1920x1080@1920,0"),
            ),
        ),
    ) { get ->
        openProfilePage(ProfilePage.Outputs)
        val tile = screenTileByTag().performScrollTo()
        tile.assert(hasText("Not used"))
        // It follows this profile, but drives no monitor, so it is not shown as this profile's.
        tile.assert(!hasText("Uses this profile"))
        tile.assertIsNotEnabled()
        onNodeWithTag(outputTileTag(OutputKind.SCREEN, 1)).assertIsEnabled()

        tile.performClick()
        waitForIdle()
        assertEquals(listOf("main", "stage"), get().projectionSettings.screenAssignments.map { it.activeProfileId })
    }
}
