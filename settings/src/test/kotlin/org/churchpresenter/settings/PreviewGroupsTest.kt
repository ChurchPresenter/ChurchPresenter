package org.churchpresenter.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** How the preview panel's groups are edited: shapes, membership, ordering and renumbering. */
class PreviewGroupsTest {

    private val screen0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0)
    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val bs2 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 2)
    private val ndi1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 1)

    private fun settings(vararg groups: PreviewGroup) = ProjectionSettings(previewGroups = groups.toList())

    private fun group(id: String, vararg members: String, shape: PreviewGroupShape = PreviewGroupShape.TWO_BY_TWO) =
        PreviewGroup(id = id, shape = shape, members = members.toList())

    // ── Shapes ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every shape holds as many outputs as it has cells`() {
        val cells = PreviewGroupShape.entries.associateWith { it.capacity }
        assertEquals(1, cells.getValue(PreviewGroupShape.ONE_BY_ONE))
        assertEquals(2, cells.getValue(PreviewGroupShape.ONE_BY_TWO))
        assertEquals(2, cells.getValue(PreviewGroupShape.TWO_BY_ONE))
        assertEquals(3, cells.getValue(PreviewGroupShape.ONE_BY_THREE))
        assertEquals(3, cells.getValue(PreviewGroupShape.THREE_BY_ONE))
        assertEquals(4, cells.getValue(PreviewGroupShape.TWO_BY_TWO))
        assertEquals(4, cells.getValue(PreviewGroupShape.ONE_BY_FOUR))
        assertEquals(4, cells.getValue(PreviewGroupShape.FOUR_BY_ONE))
    }

    @Test
    fun `columns and rows say which way a shape runs`() {
        assertEquals(3 to 1, PreviewGroupShape.THREE_BY_ONE.let { it.columns to it.rows })
        assertEquals(1 to 3, PreviewGroupShape.ONE_BY_THREE.let { it.columns to it.rows })
    }

    @Test
    fun `members past the grid's capacity are not visible`() {
        val g = group("g", bs0, bs1, bs2, screen0, ndi1, shape = PreviewGroupShape.ONE_BY_THREE)
        assertEquals(listOf(bs0, bs1, bs2), g.visibleMembers())
    }

    @Test
    fun `a group with room shows all its members`() {
        assertEquals(listOf(bs0), group("g", bs0).visibleMembers())
    }

    // ── Creating and removing groups ────────────────────────────────────────────────────────────

    @Test
    fun `a new group is empty and takes the next free id`() {
        val first = newPreviewGroup(emptyList())
        assertEquals("group1", first.id)
        assertEquals(emptyList(), first.members)
        assertEquals("group2", newPreviewGroup(listOf(first)).id)
    }

    @Test
    fun `a new group never reuses an id already taken`() {
        val existing = listOf(group("group2"))
        assertEquals("group3", newPreviewGroup(existing).id)
    }

    @Test
    fun `a new group can be given a shape`() {
        assertEquals(PreviewGroupShape.FOUR_BY_ONE, newPreviewGroup(emptyList(), PreviewGroupShape.FOUR_BY_ONE).shape)
    }

    @Test
    fun `adding a group puts it last`() {
        val s = settings(group("a")).addPreviewGroup(group("b"))
        assertEquals(listOf("a", "b"), s.previewGroups.map { it.id })
    }

    @Test
    fun `removing a group leaves the others`() {
        val s = settings(group("a"), group("b")).removePreviewGroup("a")
        assertEquals(listOf("b"), s.previewGroups.map { it.id })
    }

    @Test
    fun `updating a group changes only that group`() {
        val s = settings(group("a"), group("b")).updatePreviewGroup("b") { it.copy(hidden = true) }
        assertEquals(listOf(false, true), s.previewGroups.map { it.hidden })
    }

    // ── Membership ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `adding a member puts it last`() {
        val s = settings(group("a", bs0)).addPreviewMember("a", bs1)
        assertEquals(listOf(bs0, bs1), s.previewGroups.single().members)
    }

    @Test
    fun `an output lives in one group at most`() {
        val s = settings(group("a", bs0, bs1), group("b", screen0)).addPreviewMember("b", bs0)
        assertEquals(listOf(bs1), s.previewGroups[0].members)
        assertEquals(listOf(screen0, bs0), s.previewGroups[1].members)
    }

    @Test
    fun `adding a member it already has moves it to the end without duplicating`() {
        val s = settings(group("a", bs0, bs1)).addPreviewMember("a", bs0)
        assertEquals(listOf(bs1, bs0), s.previewGroups.single().members)
    }

    @Test
    fun `removing a member leaves the rest in order`() {
        val s = settings(group("a", bs0, bs1, bs2)).removePreviewMember("a", bs1)
        assertEquals(listOf(bs0, bs2), s.previewGroups.single().members)
    }

    // ── Ordering ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a member can be moved down a cell`() {
        val s = settings(group("a", bs0, bs1, bs2)).movePreviewMember("a", index = 0, offset = 1)
        assertEquals(listOf(bs1, bs0, bs2), s.previewGroups.single().members)
    }

    @Test
    fun `a member can be moved up a cell`() {
        val s = settings(group("a", bs0, bs1, bs2)).movePreviewMember("a", index = 2, offset = -1)
        assertEquals(listOf(bs0, bs2, bs1), s.previewGroups.single().members)
    }

    @Test
    fun `moving past either end stops at the end`() {
        val members = listOf(bs0, bs1, bs2)
        val up = settings(group("a", *members.toTypedArray())).movePreviewMember("a", 0, -1)
        val down = settings(group("a", *members.toTypedArray())).movePreviewMember("a", 2, 1)
        assertEquals(members, up.previewGroups.single().members)
        assertEquals(members, down.previewGroups.single().members)
    }

    @Test
    fun `moving an index that is not a member changes nothing`() {
        val g = group("a", bs0, bs1)
        assertSame(g, settings(g).movePreviewMember("a", 5, -1).previewGroups.single())
    }

    @Test
    fun `moving in an empty group changes nothing`() {
        val g = group("a")
        assertSame(g, settings(g).movePreviewMember("a", 0, 1).previewGroups.single())
    }

    // ── Keys ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a key parses to its kind and index`() {
        assertEquals(Constants.PREVIEW_OUTPUT_NDI to 1, parsePreviewOutputKey(ndi1))
    }

    @Test
    fun `text that is not a key does not parse`() {
        assertNull(parsePreviewOutputKey("screen"))
        assertNull(parsePreviewOutputKey("screen:x"))
        assertNull(parsePreviewOutputKey(":3"))
    }

    // ── Renumbering when an output is removed ───────────────────────────────────────────────────

    @Test
    fun `removing an output drops its key and renumbers the ones after it`() {
        val s = settings(group("a", bs0, bs1, bs2))
            .shiftPreviewMembers(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, removedIndex = 1)
        assertEquals(listOf(bs0, bs1), s.previewGroups.single().members)
    }

    @Test
    fun `removing an output of one kind leaves the other kinds alone`() {
        val s = settings(group("a", screen0, bs1, ndi1))
            .shiftPreviewMembers(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, removedIndex = 0)
        assertEquals(listOf(screen0, bs0, ndi1), s.previewGroups.single().members)
    }

    @Test
    fun `a member that is not a valid key survives a renumber`() {
        val s = settings(group("a", "junk"))
            .shiftPreviewMembers(Constants.PREVIEW_OUTPUT_NDI, removedIndex = 0)
        assertEquals(listOf("junk"), s.previewGroups.single().members)
    }

    @Test
    fun `removing a Browser Source output renumbers the groups that hold it`() {
        val s = ProjectionSettings(
            browserSourceOutputs = listOf(ScreenAssignment(), ScreenAssignment(), ScreenAssignment()),
            previewGroups = listOf(group("a", bs0, bs2)),
        ).removeBrowserSourceOutput(0)
        assertEquals(2, s.browserSourceOutputs.size)
        assertEquals(listOf(bs1), s.previewGroups.single().members)
    }

    @Test
    fun `removing an NDI output renumbers the groups that hold it`() {
        val s = ProjectionSettings(
            ndiOutputs = listOf(ScreenAssignment(), ScreenAssignment()),
            previewGroups = listOf(group("a", ndi1)),
        ).removeNdiOutput(0)
        assertEquals(1, s.ndiOutputs.size)
        val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)
        assertEquals(listOf(ndi0), s.previewGroups.single().members)
    }

    // ── What is stored ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `settings saved before groups existed load with none and everything shown`() {
        val s = Json { ignoreUnknownKeys = true }.decodeFromString<ProjectionSettings>("{}")
        assertEquals(emptyList(), s.previewGroups)
        assertEquals(true, s.showOutputLabels)
        assertEquals(true, s.showOutputModes)
    }

    @Test
    fun `groups round trip through the settings file`() {
        val original = settings(
            PreviewGroup("g", "Sanctuary", PreviewGroupShape.THREE_BY_ONE, listOf(bs0, screen0), hidden = true),
        ).copy(showOutputLabels = false, showOutputModes = false)
        val json = Json { ignoreUnknownKeys = true }
        assertEquals(original, json.decodeFromString<ProjectionSettings>(json.encodeToString(original)))
    }
}
