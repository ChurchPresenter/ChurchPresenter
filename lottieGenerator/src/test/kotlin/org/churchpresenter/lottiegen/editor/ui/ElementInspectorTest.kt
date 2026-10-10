@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.churchpresenter.lottiegen.editor.withCommon
import org.churchpresenter.lottiegen.spec.ElementSpec
import org.churchpresenter.lottiegen.ui.Hosted
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.churchpresenter.lottiegen.spec.AnchorIn
import org.churchpresenter.lottiegen.spec.BackgroundElement
import org.churchpresenter.lottiegen.spec.EllipseElement
import org.churchpresenter.lottiegen.spec.GrowOrigin
import org.churchpresenter.lottiegen.spec.WidthBasis
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.LayoutSpec
import org.churchpresenter.lottiegen.spec.LineAnchor
import org.churchpresenter.lottiegen.spec.LogoElement
import org.churchpresenter.lottiegen.spec.MirrorMode
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.SlotKind
import org.churchpresenter.lottiegen.spec.SlotSpec
import org.churchpresenter.lottiegen.spec.SpecLayoutContext
import org.churchpresenter.lottiegen.spec.StyleSpec
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.VisibilityRule
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.choose
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.clickDescription
import org.churchpresenter.lottiegen.ui.fillEveryField
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.pick
import org.churchpresenter.lottiegen.ui.showDark
import org.churchpresenter.lottiegen.ui.type
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElementInspectorTest {

    private val everyKind = StyleSpec(
        id = "90",
        name = "Draft",
        elements = listOf(
            RectElement("rect", name = "Bar"),
            EllipseElement("ellipse", name = "Dot"),
            PolygonElement("poly", name = "Tri"),
            PathElement("path", name = "Line"),
            TextElement("text", name = "Title", field = TextFieldRef.NAME),
            BackgroundElement("bg", name = "Back"),
            ImageElement("img", name = "Pic"),
            LogoElement("logo", name = "Mark"),
        ),
    )

    private fun inspector(state: FakeEditorState): @androidx.compose.runtime.Composable () -> Unit = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ElementListPanel(state)
            ElementInspector(state)
        }
    }

    @Test
    fun `every element kind opens its own sections`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(everyKind)
        showDark(content = inspector(state))
        assertTrue(hasNode(Strings.editorNoSelection))
        for (element in everyKind.elements) {
            click(element.name)
            assertEquals(element.id, state.selectedElementId)
            assertTrue(hasNode(Strings.editorSectionGeneral))
            listOf(Strings.editorSectionSize, Strings.editorSectionPaint).forEach {
                if (hasNode(it) && !hasNode(Strings.editorSizeType.uppercase())) click(it)
            }
        }
        click("Mark")
        assertNull(state.selectedElementId)
    }

    @Test
    fun `the shape sections of each kind write back into the spec`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(everyKind)
        showDark(content = inspector(state))
        for (id in listOf("rect", "ellipse", "poly", "path", "bg", "img")) {
            state.selectElement(id)
            waitForIdle()
            listOf(Strings.editorSectionSize, Strings.editorSectionPaint).forEach {
                if (hasNode(it) && !(id == "path" && it == Strings.editorSectionSize)) click(it)
            }
            if (hasNode(Strings.editorRepeat)) click(Strings.editorRepeat)
            if (hasNode(Strings.editorStroke)) click(Strings.editorStroke)
        }
        val elements = state.spec.elements.associateBy { it.id }
        assertTrue((elements.getValue("rect") as RectElement).repeat != null)
        assertTrue((elements.getValue("ellipse") as EllipseElement).paint.stroke != null)
        assertTrue((elements.getValue("poly") as PolygonElement).repeat != null)
        assertTrue((elements.getValue("path") as PathElement).repeat != null)
        assertTrue((elements.getValue("bg") as BackgroundElement).paint.stroke != null)
    }

    @Test
    fun `a path fits its width and a background grows from where it is told`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(everyKind)
        state.selectElement("path")
        showDark(content = inspector(state))
        choose(Strings.editorFitWidth, Strings.editorFitInfo)
        state.selectElement("bg")
        waitForIdle()
        click(Strings.editorSectionSize)
        choose(Strings.editorGrowFrom, Strings.editorGrowCenter)
        val elements = state.spec.elements.associateBy { it.id }
        assertEquals(WidthBasis.INFO, (elements.getValue("path") as PathElement).fitWidthTo)
        assertEquals(GrowOrigin.CENTER, (elements.getValue("bg") as BackgroundElement).growFrom)
    }

    @Test
    fun `general and placement edit the selected element`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(everyKind)
        state.selectElement("rect")
        showDark(content = inspector(state))
        type("Bar", "Banner")
        click(EditorLabels.rule(VisibilityRule.LOGO_ENABLED))
        choose(Strings.editorSlot, Strings.editorSlotBlock)
        choose(Strings.editorAnchor, EditorLabels.anchor(AnchorIn.END))
        choose(Strings.editorLine, EditorLabels.line(LineAnchor.INFO_LINE))
        click(Strings.editorMirror)
        val rect = state.spec.elements.first() as RectElement
        assertEquals("Banner", rect.name)
        assertEquals(listOf(VisibilityRule.LOGO_ENABLED), rect.visibleWhen)
        val placement = rect.placement
        assertEquals(SpecLayoutContext.BLOCK_SLOT, placement.slot)
        assertEquals(AnchorIn.END to LineAnchor.INFO_LINE, placement.anchorIn to placement.line)
        assertEquals(MirrorMode.NONE, placement.mirror)
        click(EditorLabels.rule(VisibilityRule.LOGO_ENABLED))
        click(Strings.editorMirror)
        assertTrue((state.spec.elements.first() as RectElement).visibleWhen.isEmpty())
        assertEquals(MirrorMode.FLIP_ON_RIGHT, state.spec.elements.first().placement.mirror)
    }

    @Test
    fun `alignment overrides are added, edited and removed`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(everyKind)
        state.selectElement("rect")
        showDark(content = inspector(state))
        for (align in listOf("left", "center", "right")) {
            click("${Strings.editorAddOverride}: ${EditorLabels.align(align)}")
        }
        assertEquals(setOf("left", "center", "right"), state.spec.elements.first().placement.alignOverrides.keys)
        choose(Strings.editorAnchor, EditorLabels.anchor(AnchorIn.START), index = 1)
        fillEveryField("2")
        val placement = state.spec.elements.first().placement
        assertEquals(
            listOf(2.0, 2.0, 2.0, 2.0),
            listOf(placement.offsetXEm, placement.offsetYEm, placement.pivotXEm, placement.pivotYEm),
        )
        assertEquals(AnchorIn.START, placement.alignOverrides.getValue("left").anchorIn)
        assertTrue(placement.alignOverrides.values.all { it.offsetXEm == 2.0 && it.offsetYEm == 2.0 })
        repeat(3) { click(Strings.editorRemoveOverride) }
        assertTrue(state.spec.elements.first().placement.alignOverrides.isEmpty())
    }

    @Test
    fun `the placement editor on its own edits every field of a pivoting element`() =
        runDesktopComposeUiTest(900, 2000) {
        val state = FakeEditorState(everyKind)
        var element by mutableStateOf<ElementSpec>(everyKind.elements[2])
        showDark {
            Hosted(element, { element = it }) { e, set ->
                PlacementEditor(state, e) { p -> set(e.withCommon(placement = p)) }
            }
        }
        choose(Strings.editorSlot, "logo")
        choose(Strings.editorLine, EditorLabels.line(LineAnchor.NAME_LINE))
        click("${Strings.editorAddOverride}: ${EditorLabels.align("right")}")
        choose(Strings.editorAnchor, EditorLabels.anchor(AnchorIn.CENTER), index = 1)
        fillEveryField("1.5")
        val placement = element.placement
        assertEquals("logo" to LineAnchor.NAME_LINE, placement.slot to placement.line)
        assertEquals(listOf(1.5, 1.5), listOf(placement.pivotXEm, placement.pivotYEm))
        assertEquals(1.5, placement.alignOverrides.getValue("right").offsetYEm)
        click(Strings.editorRemoveOverride)
        assertTrue(element.placement.alignOverrides.isEmpty())
    }

    @Test
    fun `the element list adds every kind, moves and deletes`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(StyleSpec(id = "90", name = "Draft"))
        showDark(content = inspector(state))
        val labels = listOf(
            Strings.editorElementRect, Strings.editorElementEllipse, Strings.editorElementPolygon,
            Strings.editorElementPath, Strings.editorElementNameText, Strings.editorElementInfoText,
            Strings.editorElementLogo, Strings.editorElementImage, Strings.editorElementBackground,
        )
        for (label in labels) {
            click(Strings.editorAddElement)
            pick(label)
        }
        assertEquals(labels.size, state.spec.elements.size)
        val before = state.spec.elements.map { it.id }
        clickDescription(Strings.editorMoveDown)
        assertEquals(before[0], state.spec.elements[1].id)
        clickDescription(Strings.editorMoveUp, 1)
        assertEquals(before, state.spec.elements.map { it.id })
        state.selectElement(before[0])
        waitForIdle()
        clickDescription(Strings.editorDelete)
        assertNull(state.selectedElementId)
        assertEquals(labels.size - 1, state.spec.elements.size)
    }

    @Test
    fun `the layout inspector edits block options and slots`() = runDesktopComposeUiTest(900, 3000) {
        val state = FakeEditorState(
            StyleSpec(
                layout = LayoutSpec(slots = listOf(SlotSpec("logo", SlotKind.LOGO), SlotSpec("text", SlotKind.TEXT))),
            ),
        )
        showDark(content = inspector(state))
        click(Strings.editorCenterSingleLine)
        click(Strings.editorAddSlot)
        assertTrue(state.spec.layout.centerSingleLine)
        assertEquals("slot1", state.spec.layout.slots.last().id)
        choose(Strings.editorSlotKind, EditorLabels.slotKind(SlotKind.FIXED))
        click(EditorLabels.rule(VisibilityRule.BG_ENABLED))
        assertEquals(SlotKind.FIXED, state.spec.layout.slots.first().kind)
        assertEquals(listOf(VisibilityRule.BG_ENABLED), state.spec.layout.slots.first().visibleWhen)
        click(EditorLabels.rule(VisibilityRule.BG_ENABLED))
        clickDescription(Strings.editorMoveDown)
        assertEquals("text", state.spec.layout.slots.first().id)
        clickDescription(Strings.editorMoveUp)
        clickDescription(Strings.editorMoveDown, 2)
        assertEquals(listOf("text", "logo", "slot1"), state.spec.layout.slots.map { it.id })
        fillEveryField("3")
        assertEquals(3.0, state.spec.layout.blockHeightEm)
        assertEquals(listOf("3", "3", "3"), state.spec.layout.slots.map { it.id })
        assertTrue(state.spec.layout.slots.all { it.gapBeforeEm == 3.0 && it.gapAfterEm == 3.0 })
        clickDescription(Strings.editorDelete)
        assertEquals(2, state.spec.layout.slots.size)
    }
}
