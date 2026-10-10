package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.pptx.PptxSlideRasterizer.RunFillGuard
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextField
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextLineBreak
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraphProperties
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunFillGuardTest {

    @Test
    fun `a run without properties is hidden and then left as it was`() {
        val run = CTRegularTextRun.Factory.newInstance().apply { t = "Hello" }
        val guard = assertNotNull(RunFillGuard.hide(run))
        assertTrue(run.isSetRPr)
        assertEquals(0, run.rPr.solidFill.srgbClr.alphaList.single().`val`)
        guard.restore()
        assertFalse(run.isSetRPr)
    }

    @Test
    fun `a run with its own solid fill gets that fill back`() {
        val run = CTRegularTextRun.Factory.newInstance()
        run.addNewRPr().addNewSolidFill().addNewSrgbClr().`val` = byteArrayOf(0x11, 0x22, 0x33)
        val guard = assertNotNull(RunFillGuard.hide(run))
        assertEquals(1, run.rPr.solidFill.srgbClr.alphaList.size)
        guard.restore()
        assertTrue(run.isSetRPr)
        assertContentEquals(byteArrayOf(0x11, 0x22, 0x33), run.rPr.solidFill.srgbClr.`val`)
        assertTrue(run.rPr.solidFill.srgbClr.alphaList.isEmpty())
    }

    @Test
    fun `a run with properties but no fill keeps its properties after restore`() {
        val run = CTRegularTextRun.Factory.newInstance()
        run.addNewRPr().b = true
        RunFillGuard.hide(run)!!.restore()
        assertTrue(run.isSetRPr)
        assertTrue(run.rPr.b)
        assertFalse(run.rPr.isSetSolidFill)
    }

    @Test
    fun `a field run is hidden like a text run`() {
        val field = CTTextField.Factory.newInstance().apply { id = "{0}"; type = "slidenum" }
        val guard = assertNotNull(RunFillGuard.hide(field))
        assertTrue(field.rPr.isSetSolidFill)
        guard.restore()
        assertFalse(field.isSetRPr)
    }

    @Test
    fun `a gradient run is left visible rather than corrupted`() {
        val run = CTRegularTextRun.Factory.newInstance()
        run.addNewRPr().addNewGradFill()
        RunFillGuard.hide(run)
        assertFalse(run.rPr.isSetSolidFill)
        assertTrue(run.rPr.isSetGradFill)
    }

    @Test
    fun `line breaks and foreign elements are not hidden`() {
        assertNull(RunFillGuard.hide(CTTextLineBreak.Factory.newInstance()))
        assertNull(RunFillGuard.hide(CTTextParagraphProperties.Factory.newInstance()))
    }

    @Test
    fun `a bullet color is swapped for a transparent one and put back`() {
        val pPr = CTTextParagraphProperties.Factory.newInstance()
        pPr.addNewBuClr().addNewSrgbClr().`val` = byteArrayOf(0x44, 0x55, 0x66)
        val guard = RunFillGuard.hideBullet(pPr)
        assertEquals(0, pPr.buClr.srgbClr.alphaList.single().`val`)
        guard.restore()
        assertContentEquals(byteArrayOf(0x44, 0x55, 0x66), pPr.buClr.srgbClr.`val`)
        assertTrue(pPr.buClr.srgbClr.alphaList.isEmpty())
    }

    @Test
    fun `a bullet without its own color has none after restore`() {
        val pPr = CTTextParagraphProperties.Factory.newInstance()
        val guard = RunFillGuard.hideBullet(pPr)
        assertTrue(pPr.isSetBuClr)
        guard.restore()
        assertFalse(pPr.isSetBuClr)
    }
}
