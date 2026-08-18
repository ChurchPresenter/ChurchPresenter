package lottiegen

import lottiegen.editor.NewElementKind
import lottiegen.editor.addElement
import lottiegen.editor.moveElement
import lottiegen.editor.removeElement
import lottiegen.editor.replaceElement
import lottiegen.editor.uniqueElementId
import lottiegen.editor.withCommon
import lottiegen.spec.EllipseElement
import lottiegen.spec.ElementSpec
import lottiegen.spec.Placement
import lottiegen.spec.RectElement
import lottiegen.spec.StyleSpec
import lottiegen.spec.TextElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The editor's pure document edits — everything the lower-third designer does to a spec that does
 * not involve drawing it.
 *
 * Two properties matter throughout. Element order **is** z-order (first = topmost), so a reorder
 * that silently no-ops or overshoots puts a layer behind the background. And every edit returns a
 * new spec rather than mutating: the editor's undo stack is a list of previous specs, so an edit
 * that mutated in place would corrupt every earlier entry at once.
 */
class SpecEditOpsTest {

    private fun spec(vararg ids: String) = StyleSpec(
        elements = ids.map { RectElement(id = it, name = "Element $it") }
    )

    // ── withCommon ────────────────────────────────────────────────────────────

    @Test
    fun `withCommon renames without changing the element's kind`() {
        val renamed = RectElement(id = "r1").withCommon(name = "Banner")
        assertIs<RectElement>(renamed, "a rect stays a rect")
        assertEquals("Banner", renamed.name)
        assertEquals("r1", renamed.id, "identity is preserved")
    }

    @Test
    fun `withCommon works across every element kind`() {
        val kinds: List<ElementSpec> = NewElementKind.entries.map { kind ->
            StyleSpec().addElement(kind).elements.first()
        }
        for (element in kinds) {
            val updated = element.withCommon(name = "Renamed")
            assertEquals("Renamed", updated.name, "${element::class.simpleName} supports rename")
            assertEquals(element::class, updated::class, "${element::class.simpleName} keeps its type")
            assertEquals(element.id, updated.id)
        }
    }

    @Test
    fun `withCommon leaves untouched fields alone`() {
        val original = RectElement(id = "r1", name = "Keep", placement = Placement(slot = "text"))
        val updated = original.withCommon(name = "New")
        assertEquals("text", updated.placement.slot, "placement survives a rename")
    }

    // ── replace and remove ────────────────────────────────────────────────────

    @Test
    fun `replaceElement transforms only the named element`() {
        val updated = spec("a", "b", "c").replaceElement("b") { it.withCommon(name = "Changed") }
        assertEquals(listOf("Element a", "Changed", "Element c"), updated.elements.map { it.name })
    }

    @Test
    fun `replacing an id that is not present leaves the document alone`() {
        val original = spec("a", "b")
        assertEquals(original, original.replaceElement("nope") { it.withCommon(name = "X") })
    }

    @Test
    fun `removeElement drops just that element`() {
        assertEquals(listOf("a", "c"), spec("a", "b", "c").removeElement("b").elements.map { it.id })
    }

    @Test
    fun `removing an absent id is a no-op`() {
        val original = spec("a", "b")
        assertEquals(original.elements.size, original.removeElement("nope").elements.size)
    }

    @Test
    fun `an edit returns a new document and leaves the original intact`() {
        // The undo stack holds previous specs, so in-place mutation would rewrite history.
        val original = spec("a", "b")
        original.removeElement("a")
        original.replaceElement("b") { it.withCommon(name = "X") }
        assertEquals(listOf("a", "b"), original.elements.map { it.id }, "the original is unchanged")
    }

    // ── Reordering ────────────────────────────────────────────────────────────

    @Test
    fun `moveElement shifts one place toward the back`() {
        assertEquals(listOf("b", "a", "c"), spec("a", "b", "c").moveElement("a", 1).elements.map { it.id })
    }

    @Test
    fun `moveElement shifts one place toward the front`() {
        assertEquals(listOf("a", "c", "b"), spec("a", "b", "c").moveElement("c", -1).elements.map { it.id })
    }

