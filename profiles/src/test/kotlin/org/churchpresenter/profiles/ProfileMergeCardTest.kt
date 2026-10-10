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
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Profiles → Outputs → Merged picture: which outputs can be merged, switching the merge on and off,
 * and placing each output's tile -- by hand for NDI and the like, by the desktop for real displays.
 */
class ProfileMergeCardTest {

    private val wall = OutputProfile(id = "wall", name = "Sanctuary")

    private fun ndi(w: Int = 1920, h: Int = 1080, follows: String = "wall") =
        ScreenAssignment(ndiWidth = w, ndiHeight = h, activeProfileId = follows)

    private fun display(x: Int, follows: String = "wall") = ScreenAssignment(
        targetDisplay = 1,
        targetBoundsX = x,
        targetBoundsY = 0,
        targetBoundsW = 1920,
        targetBoundsH = 1080,
        activeProfileId = follows,
    )

    private fun tile(kind: OutputKind, index: Int, label: String, follows: String? = "wall") =
        OutputTile(kind, index, label, size = null, profileId = follows)

    private val ndiTiles = listOf(tile(OutputKind.NDI, 0, "Left TV"), tile(OutputKind.NDI, 1, "Right TV"))

    private fun ndiProj(profile: OutputProfile = wall, count: Int = 2) =
        ProjectionSettings(ndiOutputs = List(count) { ndi() }, outputProfiles = listOf(profile))

    /** The card over [initial], changing it as the card asks; [body] sees the latest profile. */
    private fun card(
        initial: OutputProfile,
        proj: (OutputProfile) -> ProjectionSettings = { ndiProj(it) },
        tiles: List<OutputTile> = ndiTiles,
        onMac: Boolean = false,
        body: ComposeUiTest.(latest: () -> OutputProfile) -> Unit,
    ) = runComposeUiTest {
        var profile by mutableStateOf(initial)
        setContent {
            // In a scrolling column, as the settings dialog holds it: rows are found by scrolling to them.
            Column(Modifier.verticalScroll(rememberScrollState())) { ProfileMergeCard(
                profile = profile,
                proj = proj(profile),
                tiles = tiles,
                onMergeChange = { profile = profile.copy(merge = it) },
                onMac = onMac,
                deckLinkSize = { null },
            ) }
        }
        waitForIdle()
        body { profile }
    }

    private fun ComposeUiTest.mergeSwitch() = onAllNodes(isToggleable())[0]

    /** The merge switch, then each output's tick in list order: Left TV, Right TV. */
    private fun ComposeUiTest.rightTvTick() = onAllNodes(isToggleable())[2]

    // ── Who can take part ──────────────────────────────────────────────────────

    @Test
    fun `the candidates are the outputs of the kind this profile has most of`() {
        val tiles = ndiTiles + tile(OutputKind.SCREEN, 0, "Lobby") + tile(OutputKind.NDI, 2, "Other", follows = "else")
        val proj = ProjectionSettings(
            screenAssignments = listOf(display(0)),
            ndiOutputs = listOf(ndi(), ndi(), ndi(follows = "else")),
            outputProfiles = listOf(wall),
        )

        assertEquals(listOf("ndi:0", "ndi:1"), mergeCandidates(wall, proj, tiles).map { it.output })
        assertEquals(emptyList(), mergeCandidates(wall, ProjectionSettings(), emptyList()), "nothing follows it")
    }

    @Test
    fun `once merged, the candidates are of the merge's own kind`() {
        val tiles = ndiTiles + listOf(tile(OutputKind.SCREEN, 0, "A"), tile(OutputKind.SCREEN, 1, "B"),
            tile(OutputKind.SCREEN, 2, "C"))
        val merged = wall.copy(merge = OutputMerge(listOf(MergeTile("ndi:0"), MergeTile("ndi:1"))))
        val proj = ProjectionSettings(
            screenAssignments = listOf(display(0), display(1920), display(3840)),
            ndiOutputs = listOf(ndi(), ndi()),
            outputProfiles = listOf(merged),
        )

        assertEquals(MergeKind.NDI, mergeCandidates(merged, proj, tiles).map { it.kind }.distinct().single())
    }

