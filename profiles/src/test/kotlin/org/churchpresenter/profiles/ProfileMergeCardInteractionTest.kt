@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.MergeKind
import org.churchpresenter.settings.MergeMember
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileMergeCardInteractionTest {

    private val wall = OutputProfile(id = "wall", name = "Sanctuary")

    private fun ndi(profileId: String = "wall") =
        ScreenAssignment(ndiWidth = 1920, ndiHeight = 1080, activeProfileId = profileId)

    private fun ndiTile(index: Int) = OutputTile(OutputKind.NDI, index, "Feed $index", size = null, profileId = "wall")

    private fun screen(x: Int) = ScreenAssignment(
        targetDisplay = 0,
        targetBoundsX = x,
        targetBoundsY = 0,
        targetBoundsW = 1920,
        targetBoundsH = 1080,
        activeProfileId = "wall",
    )

    private val sideBySide = OutputMerge(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0)))

    private fun ComposeUiTest.showCard(
        initial: OutputProfile,
        proj: (OutputProfile) -> ProjectionSettings,
        tiles: List<OutputTile>,
    ): () -> OutputProfile {
        var profile by mutableStateOf(initial)
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileMergeCard(
                    profile = profile,
                    proj = proj(profile),
                    tiles = tiles,
                    onMergeChange = { profile = profile.copy(merge = it) },
                )
            }
        }
        waitForIdle()
        return { profile }
    }

    private fun ndiProj(profile: OutputProfile) =
        ProjectionSettings(ndiOutputs = listOf(ndi(), ndi()), outputProfiles = listOf(profile))

    @Test
    fun `with no outputs following the profile there is nothing to merge`() {
        assertEquals(emptyList(), mergeCandidates(wall, ProjectionSettings(outputProfiles = listOf(wall)), emptyList()))
    }

    @Test
    fun `taking an output out, adding one already in, and adding to an empty merge`() {
        val member = MergeMember("ndi:1", MergeKind.NDI, 1920, 1080)
        val sizes = mapOf("ndi:0" to (1920 to 1080), "ndi:1" to (1920 to 1080))

        assertEquals(listOf("ndi:0"), sideBySide.including(member, on = false, sizes).tiles.map { it.output })
        assertSame(sideBySide, sideBySide.including(member, on = true, sizes))
        assertEquals(listOf(MergeTile("ndi:1", 0, 0)), OutputMerge(emptyList()).including(member, true, sizes).tiles)
    }

    @Test
    fun `dragging a tile on the map moves it`() = runComposeUiTest {
        val profile = showCard(wall.copy(merge = sideBySide), ::ndiProj, listOf(ndiTile(0), ndiTile(1)))

        onNodeWithTag(MERGE_MAP_TAG).performTouchInput {
            down(Offset(width * 0.25f, height / 2f))
            repeat(5) { moveBy(Offset(0f, 8f)) }
            up()
        }
        waitForIdle()

        assertNotEquals(0, profile().merge!!.tiles.first { it.output == "ndi:0" }.y)
    }

    @Test
    fun `typing a tile's place, unticking an output and snapping write the merge`() = runComposeUiTest {
        val profile = showCard(wall.copy(merge = sideBySide), ::ndiProj, listOf(ndiTile(0), ndiTile(1)))

        onAllNodes(hasSetTextAction())[2].performTextReplacement("2000")
        waitForIdle()
        assertEquals(2000, profile().merge!!.tiles.first { it.output == "ndi:1" }.x)

        onAllNodes(hasSetTextAction())[1].performTextReplacement("40")
        waitForIdle()
        assertEquals(40, profile().merge!!.tiles.first { it.output == "ndi:0" }.y)

        onNodeWithText("Snap side by side").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf(0, 1920), profile().merge!!.tiles.map { it.x })

        onAllNodes(isToggleable())[1].performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("ndi:1"), profile().merge!!.tiles.map { it.output })
    }

    @Test
    fun `an output handed to another profile is reported as no longer following`() = runComposeUiTest {
        val merged = wall.copy(merge = sideBySide)
        showCard(
            merged,
            { p -> ProjectionSettings(ndiOutputs = listOf(ndi(), ndi("other")), outputProfiles = listOf(p)) },
            listOf(ndiTile(0), ndiTile(1)),
        )

        assertTrue(
            onAllNodesWithText("An output in the merge no longer follows this profile.")
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun `two displays with a gap between them do not form one rectangle`() = runComposeUiTest {
        val merged = wall.copy(merge = OutputMerge(listOf(MergeTile("screen:0"), MergeTile("screen:1"))))
        showCard(
            merged,
            { p ->
                ProjectionSettings(screenAssignments = listOf(screen(0), screen(2000)), outputProfiles = listOf(p))
            },
            listOf(
                OutputTile(OutputKind.SCREEN, 0, "Left TV", size = null, profileId = "wall"),
                OutputTile(OutputKind.SCREEN, 1, "Right TV", size = null, profileId = "wall"),
            ),
        )

        assertTrue(onAllNodesWithText("These displays don't", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a merge whose outputs have all gone draws no map`() = runComposeUiTest {
        val merged = wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:5"), MergeTile("ndi:6"))))
        var profile by mutableStateOf(merged)
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileMergeCard(
                    profile = profile,
                    proj = ndiProj(profile),
                    tiles = listOf(ndiTile(0), ndiTile(1)),
                    onMergeChange = {},
                    onMac = false,
                    deckLinkSize = { null },
                )
            }
        }
        waitForIdle()

        assertTrue(onAllNodes(hasTestTag(MERGE_MAP_TAG)).fetchSemanticsNodes().isEmpty())
        assertEquals(merged, profile)
    }
}
