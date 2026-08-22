package org.churchpresenter.presentationengine

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.churchpresenter.presentationengine.cache.SlideCacheSupersededException
import org.churchpresenter.presentationengine.cache.SlideDiskCache
import org.churchpresenter.presentationengine.model.DeckFormat
import org.churchpresenter.presentationengine.model.Fidelity
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlideDiskCacheTest {

    @TempDir
    lateinit var tempDir: File

    private fun image(color: Color): BufferedImage =
        BufferedImage(64, 36, BufferedImage.TYPE_INT_ARGB).also {
            val g = it.createGraphics()
            g.color = color
            g.fillRect(0, 0, 64, 36)
            g.dispose()
        }

    private fun newCache() = SlideDiskCache(File(tempDir, "cache"))

    private fun sourceFile() = File(tempDir, "deck.pptx").apply { if (!exists()) writeText("stub") }

    @Test
    fun `write commit lookup round-trips slides and notes`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, renderWidthPx = 1920)
        writer.putSlide(0, image(Color.RED), "note zero", Fidelity.NATIVE, hasTimeline = false)
        writer.putSlide(1, image(Color.BLUE), "", Fidelity.NATIVE, hasTimeline = false)
        writer.commit()

        val cached = assertNotNull(cache.lookup(source, renderWidthPx = 1920))
        assertEquals(2, cached.slideFiles.size)
        assertTrue(cached.slideFiles.all { it.isFile })
        assertEquals(listOf("note zero", ""), cached.notes)
        assertEquals(DeckFormat.PPTX, cached.manifest.format)
    }

    @Test
    fun `uncommitted write is invisible`() {
        val cache = newCache()
        val source = sourceFile()
        cache.beginWrite(source, DeckFormat.PPTX, 1920)
            .putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `source mtime change invalidates`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        writer.commit()
        source.setLastModified(source.lastModified() + 5_000)
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `render width change invalidates`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        writer.commit()
        assertNull(cache.lookup(source, 1280))
    }

    @Test
    fun `legacy v1 entry (mtime txt, no manifest) is invalid`() {
        val cache = newCache()
        val source = sourceFile()
        val dir = cache.dirFor(source).apply { mkdirs() }
        File(dir, "slide_0000.jpg").writeBytes(Fixtures.jpegBytes(64, 36, Color.RED))
        File(dir, "mtime.txt").writeText(source.lastModified().toString())
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `a superseded writer cannot write or delete the newer entry`() {
        val cache = newCache()
        val source = sourceFile()
        val first = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        first.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)

        val second = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        second.putSlide(0, image(Color.BLUE), "", Fidelity.NATIVE, false)

        assertFailsWith<SlideCacheSupersededException> {
            first.putSlide(1, image(Color.RED), "", Fidelity.NATIVE, false)
        }
        assertFailsWith<SlideCacheSupersededException> { first.commit() }
        first.abort()

        second.putSlide(1, image(Color.BLUE), "", Fidelity.NATIVE, false)
        second.commit()

        val cached = assertNotNull(cache.lookup(source, 1920))
        assertEquals(2, cached.slideFiles.size)
        assertTrue(cached.slideFiles.all { it.isFile })
    }

    @Test
    fun `invalidate during a render disowns the writer instead of leaving it writing into nothing`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)

        cache.invalidate(source)

        assertFailsWith<SlideCacheSupersededException> {
            writer.putSlide(1, image(Color.RED), "", Fidelity.NATIVE, false)
        }
        assertNull(cache.lookup(source, 1920))
    }

    @Test
    fun `cleanupOrphaned leaves an in-flight render alone`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)

        cache.cleanupOrphaned(emptyList())

        writer.putSlide(1, image(Color.RED), "", Fidelity.NATIVE, false)
        writer.commit()
        assertEquals(2, assertNotNull(cache.lookup(source, 1920)).slideFiles.size)
    }

    @Test
    fun `putSlide recreates an entry directory deleted underneath it`() {
        val cache = newCache()
        val source = sourceFile()
        val writer = cache.beginWrite(source, DeckFormat.PPTX, 1920)
        writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
        cache.dirFor(source).deleteRecursively()

        val slide = writer.putSlide(1, image(Color.BLUE), "", Fidelity.NATIVE, false)
        assertTrue(slide.isFile)
    }

    @Test
    fun `an entry directory that cannot be created fails naming it`() {
        val base = File(tempDir, "readonly-cache").apply { mkdirs() }
        val probe = File(base, "probe")
        // Root, and some Windows setups, ignore the read-only bit — then there is nothing to test.
        assumeTrue(base.setWritable(false) && !probe.mkdir(), "cache dir stayed writable here")
        try {
            val cache = SlideDiskCache(base)
            val source = sourceFile()
            val failure = assertFailsWith<IOException> { cache.beginWrite(source, DeckFormat.PPTX, 1920) }
            assertTrue(
                cache.dirFor(source).absolutePath in (failure.message ?: ""),
                "message was: ${failure.message}"
            )
        } finally {
            base.setWritable(true)
        }
    }

    @Test
    fun `cleanupOrphaned keeps only listed sources`() {
        val cache = newCache()
        val keep = sourceFile()
        val drop = File(tempDir, "old.pptx").apply { writeText("stub") }
        for (f in listOf(keep, drop)) {
            val writer = cache.beginWrite(f, DeckFormat.PPTX, 1920)
            writer.putSlide(0, image(Color.RED), "", Fidelity.NATIVE, false)
            writer.commit()
        }
        cache.cleanupOrphaned(listOf(keep.absolutePath))
        assertNotNull(cache.lookup(keep, 1920))
        assertNull(cache.lookup(drop, 1920))
    }

    /**
     * A session-scoped `remote_*` entry is orphaned by definition, so the startup prune deletes it
     * unconditionally. The instance-link download fills that directory by hand rather than through
     * a [SlideDiskCache.Writer], so before [SlideDiskCache.claimDir] the prune could delete it
     * mid-download and every `renameTo` afterwards failed into a vanished parent — the load landed
     * zero slides and reported RENDER_FAILED.
     */
    @Test
    fun `cleanupOrphaned leaves a claimed download alone and deletes it once released`() {
        val base = File(tempDir, "cache")
        val cache = SlideDiskCache(base)
        val remote = File(base, "remote_item-3").apply { mkdirs() }
        val slide = File(remote, "slide_0000.jpg").apply { writeText("stub") }

        SlideDiskCache.claimDir(remote)
        cache.cleanupOrphaned(emptyList())
        assertTrue(slide.isFile, "a claimed download's slide was deleted underneath it")

        SlideDiskCache.releaseDir(remote)
        cache.cleanupOrphaned(emptyList())
        assertFalse(remote.exists(), "a released entry should be pruned")
    }

    /** Two loads of one schedule item overlap; the first to finish must not unprotect the second. */
    @Test
    fun `a claim held twice survives the first release`() {
        val base = File(tempDir, "cache")
        val cache = SlideDiskCache(base)
        val remote = File(base, "remote_item-9").apply { mkdirs() }
        val slide = File(remote, "slide_0000.jpg").apply { writeText("stub") }

        SlideDiskCache.claimDir(remote)
        SlideDiskCache.claimDir(remote)
        SlideDiskCache.releaseDir(remote)

        cache.cleanupOrphaned(emptyList())
        assertTrue(slide.isFile, "the second holder's directory was pruned")

        SlideDiskCache.releaseDir(remote)
        cache.cleanupOrphaned(emptyList())
        assertFalse(remote.exists(), "the last release should unprotect the entry")
    }
}
