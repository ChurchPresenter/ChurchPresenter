package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A profile's merged picture (#721): who takes part, how it is laid out, and when it cannot be drawn. */
class OutputMergeTest {

    private val profile = OutputProfile(id = "wall", name = "Sanctuary")

    private fun dev(w: Int = 1920, h: Int = 1080, follows: String = "wall") =
        ScreenAssignment(
            targetDisplay = Constants.KEY_TARGET_NONE,
            devWindowWidth = w,
            devWindowHeight = h,
            activeProfileId = follows,
        )

    private fun display(x: Int, y: Int = 0, follows: String = "wall") = ScreenAssignment(
        targetDisplay = 1,
        targetBoundsX = x,
        targetBoundsY = y,
        targetBoundsW = 1920,
        targetBoundsH = 1080,
        activeProfileId = follows,
    )

    private fun ndi(w: Int = 1920, h: Int = 1080, follows: String = "wall") =
        ScreenAssignment(ndiWidth = w, ndiHeight = h, activeProfileId = follows)

    private fun merged(vararg tiles: MergeTile) = profile.copy(merge = OutputMerge(tiles.toList()))

    private fun proj(
        profile: OutputProfile,
        screens: List<ScreenAssignment> = emptyList(),
        ndi: List<ScreenAssignment> = emptyList(),
    ) = ProjectionSettings(screenAssignments = screens, ndiOutputs = ndi, outputProfiles = listOf(profile))

    @Test
    fun `two dev windows side by side make one 3840 by 1080 picture, each showing its half`() {
        val wall = merged(MergeTile("screen:0", 0, 0), MergeTile("screen:1", 1920, 0))
        val (merge, problem) = proj(wall, screens = listOf(dev(), dev())).resolveMerge(wall)

        assertNull(problem)
        merge!!
        assertEquals(MergeKind.DEV_WINDOW, merge.kind)
        assertEquals(3840 to 1080, merge.width to merge.height)
        assertEquals(DisplayRect(0, 0, 1920, 1080), merge.tiles["screen:0"])
        assertEquals(DisplayRect(1920, 0, 1920, 1080), merge.tiles["screen:1"])
        assertEquals("screen:0", merge.host)
        assertNull(merge.desktop, "only real displays have a place on the desktop")
    }

    @Test
    fun `positions are taken relative to the picture's corner, with gaps and overlaps allowed`() {
        val wall = merged(MergeTile("ndi:0", 100, 50), MergeTile("ndi:1", 1900, 50))
        val (merge, _) = proj(wall, ndi = listOf(ndi(), ndi())).resolveMerge(wall)

        assertEquals(DisplayRect(0, 0, 1920, 1080), merge!!.tiles["ndi:0"])
        assertEquals(DisplayRect(1800, 0, 1920, 1080), merge.tiles["ndi:1"])
        assertEquals(3720, merge.width)
    }

    @Test
    fun `real displays are placed where the desktop has them, whatever the stored positions say`() {
        val wall = merged(MergeTile("screen:0", 999, 999), MergeTile("screen:1", 0, 0))
        val (merge, problem) = proj(wall, screens = listOf(display(1920), display(3840))).resolveMerge(wall)

        assertNull(problem)
        assertEquals(MergeKind.REAL_DISPLAY, merge!!.kind)
        assertEquals(DisplayRect(1920, 0, 3840, 1080), merge.desktop)
        assertEquals(DisplayRect(1920, 0, 1920, 1080), merge.tiles["screen:1"])
    }

    @Test
    fun `real displays that do not tile a rectangle cannot be merged`() {
        val wall = merged(MergeTile("screen:0"), MergeTile("screen:1"))
        val (_, problem) = proj(wall, screens = listOf(display(1920), display(3840, y = 1080))).resolveMerge(wall)
        assertEquals(MergeProblem.NOT_A_RECTANGLE, problem)
    }

    @Test
    fun `outputs of different kinds cannot be merged`() {
        val wall = merged(MergeTile("screen:0"), MergeTile("ndi:0"))
        val (_, problem) = proj(wall, screens = listOf(dev()), ndi = listOf(ndi())).resolveMerge(wall)
        assertEquals(MergeProblem.MIXED_KINDS, problem)
    }

    @Test
    fun `an output that stopped following the profile, or is gone, breaks the merge`() {
        val wall = merged(MergeTile("screen:0"), MergeTile("screen:1"))
        assertEquals(
            MergeProblem.NOT_FOLLOWING,
            proj(wall, screens = listOf(dev(), dev(follows = "other"))).resolveMerge(wall).second,
        )
        assertEquals(MergeProblem.NOT_FOLLOWING, proj(wall, screens = listOf(dev())).resolveMerge(wall).second)
    }