    @Test
    fun `moving past either end is refused rather than clamped or wrapped`() {
        val original = spec("a", "b", "c")
        assertEquals(original, original.moveElement("a", -1), "already topmost")
        assertEquals(original, original.moveElement("c", 1), "already bottom")
    }

    @Test
    fun `moving an element that is not present is a no-op`() {
        val original = spec("a", "b")
        assertEquals(original, original.moveElement("nope", 1))
    }

    @Test
    fun `a move preserves every element`() {
        val moved = spec("a", "b", "c", "d").moveElement("b", 1)
        assertEquals(setOf("a", "b", "c", "d"), moved.elements.map { it.id }.toSet(), "nothing is lost")
        assertEquals(4, moved.elements.size, "and nothing is duplicated")
    }

    // ── Id allocation ─────────────────────────────────────────────────────────

    @Test
    fun `the first id of a prefix is numbered one`() {
        assertEquals("rect1", StyleSpec().uniqueElementId("rect"))
    }

    @Test
    fun `an id already taken is skipped`() {
        val spec = StyleSpec(elements = listOf(RectElement(id = "rect1"), RectElement(id = "rect2")))
        assertEquals("rect3", spec.uniqueElementId("rect"))
    }

    @Test
    fun `a gap in the numbering is filled`() {
        val spec = StyleSpec(elements = listOf(RectElement(id = "rect1"), RectElement(id = "rect3")))
        assertEquals("rect2", spec.uniqueElementId("rect"))
    }

    @Test
    fun `prefixes are independent of each other`() {
        val spec = StyleSpec(elements = listOf(RectElement(id = "rect1")))
        assertEquals("ellipse1", spec.uniqueElementId("ellipse"))
    }

    // ── Adding ────────────────────────────────────────────────────────────────

    @Test
    fun `a new element lands on top so it is visible immediately`() {
        val updated = spec("existing").addElement(NewElementKind.RECT)
        assertEquals(2, updated.elements.size)
        assertEquals("rect1", updated.elements.first().id, "first in the list is the topmost layer")
    }

    @Test
    fun `every kind can be added and gets a distinct id`() {
        var spec = StyleSpec()
        for (kind in NewElementKind.entries) spec = spec.addElement(kind)
        assertEquals(NewElementKind.entries.size, spec.elements.size)
        assertEquals(spec.elements.size, spec.elements.map { it.id }.distinct().size, "ids are unique")
    }

    @Test
    fun `adding the same kind twice yields two differently numbered elements`() {
        val spec = StyleSpec().addElement(NewElementKind.RECT).addElement(NewElementKind.RECT)
        assertEquals(setOf("rect1", "rect2"), spec.elements.map { it.id }.toSet())
    }

    @Test
    fun `each kind produces its own element type`() {
        assertIs<RectElement>(StyleSpec().addElement(NewElementKind.RECT).elements.first())
        assertIs<EllipseElement>(StyleSpec().addElement(NewElementKind.ELLIPSE).elements.first())
        assertIs<TextElement>(StyleSpec().addElement(NewElementKind.NAME_TEXT).elements.first())
    }

    @Test
    fun `a new path arrives with a draw-on track so it animates out of the box`() {
        val path = StyleSpec().addElement(NewElementKind.PATH).elements.first()
        assertTrue(path.tracks.isNotEmpty(), "a fresh path demonstrates growth immediately")
    }

    @Test
    fun `the two text kinds bind to different fields and lines`() {
        val name = StyleSpec().addElement(NewElementKind.NAME_TEXT).elements.first() as TextElement
        val info = StyleSpec().addElement(NewElementKind.INFO_TEXT).elements.first() as TextElement
        assertTrue(name.field != info.field, "one shows the name, the other the info line")
        assertTrue(name.placement.line != info.placement.line, "and they sit on different lines")
        assertTrue(name.visibleWhen.isNotEmpty(), "each is hidden when its field is empty")
        assertTrue(info.visibleWhen.isNotEmpty())
    }
}
