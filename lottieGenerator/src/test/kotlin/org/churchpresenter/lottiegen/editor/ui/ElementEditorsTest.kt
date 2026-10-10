@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lottiegen.editor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import org.churchpresenter.lottiegen.spec.AnimProperty
import org.churchpresenter.lottiegen.spec.ColorRole
import org.churchpresenter.lottiegen.spec.CornerSpec
import org.churchpresenter.lottiegen.spec.CurveVertex
import org.churchpresenter.lottiegen.spec.EasingKind
import org.churchpresenter.lottiegen.spec.GrowOrigin
import org.churchpresenter.lottiegen.spec.ImageElement
import org.churchpresenter.lottiegen.spec.ImageScaleMode
import org.churchpresenter.lottiegen.spec.OffsetUnit
import org.churchpresenter.lottiegen.spec.PaintSpec
import org.churchpresenter.lottiegen.spec.PathElement
import org.churchpresenter.lottiegen.spec.PolygonElement
import org.churchpresenter.lottiegen.spec.RectElement
import org.churchpresenter.lottiegen.spec.RepeatSpec
import org.churchpresenter.lottiegen.spec.SizeSpec
import org.churchpresenter.lottiegen.spec.StrokeWidthSpec
import org.churchpresenter.lottiegen.spec.TextAnimatorKind
import org.churchpresenter.lottiegen.spec.TextElement
import org.churchpresenter.lottiegen.spec.TextFieldRef
import org.churchpresenter.lottiegen.spec.WidthBasis
import org.churchpresenter.lottiegen.ui.EditorStrings
import org.churchpresenter.lottiegen.ui.Hosted
import org.churchpresenter.lottiegen.ui.Strings
import org.churchpresenter.lottiegen.ui.choose
import org.churchpresenter.lottiegen.ui.click
import org.churchpresenter.lottiegen.ui.clickDescription
import org.churchpresenter.lottiegen.ui.fillEveryField
import org.churchpresenter.lottiegen.ui.hasNode
import org.churchpresenter.lottiegen.ui.showDark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ElementEditorsTest {

    @Test
    fun `the size editor switches between its four kinds and edits each`() = runDesktopComposeUiTest(1000, 1200) {
        var size by mutableStateOf<SizeSpec>(SizeSpec.Em(1.0, 1.0))
        showDark { Hosted(size, { size = it }) { s, set -> SizeEditor(s, set) } }
        fillEveryField("2")
        assertEquals(SizeSpec.Em(2.0, 2.0), size)
        choose(Strings.editorSizeType, Strings.editorSizeContent)
        fillEveryField("3")
        assertEquals(SizeSpec.ContentDerived(3.0, 3.0), size)
        choose(Strings.editorSizeType, Strings.editorSizeTextWrap)
        choose(Strings.editorTextFieldRef, EditorLabels.textField(TextFieldRef.DETAIL))
        fillEveryField("4")
        assertEquals(SizeSpec.TextWrap(TextFieldRef.DETAIL, 4.0, 4.0), size)
        choose(Strings.editorSizeType, Strings.editorSizeCanvasWidth)
        fillEveryField("5")
        assertEquals(5.0, (size as SizeSpec.CanvasWidth).hEm)
        choose(Strings.editorSizeType, Strings.editorSizeEm)
        assertEquals(SizeSpec.Em(1.0, 1.0), size)
    }

    @Test
    fun `the corner and grow editors cycle their options`() = runDesktopComposeUiTest(1000, 1200) {
        var corner by mutableStateOf<CornerSpec>(CornerSpec.None)
        var grow by mutableStateOf(GrowOrigin.CENTER)
        showDark {
            Column {
                CornerEditor(corner) { corner = it }
                GrowFromEditor(grow) { grow = it }
            }
        }
        choose(Strings.editorCorner, Strings.editorCornerFromConfig)
        fillEveryField("2")
        assertEquals(CornerSpec.FromConfig(2.0), corner)
        choose(Strings.editorCorner, Strings.editorCornerEm)
        fillEveryField("0.5")
        assertEquals(CornerSpec.Em(0.5), corner)
        choose(Strings.editorCorner, Strings.editorCornerNone)
        assertEquals(CornerSpec.None, corner)
        choose(Strings.editorGrowFrom, Strings.editorGrowEdge)
        assertEquals(GrowOrigin.ALIGN_EDGE, grow)
    }

    @Test
    fun `the repeat editor turns on, edits every field and turns off`() = runDesktopComposeUiTest(1000, 1200) {
        var repeat by mutableStateOf<RepeatSpec?>(null)
        showDark { Hosted(repeat, { repeat = it }) { r, set -> RepeatEditor(r, set) } }
        click(Strings.editorRepeat)
        assertEquals(RepeatSpec(), repeat)
        fillEveryField("6")
        assertEquals(RepeatSpec(copies = 6, offsetXEm = 6.0, offsetYEm = 6.0, rotationDeg = 6.0, scalePct = 6.0), repeat)
        choose(Strings.editorRepeatFitWidth, Strings.editorFitTextBlock)
        click(Strings.editorRepeatFade)
        assertEquals(WidthBasis.TEXT_BLOCK, repeat?.fitWidthTo)
        assertTrue(repeat!!.fadeOut)
        click(Strings.editorRepeat)
        assertNull(repeat)
    }

    @Test
    fun `the paint editor edits fill, gradient and stroke`() = runDesktopComposeUiTest(1000, 1400) {
        var paint by mutableStateOf(PaintSpec())
        showDark { Hosted(paint, { paint = it }) { p, set -> PaintEditor(p, set) } }
        choose(Strings.editorColorRole, EditorLabels.role(ColorRole.NAME))
        assertEquals(ColorRole.NAME, paint.fill?.role)
        click(Strings.editorGradient)
        click(Strings.editorStroke)
        choose(Strings.editorStrokeWidthType, Strings.editorStrokeEm)
        assertEquals(StrokeWidthSpec.Em(0.1), paint.stroke?.width)
        fillEveryField("2")
        val gradient = paint.fill?.gradient!!
        assertEquals(listOf(2.0, 2.0, 2.0, 2.0), listOf(gradient.startXEm, gradient.startYEm, gradient.endXEm, gradient.endYEm))
        assertEquals(StrokeWidthSpec.Em(2.0), paint.stroke?.width)
        assertEquals(2.0, paint.stroke?.dashEm)
        choose(Strings.editorStrokeWidthType, Strings.editorStrokeFromConfig)
        assertEquals(StrokeWidthSpec.FromConfig, paint.stroke?.width)
        choose(Strings.editorColorRole, EditorLabels.role(ColorRole.DETAIL), index = 1)
        assertEquals(ColorRole.DETAIL, paint.stroke?.role)
        click(Strings.editorGradient)
        assertNull(paint.fill?.gradient)
        click(Strings.editorStroke)
        click(Strings.editorFill)
        assertEquals(PaintSpec(fill = null, stroke = null), paint)
    }

    @Test
    fun `the text options editor edits its field, mask and animator`() = runDesktopComposeUiTest(1000, 1200) {
        var element by mutableStateOf(TextElement("t", "Name", field = TextFieldRef.NAME))
        showDark { Hosted(element, { element = it }) { e, set -> TextOptionsEditor(e, set) } }
        choose(Strings.editorTextFieldRef, EditorLabels.textField(TextFieldRef.INFO))
        click(Strings.editorMaskReveal)
        click(Strings.editorAnimator)
        choose(Strings.editorAnimatorKind, TrackLabels.animatorKind(TextAnimatorKind.RANDOM_FADE))
        fillEveryField("9")
        assertEquals(TextFieldRef.INFO, element.field)
        val mask = element.maskReveal!!
        assertEquals(listOf(9.0, 9.0, 9.0), listOf(mask.padPx, mask.heightFactor, mask.yOffsetFactor))
        val animator = element.animator!!
        assertEquals(TextAnimatorKind.RANDOM_FADE, animator.kind)
        assertEquals(listOf(9.0, 9.0, 9.0), listOf(animator.startPct, animator.endPct, animator.posOffsetEm))
        click(Strings.editorMaskReveal)
        click(Strings.editorAnimator)
        assertNull(element.maskReveal)
        assertNull(element.animator)
    }

    @Test
    fun `the polygon editor adds, edits and deletes vertices`() = runDesktopComposeUiTest(1000, 1200) {
        var element by mutableStateOf(PolygonElement("p"))
        showDark { Hosted(element, { element = it }) { e, set -> PolygonEditor(e, set) } }
        click(Strings.editorAddVertex)
        click(Strings.editorAddVertex)
        fillEveryField("1.5")
        assertEquals(listOf(listOf(1.5, 1.5), listOf(1.5, 1.5)), element.verticesEm)
        click(Strings.editorClosedPath)
        choose(Strings.editorFitWidth, Strings.editorFitName)
        assertEquals(false, element.closed)
        assertEquals(WidthBasis.NAME, element.fitWidthTo)
        clickDescription(Strings.editorDelete)
        assertEquals(1, element.verticesEm.size)
        choose(Strings.editorFitWidth, Strings.editorFitNone)
        assertNull(element.fitWidthTo)
    }

    @Test
    fun `the path editor adds, edits and deletes curve vertices`() = runDesktopComposeUiTest(1200, 1200) {
        var element by mutableStateOf(PathElement("p"))
        showDark { Hosted(element, { element = it }) { e, set -> PathVerticesEditor(e, set) } }
        click(Strings.editorAddVertex)
        click(Strings.editorAddVertex)
        assertEquals(listOf(CurveVertex(1.0, 0.0), CurveVertex(2.0, 0.0)), element.verticesEm)
        fillEveryField("3")
        assertEquals(List(2) { CurveVertex(3.0, 3.0, 3.0, 3.0, 3.0, 3.0) }, element.verticesEm)
        click(Strings.editorClosedPath)
        assertTrue(element.closed)
        clickDescription(Strings.editorDelete)
        assertEquals(1, element.verticesEm.size)
    }

    @Test
    fun `the image options show the picture and edit scale and alpha`() = runDesktopComposeUiTest(1000, 1000) {
        var element by mutableStateOf(ImageElement("i"))
        showDark { Hosted(element, { element = it }) { e, set -> ImageOptionsEditor(e, set) } }
        assertTrue(hasNode(Strings.editorImageNone))
        choose(Strings.editorImageScaleMode, Strings.editorImageCover)
        choose(Strings.editorImageScaleMode, Strings.editorImageStretch)
        fillEveryField("4")
        assertEquals(ImageScaleMode.STRETCH, element.scaleMode)
        assertEquals(1.0, element.alphaFactor)
        element = element.copy(dataUri = "x".repeat(4096), naturalW = 20, naturalH = 10)
        waitForIdle()
        assertTrue(hasNode(EditorStrings.imageInfo(20, 10, 3)))
    }

    @Test
    fun `the tracks editor adds a track and edits its keyframes, easing and overrides`() =
        runDesktopComposeUiTest(1400, 2400) {
            var element by mutableStateOf(RectElement("r"))
            showDark { TracksEditor(element) { element = element.copy(tracks = it) } }
            click(Strings.editorAddTrack)
            assertEquals(AnimProperty.OPACITY, element.tracks.single().property)
            choose(Strings.editorProperty, TrackLabels.property(AnimProperty.POSITION_OFFSET))
            choose(Strings.editorEasing, TrackLabels.easing(EasingKind.LINEAR))
            choose(Strings.editorOffsetUnit, TrackLabels.offsetUnit(OffsetUnit.ELEMENT_WIDTH))
            val track = element.tracks.single()
            assertEquals(AnimProperty.POSITION_OFFSET, track.property)
            assertEquals(EasingKind.LINEAR to OffsetUnit.ELEMENT_WIDTH, track.easing to track.offsetUnit)
            click("${Strings.editorAddOverride}: ${EditorLabels.align("left")}")
            click(Strings.editorAddKeyframe)
            fillEveryField("50")
            val edited = element.tracks.single()
            assertTrue(edited.keyframes.all { it.pct == 50.0 && it.values == listOf(50.0, 50.0) })
            assertTrue(edited.alignOverrides.getValue("left").all { it.values == listOf(50.0, 50.0) })
            click(Strings.editorRemoveOverride)
            assertTrue(element.tracks.single().alignOverrides.isEmpty())
            clickDescription(Strings.editorDelete)
            assertEquals(2, element.tracks.single().keyframes.size)
            click(Strings.editorRemoveTrack)
            assertTrue(element.tracks.isEmpty())
        }

    @Test
    fun `every property gets default keyframes of its own arity`() {
        AnimProperty.entries.forEach { property ->
            val keyframes = defaultKeyframes(property)
            assertEquals(2, keyframes.size)
            assertTrue(keyframes.all { it.values.size == valueArity(property) })
        }
    }
}
