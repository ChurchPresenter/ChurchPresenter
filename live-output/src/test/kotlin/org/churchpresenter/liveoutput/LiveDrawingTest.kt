package org.churchpresenter.liveoutput

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A remote device's drawing on the outputs: each page replaces the last, and leaving the screen forgets it. */
class LiveDrawingTest {

    private fun page() = ImageBitmap(2, 2)

    @Test
    fun `nothing is up before a page arrives`() {
        val presenter = PresenterManager()

        assertNull(presenter.drawingFrame.value)
        assertEquals(0L, presenter.drawingRevision.value)
    }

    @Test
    fun `each page replaces the last and counts as a new revision`() {
        val presenter = PresenterManager()
        presenter.setPresentingMode(Presenting.DRAWING)
        val first = page()
        val second = page()

        presenter.showDrawingFrame(first, Color.White)
        presenter.showDrawingFrame(second, Color.Blue)

        assertSame(second, presenter.drawingFrame.value)
        assertEquals(Color.Blue, presenter.drawingPaper.value)
        assertEquals(2L, presenter.drawingRevision.value)
    }

    @Test
    fun `clearing forgets the page and its paper`() {
        val presenter = PresenterManager()
        presenter.showDrawingFrame(page(), Color.White)

        presenter.clearDrawing()

        assertNull(presenter.drawingFrame.value)
        assertEquals(Color.Black, presenter.drawingPaper.value)
        assertEquals(0L, presenter.drawingRevision.value)
    }

    @Test
    fun `anything else going live takes the drawing down`() {
        val presenter = PresenterManager()
        presenter.setPresentingMode(Presenting.DRAWING)
        presenter.showDrawingFrame(page(), Color.White)

        presenter.setPresentingMode(Presenting.LYRICS)

        assertNull(presenter.drawingFrame.value, "the next drawing starts from its own first page")
    }
}