    @Test
    fun `one output is nothing to merge, and no merge is no problem`() {
        val single = merged(MergeTile("screen:0"), MergeTile("screen:0"))
        assertEquals(MergeProblem.TOO_FEW, proj(single, screens = listOf(dev())).resolveMerge(single).second)
        assertEquals(null to null, proj(profile, screens = listOf(dev())).resolveMerge(profile))
    }

    @Test
    fun `a screen slot is a DeckLink device when it names one, sized by the device`() {
        val deck = ScreenAssignment(
            targetType = Constants.TARGET_TYPE_DECKLINK,
            targetDisplay = 2,
            activeProfileId = "wall",
        )
        val p = proj(profile, screens = listOf(deck))

        assertEquals(
            MergeMember("screen:0", MergeKind.DECKLINK, 3840, 2160),
            p.mergeMember("screen:0") { 3840 to 2160 },
        )
        assertEquals(1920, p.mergeMember("screen:0")!!.width, "an unknown mode is 1080p")
    }

    @Test
    fun `every kind of output has its own size`() {
        val p = ProjectionSettings(
            omtOutputs = listOf(ScreenAssignment(omtWidth = 1280, omtHeight = 720)),
            browserSourceOutputs = listOf(ScreenAssignment(browserSourceWidth = 800, browserSourceHeight = 600)),
        )
        assertEquals(MergeMember("omt:0", MergeKind.OMT, 1280, 720), p.mergeMember("omt:0"))
        assertEquals(
            MergeMember("browserSource:0", MergeKind.BROWSER_SOURCE, 800, 600),
            p.mergeMember("browserSource:0"),
        )
        assertNull(p.mergeMember("ndi:0"), "no such output")
        assertNull(p.mergeMember("nonsense"))
    }

    @Test
    fun `output keys read back as their list and index`() {
        assertEquals("ndi" to 3, parseOutputKey("ndi:3"))
        assertNull(parseOutputKey("ndi"))
        assertNull(parseOutputKey(":1"))
        assertNull(parseOutputKey("ndi:-1"))
        assertNull(ProjectionSettings().assignmentFor("elsewhere:0"))
    }

    @Test
    fun `every drawable merge is found by each of its outputs`() {
        val wall = merged(MergeTile("screen:0", 0, 0), MergeTile("screen:1", 1920, 0))
        val broken = OutputProfile(id = "b", merge = OutputMerge(listOf(MergeTile("ndi:0"))))
        val p = ProjectionSettings(screenAssignments = listOf(dev(), dev()), outputProfiles = listOf(wall, broken))

        val merges = p.resolvedMerges()
        assertEquals(setOf("screen:0", "screen:1"), merges.keys)
        assertTrue(merges.values.all { it.profileId == "wall" })
    }

    @Test
    fun `snap side by side lines the tiles up left to right`() {
        val sizes = mapOf("a" to 1920, "b" to 1280)
        val tiles = listOf(MergeTile("a", 5, 5), MergeTile("b", 0, 900), MergeTile("c", 7, 7))
        val snapped = sideBySide(tiles) { sizes[it] }
        assertEquals(listOf(MergeTile("a", 0, 0), MergeTile("b", 1920, 0), MergeTile("c", 7, 7)), snapped)
    }

    @Test
    fun `a linked profile keeps its own merge and never follows its master's`() {
        val master = OutputProfile(id = "m", merge = OutputMerge(listOf(MergeTile("screen:0"), MergeTile("screen:1"))))
        val own = OutputMerge(listOf(MergeTile("ndi:0"), MergeTile("ndi:1")))
        val child = OutputProfile(id = "c", parentId = "m", merge = own)
        val alone = OutputProfile(id = "d", parentId = "m")

        assertEquals(own, materialize(master, child).merge)
        assertNull(materialize(master, alone).merge, "a master's merge is not handed down")
        assertTrue(master.settingPaths().keys.none { it.startsWith("merge") }, "never a followed or reverted value")
    }

    @Test
    fun `a duplicated profile starts without a merge, since no output follows it yet`() {
        val wall = merged(MergeTile("screen:0"), MergeTile("screen:1"))
        val copied = ProjectionSettings(outputProfiles = listOf(wall)).duplicateOutputProfile("wall", "Copy")
        assertNull(copied.outputProfiles.last().merge)
        assertEquals(wall.merge, copied.outputProfiles.first().merge)
    }

    @Test
    fun `an output is overridden by the profile whose merge it is in, and only that one`() {
        val wall = merged(MergeTile("ndi:0"), MergeTile("ndi:1"))
        val profiles = listOf(OutputProfile(id = "other"), wall)
        assertEquals(wall, profiles.mergingProfileOf("ndi:1", "wall"))
        assertNull(profiles.mergingProfileOf("ndi:2", "wall"), "not in the merge")
        assertNull(profiles.mergingProfileOf("ndi:1", "other"), "follows a profile that merges nothing")
    }
}
