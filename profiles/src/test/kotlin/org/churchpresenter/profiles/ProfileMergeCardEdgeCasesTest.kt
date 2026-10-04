@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.MergeKind
import org.churchpresenter.settings.MergeMember
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProfileMergeCardEdgeCasesTest {

    private val wall = OutputProfile(id = "wall", name = "Sanctuary")

    private fun ndi() = ScreenAssignment(ndiWidth = 1920, ndiHeight = 1080, activeProfileId = "wall")

    private fun tile(kind: OutputKind, index: Int, label: String = "$kind $index") =
        OutputTile(kind, index, label, size = null, profileId = "wall")

    @Test
    fun `every kind of output tile has its own key`() {
        assertEquals(Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0), tile(OutputKind.SCREEN, 0).key)
        assertEquals(Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 1), tile(OutputKind.NDI, 1).key)
        assertEquals(Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_OMT, 2), tile(OutputKind.OMT, 2).key)
        assertEquals(
            Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 3),
            tile(OutputKind.BROWSER_SOURCE, 3).key,
        )
    }

    @Test
    fun `a merge naming an output that is no longer here falls back to the most common kind`() {
        val merged = wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:9"))))
        val proj = ProjectionSettings(ndiOutputs = listOf(ndi(), ndi()), outputProfiles = listOf(merged))
        val tiles = listOf(tile(OutputKind.NDI, 0), tile(OutputKind.NDI, 1))

        assertEquals(listOf("ndi:0", "ndi:1"), mergeCandidates(merged, proj, tiles).map { it.output })
    }

    @Test
    fun `an output added beside a tile of unknown size goes at that tile's corner`() {
        val merge = OutputMerge(listOf(MergeTile("ndi:0", 100, 0)))
        val added = merge.including(MergeMember("ndi:1", MergeKind.NDI, 1920, 1080), on = true, sizes = emptyMap())

        assertEquals(MergeTile("ndi:1", 100, 0), added.tiles.last())
    }

    @Test
    fun `a merge left with one output still offers to merge and lists it`() = runComposeUiTest {
        var profile by mutableStateOf(wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:0")))))
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileMergeCard(
                    profile = profile,
                    proj = ProjectionSettings(ndiOutputs = listOf(ndi()), outputProfiles = listOf(profile)),
                    tiles = listOf(tile(OutputKind.NDI, 0, "Only TV")),
                    onMergeChange = { profile = profile.copy(merge = it) },
                    onMac = false,
                    deckLinkSize = { null },
                )
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText("Only TV").fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodesWithText("Outputs of one kind", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a member with no tile label is listed by its key`() = runComposeUiTest {
        val merged = wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:0"), MergeTile("ndi:1", 1920, 0))))
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileMergeCard(
                    profile = merged,
                    proj = ProjectionSettings(ndiOutputs = listOf(ndi(), ndi()), outputProfiles = listOf(merged)),
                    tiles = listOf(tile(OutputKind.NDI, 0), tile(OutputKind.NDI, 1)).map { it.copy(label = "") },
                    onMergeChange = {},
                    onMac = true,
                    deckLinkSize = { null },
                )
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText("Drag a tile", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `a merge of an NDI output and an OMT output says only one kind can be merged`() = runComposeUiTest {
        val profile = wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:0"), MergeTile("omt:0"))))
        val omt = ScreenAssignment(omtWidth = 1920, omtHeight = 1080, activeProfileId = "wall")
        setContent {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProfileMergeCard(
                    profile = profile,
                    proj = ProjectionSettings(
                        ndiOutputs = listOf(ndi()), omtOutputs = listOf(omt), outputProfiles = listOf(profile),
                    ),
                    tiles = listOf(tile(OutputKind.NDI, 0, "Lobby"), tile(OutputKind.OMT, 0, "Stream")),
                    onMergeChange = {},
                    onMac = false,
                    deckLinkSize = { null },
                )
            }
        }
        waitForIdle()

        assertTrue(onAllNodesWithText("Only outputs of one kind can be merged.").fetchSemanticsNodes().isNotEmpty())
    }
}
