package org.churchpresenter.lottiegen.editor

import org.churchpresenter.lottiegen.spec.AnchorIn
import org.churchpresenter.lottiegen.spec.AnimTrack
import org.churchpresenter.lottiegen.spec.BackgroundElement
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.spec.EllipseElement
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.CurveVertex
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.LineAnchor
import org.churchpresenter.lottiegen.spec.LogoElement
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.Placement
import org.churchpresenter.lottiegen.spec.SpecKeyframe
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.SpecLayoutContext
import org.churchpresenter.lottiegen.spec.StyleSpec
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.VisibilityRule

/** Pure spec-document edit operations shared by the editor UI. */

/** Copies an element with the fields every kind shares — the sealed-type-safe rename/rewire helper. */
fun ElementSpec.withCommon(
    name: String = this.name,
    visibleWhen: List<VisibilityRule> = this.visibleWhen,
    placement: Placement = this.placement,
    tracks: List<AnimTrack> = this.tracks
): ElementSpec = when (this) {
    is RectElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is EllipseElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is PolygonElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is PathElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is TextElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is LogoElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is ImageElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
    is BackgroundElement -> copy(name = name, visibleWhen = visibleWhen, placement = placement, tracks = tracks)
}

fun StyleSpec.replaceElement(id: String, transform: (ElementSpec) -> ElementSpec): StyleSpec =
    copy(elements = elements.map { if (it.id == id) transform(it) else it })

fun StyleSpec.removeElement(id: String): StyleSpec =
    copy(elements = elements.filterNot { it.id == id })

/** Moves the element one step toward the top (delta = -1) or bottom (delta = +1) of the layer stack. */
fun StyleSpec.moveElement(id: String, delta: Int): StyleSpec {
    val index = elements.indexOfFirst { it.id == id }
    val target = index + delta
    if (index < 0 || target < 0 || target >= elements.size) return this
    val reordered = elements.toMutableList()
    val element = reordered.removeAt(index)
    reordered.add(target, element)
    return copy(elements = reordered)
}

fun StyleSpec.uniqueElementId(prefix: String): String {
    val existing = elements.map { it.id }.toSet()
    var i = 1
    while ("$prefix$i" in existing) i++
    return "$prefix$i"
}

/** The kinds offered by the "Add element" menu. */
enum class NewElementKind { RECT, ELLIPSE, POLYGON, PATH, NAME_TEXT, INFO_TEXT, LOGO, IMAGE, BACKGROUND }

fun StyleSpec.addElement(kind: NewElementKind): StyleSpec {
    val defaultSlot = layout.slots.firstOrNull()?.id ?: SpecLayoutContext.BLOCK_SLOT
    val element: ElementSpec = when (kind) {
        NewElementKind.RECT -> RectElement(
            id = uniqueElementId("rect"),
            placement = Placement(slot = defaultSlot)
        )
        NewElementKind.ELLIPSE -> EllipseElement(
            id = uniqueElementId("ellipse"),
            placement = Placement(slot = defaultSlot)
        )
        NewElementKind.POLYGON -> PolygonElement(
            id = uniqueElementId("polygon"),
            placement = Placement(slot = defaultSlot),
            verticesEm = listOf(listOf(0.0, -1.0), listOf(1.0, 1.0), listOf(-1.0, 1.0))
        )
        // A gentle S-curve with a draw-on TRIM track, so a fresh path demos growth
        // immediately.
        NewElementKind.PATH -> PathElement(
            id = uniqueElementId("path"),
            placement = Placement(slot = defaultSlot),
            verticesEm = listOf(
                CurveVertex(0.0, 2.0, outX = 0.6, outY = -0.8),
                CurveVertex(0.8, 0.0, inX = -0.6, inY = 0.8, outX = 0.6, outY = -0.8),
                CurveVertex(0.0, -2.0, inX = 0.6, inY = 0.8)
            ),
            tracks = listOf(
                AnimTrack(
                    AnimProperty.TRIM,
                    listOf(SpecKeyframe(0.0, listOf(0.0, 0.0)), SpecKeyframe(100.0, listOf(0.0, 1.0)))
                )
            )
        )
        NewElementKind.NAME_TEXT -> TextElement(
            id = uniqueElementId("name"),
            name = "Name",
            field = TextFieldRef.NAME,
            visibleWhen = listOf(VisibilityRule.NAME_VISIBLE),
            placement = Placement(slot = "text", anchorIn = AnchorIn.START, line = LineAnchor.NAME_LINE)
        )
        NewElementKind.INFO_TEXT -> TextElement(
            id = uniqueElementId("info"),
            name = "Info",
            field = TextFieldRef.INFO,
            visibleWhen = listOf(VisibilityRule.INFO_VISIBLE),
            placement = Placement(slot = "text", anchorIn = AnchorIn.START, line = LineAnchor.INFO_LINE)
        )
        NewElementKind.LOGO -> LogoElement(id = uniqueElementId("logo"))
        NewElementKind.IMAGE -> ImageElement(
            id = uniqueElementId("image"),
            placement = Placement(slot = defaultSlot)
        )
        NewElementKind.BACKGROUND -> BackgroundElement(id = uniqueElementId("background"))
    }
    // New elements land at the top of the stack (start of the list) so they are visible.
    return copy(elements = listOf(element) + elements)
}
