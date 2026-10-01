package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The preview panel's layouts: the area tree and every edit made to it, and groups becoming one. */
class PreviewLayoutsTest {

    private val screen0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_SCREEN, 0)
    private val bs0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 0)
    private val bs1 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_BROWSER_SOURCE, 1)
    private val ndi0 = Constants.previewOutputKey(Constants.PREVIEW_OUTPUT_NDI, 0)

    private val pair = splitArea(SPLIT_ACROSS, listOf(PreviewArea(output = screen0), PreviewArea(output = bs0)))

    // ── The tree ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an area with no children is a leaf, and a split of several is not`() {
        assertTrue(PreviewArea().isLeaf)
        assertTrue(PreviewArea(split = SPLIT_DOWN).isLeaf, "a split with nothing in it draws as a leaf")
        assertFalse(pair.isLeaf)
    }

    @Test
    fun `a split shares its room equally`() {
        val three = splitArea(SPLIT_DOWN, List(3) { PreviewArea() })
        assertEquals(List(3) { 1f / 3 }, three.ratios)
    }

    @Test
    fun `outputs lists every output shown, in reading order, leaving out empty areas`() {
        val tree = splitArea(SPLIT_DOWN, listOf(pair, PreviewArea(), PreviewArea(output = ndi0)))
        assertEquals(listOf(screen0, bs0, ndi0), tree.outputs())
    }

    @Test
    fun `at follows a path of child indices`() {
        assertEquals(pair, pair.at(emptyList()))
        assertEquals(bs0, pair.at(listOf(1))?.output)
        assertNull(pair.at(listOf(5)))
    }

    @Test
    fun `updatedAt changes the one area a path names`() {
        val placed = pair.updatedAt(listOf(1)) { it.copy(place = Constants.TOP) }
        assertEquals(Constants.TOP, placed.at(listOf(1))?.place)
        assertEquals(Constants.MIDDLE, placed.at(listOf(0))?.place)
        assertEquals(pair, pair.updatedAt(listOf(9)) { it.copy(output = "x") }, "a path to nowhere changes nothing")
    }

    // ── Editing ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `splitting a leaf keeps it and adds an empty area beside it`() {
        val split = PreviewArea(output = screen0, place = Constants.BOTTOM).splitAt(emptyList(), SPLIT_DOWN)
        assertEquals(SPLIT_DOWN, split.split)
        assertEquals(screen0, split.children[0].output)
        assertEquals("", split.children[1].output)
        assertEquals(Constants.BOTTOM, split.children[1].place, "the new area takes its neighbour's place")
        assertEquals(listOf(0.5f, 0.5f), split.ratios)
    }

    @Test
    fun `removing an area gives its room to the rest, and a split of one becomes that one`() {
        val three = splitArea(
            SPLIT_ACROSS,
            listOf(PreviewArea(output = screen0), PreviewArea(output = bs0), PreviewArea(output = bs1)),
        )
        val two = three.removedAt(listOf(1))
        assertEquals(listOf(screen0, bs1), two.outputs())
        assertEquals(1f, two.ratios.sum(), 0.0001f)
        val one = two.removedAt(listOf(0))
        assertTrue(one.isLeaf)
        assertEquals(bs1, one.output)
    }

    @Test
    fun `removing the whole layout empties it rather than leaving nothing`() {
        val emptied = pair.removedAt(emptyList())
        assertTrue(emptied.isLeaf)
        assertEquals("", emptied.output)
    }

    @Test
    fun `a divider moves the share between the two areas either side of it`() {
        val moved = pair.withDividerAt(emptyList(), 0, 0.75f)
        assertEquals(0.75f, moved.ratios[0], 0.0001f)
        assertEquals(0.25f, moved.ratios[1], 0.0001f)
    }

    @Test
    fun `a divider cannot squeeze an area to nothing`() {
        val squeezed = pair.withDividerAt(emptyList(), 0, 0f)
        assertEquals(MIN_AREA_SHARE, squeezed.ratios[0], 0.0001f)
        assertEquals(pair, pair.withDividerAt(emptyList(), 1, 0.5f), "there is no divider after the last area")
    }

    @Test
    fun `an output placed in one area is taken out of any other`() {
        val cleared = pair.without(bs0)
        assertEquals(listOf(screen0), cleared.outputs())
    }

    // ── Layouts on the projection settings ──────────────────────────────────────────────────────

    @Test
    fun `a new layout takes an id nothing else uses`() {
        val first = newPreviewLayout(emptyList(), name = "")
        assertEquals("layout1", first.id)
        val second = newPreviewLayout(listOf(first, PreviewLayout(id = "layout2")), name = "Sunday")
        assertEquals("layout3", second.id)
        assertEquals("Sunday", second.name)
    }

    @Test
    fun `the active layout is the chosen one, or the first, or none`() {
        val a = PreviewLayout(id = "a")
        val b = PreviewLayout(id = "b")
        assertNull(ProjectionSettings().activeLayout())
        assertEquals(a, ProjectionSettings(previewLayouts = listOf(a, b)).activeLayout())
        assertEquals(b, ProjectionSettings(previewLayouts = listOf(a, b), activePreviewLayout = "b").activeLayout())
        assertEquals(a, ProjectionSettings(previewLayouts = listOf(a, b), activePreviewLayout = "gone").activeLayout())
    }

    @Test
    fun `updateLayout changes only the layout named`() {
        val proj = ProjectionSettings(previewLayouts = listOf(PreviewLayout(id = "a"), PreviewLayout(id = "b")))
        val renamed = proj.updateLayout("b") { it.copy(name = "Youth") }
        assertEquals(listOf("", "Youth"), renamed.previewLayouts.map { it.name })
    }

    // ── Groups becoming a layout ────────────────────────────────────────────────────────────────

    @Test
    fun `no groups is no layout, so every output is still listed`() {
        assertNull(layoutFromGroups(emptyList(), name = ""))
    }

    @Test
    fun `groups that are all hidden become one empty area, as they drew nothing`() {
        val layout = layoutFromGroups(listOf(PreviewGroup(id = "g", members = listOf(bs0), hidden = true)), name = "")
        assertTrue(layout?.root?.isLeaf == true)
        assertEquals(emptyList(), layout.root.outputs())
    }

    @Test
    fun `a group becomes its grid's rows, a short row keeping its empty cells`() {
        val group = PreviewGroup(id = "g", shape = PreviewGroupShape.TWO_BY_TWO, members = listOf(screen0, bs0, bs1))
        val root = layoutFromGroups(listOf(group), name = "")?.root
        assertEquals(SPLIT_DOWN, root?.split)
        assertEquals(2, root?.children?.size)
        val lastRow = root?.children?.get(1)
        assertEquals(SPLIT_ACROSS, lastRow?.split)
        assertEquals(listOf(bs1, ""), lastRow?.children?.map { it.output })
    }

    @Test
    fun `shown groups stack one under another, hidden ones and members past a grid left out`() {
        val one = PreviewGroup(id = "a", shape = PreviewGroupShape.ONE_BY_ONE, members = listOf(screen0, bs1))
        val hidden = PreviewGroup(id = "b", members = listOf(ndi0), hidden = true)
        val two = PreviewGroup(id = "c", shape = PreviewGroupShape.TWO_BY_ONE, members = listOf(bs0))
        val root = layoutFromGroups(listOf(one, hidden, two), name = "")?.root
        assertEquals(SPLIT_DOWN, root?.split)
        assertEquals(listOf(screen0, bs0), root?.outputs())
    }
}