    @Test
    fun `a merge starts with every candidate side by side`() {
        val started = startedMerge(
            listOf(MergeMember("ndi:0", MergeKind.NDI, 1920, 1080), MergeMember("ndi:1", MergeKind.NDI, 1280, 720)),
        )
        assertEquals(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0)), started.tiles)
    }

    @Test
    fun `an output added goes to the right of everything, and one taken out leaves`() {
        val merge = OutputMerge(listOf(MergeTile("ndi:0", 0, 0)))
        val sizes = mapOf("ndi:0" to (1920 to 1080), "ndi:1" to (1920 to 1080))
        val right = MergeMember("ndi:1", MergeKind.NDI, 1920, 1080)

        val added = merge.including(right, on = true, sizes)
        assertEquals(MergeTile("ndi:1", 1920, 0), added.tiles.last())
        assertEquals(added, added.including(right, on = true, sizes), "already in, nothing changes")
        assertEquals(merge, added.including(right, on = false, sizes))
        val first = OutputMerge().including(right, on = true, sizes).tiles.single()
        assertEquals(MergeTile("ndi:1", 0, 0), first, "the first goes at the corner")
        assertEquals(MergeTile("ndi:0", 5, 7), merge.moved("ndi:0", 5, 7).tiles.single())
    }

    // ── Switching it on and off ────────────────────────────────────────────────

    @Test
    fun `with one output there is nothing to merge, and the switch does nothing`() =
        card(wall, proj = { ndiProj(it, count = 1) }, tiles = ndiTiles.take(1)) { latest ->
            onNodeWithText("Needs two or more outputs of one kind following this profile").assertExists()
            mergeSwitch().performClick()
            waitForIdle()
            assertNull(latest().merge)
        }

    @Test
    fun `switching the merge on places every output side by side, and off takes it away`() = card(wall) { latest ->
        mergeSwitch().performClick()
        waitForIdle()
        assertEquals(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0)), latest().merge?.tiles)
        onNodeWithText("One picture of 3840 × 1080").assertExists()
        onNodeWithText("Drag a tile, or type where it goes", substring = true).assertExists()

        mergeSwitch().performClick()
        waitForIdle()
        assertNull(latest().merge)
    }

    // ── Placing the tiles ──────────────────────────────────────────────────────

    private fun merged(vararg tiles: MergeTile) = wall.copy(merge = OutputMerge(tiles.toList()))

    /** Real displays at these desktop positions, following [profile]. */
    private fun displaysAt(profile: OutputProfile, vararg xs: Int) =
        ProjectionSettings(screenAssignments = xs.map { display(it) }, outputProfiles = listOf(profile))

    private val sideBySide = merged(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0))

    @Test
    fun `a tile's position is typed`() = card(sideBySide) { latest ->
        typeInRow("Right TV", 2000, nth = 0)
        typeInRow("Right TV", 40, nth = 1)
        assertEquals(MergeTile("ndi:1", 2000, 40), latest().merge!!.tiles.last())
    }

    @Test
    fun `an output ticked off leaves the merge, which then says it is too few`() = card(sideBySide) { latest ->
        rightTvTick().performClick()
        waitForIdle()

        assertEquals(listOf("ndi:0"), latest().merge!!.tiles.map { it.output })
        onNodeWithText("Pick at least two outputs.").assertExists()

        rightTvTick().performClick()
        waitForIdle()
        assertEquals(MergeTile("ndi:1", 1920, 0), latest().merge!!.tiles.last(), "and back on the right")
    }

    @Test
    fun `snap puts the tiles back side by side`() =
        card(merged(MergeTile("ndi:0", 300, 90), MergeTile("ndi:1", 0, 600))) { latest ->
            onNodeWithText("Snap side by side").performClick()
            waitForIdle()
            assertEquals(listOf(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0)), latest().merge?.tiles)
        }

    @Test
    fun `an output of another kind is listed as not in the merge`() =
        card(
            sideBySide,
            proj = { ProjectionSettings(screenAssignments = listOf(display(0)), ndiOutputs = listOf(ndi(), ndi()),
                outputProfiles = listOf(it)) },
            tiles = ndiTiles + tile(OutputKind.SCREEN, 0, "Lobby TV"),
        ) {
            onNodeWithText("Not in the merge: a different kind of output").assertExists()
            onNodeWithText("Lobby TV").assertExists()
        }

    @Test
    fun `an output that left the profile is taken out of the merge`() =
        card(
            merged(MergeTile("ndi:0"), MergeTile("ndi:1", 1920), MergeTile("ndi:2", 3840)),
            proj = {
                val outputs = listOf(ndi(), ndi(), ndi(follows = "else"))
                ProjectionSettings(ndiOutputs = outputs, outputProfiles = listOf(it))
            },
        ) { latest ->
            assertEquals(listOf("ndi:0", "ndi:1"), latest().merge!!.tiles.map { it.output })
        }

    // ── Dragging on the map ────────────────────────────────────────────────────

    /** Drags across the map from [from] to [to], each a fraction of its width and height. */
    private fun ComposeUiTest.dragOnMap(from: Offset, to: Offset) {
        onNodeWithTag(MERGE_MAP_TAG).performTouchInput {
            swipe(Offset(width * from.x, height * from.y), Offset(width * to.x, height * to.y), durationMillis = 200)
        }
        waitForIdle()
    }

    @Test
    fun `a tile dragged on the map moves, and the other stays put`() = card(sideBySide) { latest ->
        dragOnMap(Offset(0.75f, 0.5f), Offset(0.95f, 0.8f))

        val (left, right) = latest().merge!!.tiles
        assertEquals(MergeTile("ndi:0", 0, 0), left)
        assertTrue(right.x > 1920, "moved right, to ${right.x}")
        assertTrue(right.y > 0, "and down, to ${right.y}")
    }

    @Test
    fun `real displays cannot be dragged on the map`() =
        card(screens, proj = { displaysAt(it, 0, 1920) }, tiles = screenTiles) { latest ->
            val before = latest().merge
            dragOnMap(Offset(0.75f, 0.5f), Offset(0.95f, 0.8f))
            assertEquals(before, latest().merge)
        }

    // ── Real displays ──────────────────────────────────────────────────────────

    private val screenTiles = listOf(tile(OutputKind.SCREEN, 0, "Left"), tile(OutputKind.SCREEN, 1, "Right"))
    private val screens = wall.copy(merge = OutputMerge(listOf(MergeTile("screen:0"), MergeTile("screen:1"))))

    @Test
    fun `real displays are placed by the desktop, with no fields to type and on a Mac a note`() =
        card(
            screens,
            proj = { displaysAt(it, 0, 1920) },
            tiles = screenTiles,
            onMac = true,
        ) {
            onNodeWithText("Displays have separate Spaces", substring = true).assertExists()
            assertTrue(onAllNodesWithText("X").fetchSemanticsNodes().isEmpty(), "nowhere to type a position")
            assertTrue(onAllNodesWithText("Snap side by side").fetchSemanticsNodes().isEmpty())
        }

    @Test
    fun `real displays with a gap between them do not make one picture`() =
        card(
            screens,
            proj = { displaysAt(it, 0, 4000) },
            tiles = screenTiles,
        ) {
            onNodeWithText("These displays don't form one rectangle", substring = true).assertExists()
            val macNote = onAllNodesWithText("Displays have separate Spaces", substring = true)
            assertTrue(macNote.fetchSemanticsNodes().isEmpty(), "off a Mac there is no Spaces note")
        }

    @Test
    fun `a drag that starts on no tile moves nothing`() = card(
        merged(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 1920, 0)),
        proj = { ProjectionSettings(ndiOutputs = listOf(ndi(), ndi(w = 960, h = 540)), outputProfiles = listOf(it)) },
    ) { latest ->
        val before = latest().merge
        dragOnMap(Offset(0.85f, 0.8f), Offset(0.95f, 0.95f))
        assertEquals(before, latest().merge)
    }

    @Test
    fun `where two tiles overlap, the one drawn last is the one dragged`() = card(
        merged(MergeTile("ndi:0", 0, 0), MergeTile("ndi:1", 960, 0)),
    ) { latest ->
        dragOnMap(Offset(0.5f, 0.5f), Offset(0.7f, 0.5f))
        val (under, over) = latest().merge!!.tiles
        assertEquals(MergeTile("ndi:0", 0, 0), under)
        assertTrue(over.x > 960, "the top tile moved, to ${over.x}")
    }
}
