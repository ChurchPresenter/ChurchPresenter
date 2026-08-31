package org.churchpresenter.converter.song

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The folder scan behind "select folder".
 *
 * The case that matters most here is a `.key` **package directory**. It was previously pruned with
 * `walkTopDown().onEnter { !isPackageInput(it) }`, which does not mean "yield but do not descend" —
 * it drops the directory entirely — so every bundle vanished from the results and the feature
 * returned nothing at all.
 */
class InputDiscoveryTest {

    private fun tempDir(): File = Files.createTempDirectory("discovery").toFile()

    private fun bundle(parent: File, name: String): File =
        File(parent, name).apply {
            mkdirs()
            File(this, "Index").mkdirs()
            File(this, "Index/Document.iwa").writeText("payload")
            File(this, "Metadata").mkdirs()
        }

    @Test
    fun `a keynote package directory is taken whole`() {
        val dir = tempDir()
        val deck = bundle(dir, "service.key")

        val found = findFormatInputs(dir, DocumentFormat)

        assertEquals(listOf(deck.absolutePath), found.map { it.absolutePath })
        assertTrue(found.single().isDirectory, "the bundle itself is the input, not a file inside it")
    }

    @Test
    fun `a package's innards are never walked into`() {
        val dir = tempDir()
        bundle(dir, "service.key")

        val found = findFormatInputs(dir, DocumentFormat)

        assertTrue(found.none { it.name.endsWith(".iwa") }, "walked into the bundle: $found")
        assertTrue(found.none { it.name == "Index" }, "walked into the bundle: $found")
    }

    @Test
    fun `packages and plain files are found together, at any depth`() {
        val dir = tempDir()
        val top = bundle(dir, "top.key")
        val nestedDir = File(dir, "nested").apply { mkdirs() }
        val deep = bundle(nestedDir, "deep.key")
        val deck = File(nestedDir, "slides.pptx").apply { writeText("x") }
        File(dir, "notes.txt").writeText("ignored")

        val found = findFormatInputs(dir, DocumentFormat).map { it.absolutePath }

        assertEquals(listOf(deep, deck, top).map { it.absolutePath }.sorted(), found)
    }

    @Test
    fun `a directory named for a non-package extension stays a folder to search`() {
        val dir = tempDir()
        val decoy = File(dir, "archive.pptx").apply { mkdirs() }
        val real = File(decoy, "inner.pptx").apply { writeText("x") }

        val found = findFormatInputs(dir, DocumentFormat)

        assertEquals(listOf(real.absolutePath), found.map { it.absolutePath })
    }

    @Test
    fun `every one of a format's extensions is matched, not just the first`() {
        val dir = tempDir()
        val pdf = File(dir, "a.pdf").apply { writeText("x") }
        val docx = File(dir, "b.docx").apply { writeText("x") }
        val ppt = File(dir, "c.ppt").apply { writeText("x") }

        val found = findFormatInputs(dir, DocumentFormat).map { it.name }

        assertTrue(found.containsAll(listOf(pdf.name, docx.name, ppt.name)), "got $found")
    }

    @Test
    fun `a package extension is only a package for a format that reads it`() {
        val dir = tempDir()
        bundle(dir, "service.key")

        // SoftProjector does not read .key, so the bundle is just a directory to search.
        val found = findFormatInputs(dir, SoftProjectorFormat)

        assertTrue(found.none { it.name == "service.key" }, "claimed a bundle it cannot read: $found")
    }

    @Test
    fun `a bundle's size is the sum of its contents, not the directory entry`() {
        val dir = tempDir()
        val deck = bundle(dir, "service.key")

        assertEquals("payload".length.toLong(), inputSize(deck))
    }

    @Test
    fun `an unreadable directory yields nothing rather than throwing`() {
        val missing = File(tempDir(), "gone")

        assertEquals(emptyList(), findFormatInputs(missing, DocumentFormat))
    }
}
