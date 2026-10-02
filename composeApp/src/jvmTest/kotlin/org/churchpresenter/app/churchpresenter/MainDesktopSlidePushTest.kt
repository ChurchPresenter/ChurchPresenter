package org.churchpresenter.app.churchpresenter

import kotlinx.coroutines.runBlocking
import org.churchpresenter.sharedui.models.Presenting
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainDesktopSlidePushTest {

    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    private fun slideFiles(count: Int): List<File> {
        val dir = Files.createTempDirectory("cp-slide-push").toFile().also { dirs += it }
        return (0 until count).map { n ->
            File(dir, "slide-$n.jpg").also { f ->
                ImageIO.write(BufferedImage(10 + n, 4, BufferedImage.TYPE_INT_RGB), "jpg", f)
            }
        }
    }

    @Test
    fun `a step command pushes while the presentation is the live content`() {
        assertTrue(shouldPushSlide(Presenting.PRESENTATION, selectedIndex = 0, slideCount = 3))
        assertTrue(shouldPushSlide(Presenting.PRESENTATION, selectedIndex = 2, slideCount = 3))
    }

    @Test
    fun `a step command must not push over whatever else is live`() {
        listOf(Presenting.NONE, Presenting.LYRICS, Presenting.BIBLE, Presenting.MEDIA).forEach { mode ->
            assertFalse(
                shouldPushSlide(mode, selectedIndex = 0, slideCount = 3),
                "$mode is live, so a slide must not be pushed over it",
            )
        }
    }

    @Test
    fun `an index past the end of the deck pushes nothing`() {
        assertFalse(shouldPushSlide(Presenting.PRESENTATION, selectedIndex = 3, slideCount = 3))
    }

    @Test
    fun `a negative index pushes nothing`() {
        assertFalse(shouldPushSlide(Presenting.PRESENTATION, selectedIndex = -1, slideCount = 3))
    }

    @Test
    fun `with no deck loaded there is nothing to push`() {
        assertFalse(shouldPushSlide(Presenting.PRESENTATION, selectedIndex = 0, slideCount = 0))
    }

    @Test
    fun `the pair is the current slide and the one after it, in that order`() = runBlocking {
        val (current, next) = decodeSlideBitmaps(slideFiles(4), index = 1)

        assertEquals(11, current?.width, "the first value must be the slide asked for")
        assertEquals(12, next?.width, "the second must be the slide after it, not the one asked for")
    }

    @Test
    fun `the last slide has no next slide to prepare for`() = runBlocking {
        val (current, next) = decodeSlideBitmaps(slideFiles(3), index = 2)

        assertEquals(12, current?.width)
        assertNull(next, "the stage monitor's next pane must go empty at the end of the deck")
    }

    @Test
    fun `the first slide decodes with the second as its next`() = runBlocking {
        val (current, next) = decodeSlideBitmaps(slideFiles(3), index = 0)

        assertEquals(10, current?.width)
        assertEquals(11, next?.width)
    }

    @Test
    fun `an out-of-range index decodes nothing at all`() = runBlocking {
        val (current, next) = decodeSlideBitmaps(slideFiles(3), index = 9)

        assertNull(current)
        assertNull(next)
    }

    @Test
    fun `an empty deck decodes nothing`() = runBlocking {
        val (current, next) = decodeSlideBitmaps(emptyList(), index = 0)

        assertNull(current)
        assertNull(next)
    }
}
