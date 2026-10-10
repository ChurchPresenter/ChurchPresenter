package org.churchpresenter.slides.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.slides.FakeSlidesOutput
import org.churchpresenter.slides.SlidesOutput
import org.churchpresenter.slides.solidImage
import org.churchpresenter.slides.tempDir
import java.awt.Color
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PicturePartsTest {

    private val dir = tempDir("cp-picture-parts")
    private val state = PicturesState(null)
    private val navigator = PictureNavigator(state, PicturePlayback(state))
    private val live = PicturesLive(state)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun fill(vararg names: String): List<File> {
        val files = names.map { File(dir, it).apply { writeText("x") } }
        state.images.addAll(files)
        return files
    }

    private class CuedOutput(val fake: FakeSlidesOutput = FakeSlidesOutput()) : SlidesOutput by fake {
        override val picturesCued: Boolean get() = true
    }

    @Test
    fun `a move with either end out of range changes nothing`() {
        val files = fill("a.jpg", "b.jpg")
        navigator.moveImage(0, 5)
        navigator.moveImage(5, 0)
        assertEquals(files, state.images.toList())
        assertEquals(0, state.imageOrderVersion.value)
    }

    @Test
    fun `a move with nothing selected reorders and keeps the selection index`() {
        val (a, b, c) = fill("a.jpg", "b.jpg", "c.jpg")
        state.selectedImageIndex.value = 9
        navigator.moveImage(0, 2)
        assertEquals(listOf(b, c, a), state.images.toList())
        assertEquals(9, state.selectedImageIndex.value)
        assertEquals(1, state.imageOrderVersion.value)
    }

    @Test
    fun `going live on the last picture of no folder cues nothing next and adds no schedule row`() {
        val (_, b) = fill("a.jpg", "b.jpg")
        state.selectedImageIndex.value = 1
        val out = FakeSlidesOutput()
        val rows = mutableListOf<ScheduleItem>()
        live.goLive(out, onInstanceLinkSendProject = { rows += it }, onWentLive = { rows += it })
        assertEquals(b.absolutePath, out.selectedImagePath.value)
        assertNull(out.nextImagePath.value)
        assertEquals(Presenting.PICTURES, out.onAir.value)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `going live with a folder reports the row to both listeners`() {
        fill("a.jpg", "b.jpg")
        state.selectedFolder.value = dir
        val rows = mutableListOf<ScheduleItem>()
        live.goLive(FakeSlidesOutput(), onInstanceLinkSendProject = { rows += it }, onWentLive = { rows += it })
        assertEquals(2, rows.size)
        assertEquals(2, (rows.first() as ScheduleItem.PictureItem).imageCount)
    }

    @Test
    fun `a cued output is kept in step with the selection`() {
        val (_, b) = fill("a.jpg", "b.jpg")
        state.selectedImageIndex.value = 1
        val out = CuedOutput()
        live.syncWithPresenter(out)
        assertEquals(b.absolutePath, out.fake.selectedImagePath.value)
        assertNull(out.fake.nextImagePath.value)
    }

    @Test
    fun `a locked screen with no picture selected is left alone`() {
        fill("a.jpg")
        state.selectedImageIndex.value = 4
        val out = FakeSlidesOutput().apply { setScreenLock(0, Presenting.PICTURES) }
        live.syncWithPresenter(out)
        assertNull(out.selectedImagePath.value)
    }

    @Test
    fun `an unreadable file is retried before it is recorded as failed`() = runTest {
        val thumbs = PictureThumbnails(state, Dispatchers.Unconfined)
        val bad = File(dir, "broken.jpg").apply { writeText("not an image") }
        val report = thumbs.decodeThumbnail(bad, attempts = 2)
        assertNotNull(report)
        assertTrue("broken.jpg" !in report)
        assertTrue(bad in state.thumbnailFailures)
    }

    @Test
    fun `a picture too thin to scale is retried and recorded as failed`() = runTest {
        val thumbs = PictureThumbnails(state, Dispatchers.Unconfined)
        val thin = solidImage(dir, "thin.png", Color.RED, width = 4000, height = 1)
        thumbs.decodeThumbnail(thin, attempts = 2)
        assertTrue(thin in state.thumbnailFailures)
    }
}
