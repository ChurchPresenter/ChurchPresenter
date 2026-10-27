package org.churchpresenter.app.churchpresenter.remote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.server.DrawingDesk
import org.churchpresenter.server.DrawingFrame
import org.churchpresenter.sharedui.models.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A remote drawing after the operator's prompt: up when allowed, refreshed as drawn, down when ended. */
class RemoteDrawingShowTest {

    private val desk = DrawingDesk()
    private val presenter = PresenterManager()
    private val pages = mutableMapOf<String, DrawingPage?>()

    /** Decodes by title, so a test says which picture each frame stands for — or that it is not one. */
    private val show = RemoteDrawingShow(desk, presenter, Dispatchers.Unconfined) { frame -> pages[frame.title] }

    private fun frame(title: String, client: String = "ipad") = DrawingFrame(client, byteArrayOf(1), "#FFFFFF", title)

    private fun picture(title: String): DrawingPage =
        DrawingPage(ImageBitmap(2, 2), Color.White).also { pages[title] = it }

    @Test
    fun `allowing puts the latest page up and answers yes`() = runBlocking {
        picture("first")
        val latest = picture("latest")
        val (request, _) = desk.ask(frame("first"))
        desk.hold(frame("latest"))

        show.allow(request)

        assertTrue(request.decision.await())
        assertEquals(Presenting.DRAWING, presenter.slideContent.value)
        assertSame(latest.bitmap, presenter.drawingFrame.value, "the page being drawn now, not the first")
        assertTrue(desk.isLive("ipad"))
    }

    @Test
    fun `a page that is not a picture is answered no and nothing goes up`() = runBlocking {
        pages["broken"] = null
        val (request, _) = desk.ask(frame("broken"))

        show.allow(request)

        assertFalse(request.decision.await())
        assertNull(presenter.drawingFrame.value)
        assertFalse(desk.isLive("ipad"))
    }

    @Test
    fun `a request withdrawn before the operator answered puts nothing up`() = runBlocking {
        picture("first")
        val (request, _) = desk.ask(frame("first"))
        desk.withdraw("ipad")

        show.allow(request)

        assertFalse(request.decision.await())
        assertNull(presenter.drawingFrame.value)
    }

    @Test
    fun `later pages replace the one on screen while the drawing is live`() = runBlocking {
        picture("first")
        val next = picture("next")
        val (request, _) = desk.ask(frame("first"))
        show.allow(request)

        show.refresh(frame("next"))

        assertSame(next.bitmap, presenter.drawingFrame.value)
    }

    @Test
    fun `a page from a drawing that is not live is dropped`() = runBlocking {
        picture("stray")

        show.refresh(frame("stray"))

        assertNull(presenter.drawingFrame.value)
    }

    @Test
    fun `once the operator moves on a late page does not snatch the screen back`() = runBlocking {
        picture("first")
        picture("late")
        val (request, _) = desk.ask(frame("first"))
        show.allow(request)
        presenter.setPresentingMode(Presenting.LYRICS)

        show.refresh(frame("late"))

        assertEquals(Presenting.LYRICS, presenter.slideContent.value)
        assertNull(presenter.drawingFrame.value)
    }

    @Test
    fun `ending the live drawing clears the outputs`() = runBlocking {
        picture("first")
        val (request, _) = desk.ask(frame("first"))
        show.allow(request)

        show.ended()

        assertTrue(presenter.clearDisplayRequested.value)
    }

    @Test
    fun `ending when something else is up leaves it alone`() {
        presenter.setPresentingMode(Presenting.LYRICS)

        show.ended()

        assertFalse(presenter.clearDisplayRequested.value)
    }
}
