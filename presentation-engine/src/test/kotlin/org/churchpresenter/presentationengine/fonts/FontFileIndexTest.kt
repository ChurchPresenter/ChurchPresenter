package org.churchpresenter.presentationengine.fonts

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The font-file index answers from a finished scan, never from a half-built one. */
class FontFileIndexTest {

    @TempDir
    lateinit var dir: File

    private fun font(name: String, under: File = dir): File =
        File(under, name).apply { parentFile.mkdirs(); writeBytes(byteArrayOf(0)) }

    private fun index(scans: AtomicInteger = AtomicInteger(), dirs: List<File> = listOf(dir)) =
        FontFileIndex({ scans.incrementAndGet(); dirs }, maxDepth = 2) { it.nameWithoutExtension.lowercase() }

    /**
     * The startup scan adds files one at a time, so a lookup can arrive while the index holds some
     * but not all of them. That lookup has to finish the scan, not answer from what is there.
     */
    @Test
    fun `a lookup that finds a few files already added still scans for the rest`() {
        val verdana = font("Verdana.ttf")
        val arial = font("Arial.ttf")
        val index = index()

        index.add(verdana)

        assertEquals(arial, index.find("arial"))
    }

    @Test
    fun `the directories are walked once however many lookups there are`() {
        font("Arial.ttf")
        val scans = AtomicInteger()
        val index = index(scans)

        index.find("arial")
        index.find("verdana")
        index.find("arial")

        assertEquals(1, scans.get())
    }

    @Test
    fun `lookups racing the first scan all wait for it and all find the font`() {
        repeat(LOOKUPS) { font("Filler$it.ttf") }
        val arial = font("Arial.ttf")
        val index = index()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(LOOKUPS)
        try {
            val answers = (1..LOOKUPS).map { pool.submit<File?> { start.await(); index.find("arial") } }
            start.countDown()

            answers.forEach { assertEquals(arial, it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `only font files are indexed, nested and in any letter case`() {
        font("notes.txt")
        val nested = font("Lato.OTF", File(dir, "lato"))
        val index = index()

        assertNull(index.find("notes"))
        assertEquals(nested, index.find("lato"))
    }

    @Test
    fun `a directory that is not there is passed over`() {
        val arial = font("Arial.ttf")
        val index = index(dirs = listOf(File(dir, "missing"), dir))

        assertEquals(arial, index.find("arial"))
    }

    @Test
    fun `the first file under a key is the one that stays`() {
        val first = font("Arial.ttf", File(dir, "a"))
        font("Arial.ttf", File(dir, "b"))
        val index = index(dirs = emptyList())

        index.add(first)
        index.add(File(dir, "b/Arial.ttf"))

        assertEquals(first, assertNotNull(index.find("arial")))
        assertTrue(FontFileIndex.FONT_FILE_EXTENSIONS.containsAll(listOf("ttf", "otf", "ttc")))
    }

    private companion object {
        const val LOOKUPS = 8
        const val TIMEOUT_SECONDS = 10L
    }
}
