package org.churchpresenter.presentationengine

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Reading a Keynote deck's words rather than its pixels — what the converter turns into songs.
 *
 * The object graph these fixtures build is the same one [KeynoteDeckParserTest] documents, taken
 * from a real deck dumped with the module's `dumpKeynote` tool: `Document(1) → Show(2) → nodes(4)
 * → slides(5)`, a text shape as `TSWP.ShapeInfoArchive(2011)` whose field 4 references a
 * `TSWP.StorageArchive(2001)` holding the strings. Fixtures stay programmatic — no binary deck is
 * committed, matching [Fixtures]' rule for these formats.
 *
 * Both container forms are covered, because Keynote writes both and `ObjectIndex.load` accepts
 * either: a package **directory**, and a **zip**.
 */
class KeynoteTextTest {

    private val temp: File = Files.createTempDirectory("keynote-text-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    // ── Graph builders, in the real deck's shape ──────────────────────────────

    private fun reference(id: Long): ByteArray =
        Fixtures.ProtoWriter().apply { varintField(1, id) }.toByteArray()

    private fun document(showId: Long): ByteArray =
        Fixtures.ProtoWriter().apply { bytesField(2, reference(showId)) }.toByteArray()

    private fun show(nodeIds: List<Long>): ByteArray {
        val size = Fixtures.ProtoWriter().apply { floatField(1, 1920f); floatField(2, 1080f) }.toByteArray()
        val tree = Fixtures.ProtoWriter().apply { nodeIds.forEach { bytesField(2, reference(it)) } }.toByteArray()
        return Fixtures.ProtoWriter().apply { bytesField(3, tree); bytesField(4, size) }.toByteArray()
    }

    private fun slideNode(slideId: Long): ByteArray =
        Fixtures.ProtoWriter().apply { bytesField(2, reference(slideId)) }.toByteArray()

    /** KN.SlideArchive owning [drawableIds] (field 7), with field 42 as the z-order. */
    private fun slideWithDrawables(drawableIds: List<Long>, noteId: Long? = null): ByteArray =
        Fixtures.ProtoWriter().apply {
            drawableIds.forEach { bytesField(7, reference(it)) }
            drawableIds.forEach { bytesField(42, reference(it)) }
            if (noteId != null) bytesField(27, reference(noteId))
        }.toByteArray()

    /** KN.NoteArchive: field 1 = TSP.Reference → the storage holding the note's text. */
    private fun note(storageId: Long): ByteArray =
        Fixtures.ProtoWriter().apply { bytesField(1, reference(storageId)) }.toByteArray()

    private fun geometry(x: Float, y: Float): ByteArray {
        val point = Fixtures.ProtoWriter().apply { floatField(1, x); floatField(2, y) }.toByteArray()
        val size = Fixtures.ProtoWriter().apply { floatField(1, 400f); floatField(2, 200f) }.toByteArray()
        return Fixtures.ProtoWriter().apply { bytesField(1, point); bytesField(2, size) }.toByteArray()
    }

    /** TSWP.ShapeInfoArchive — geometry hangs off ShapeArchive.super, one level deeper. */
    private fun textShape(x: Float, y: Float, storageId: Long): ByteArray {
        val drawableArchive = Fixtures.ProtoWriter().apply { bytesField(1, geometry(x, y)) }.toByteArray()
        val shapeArchive = Fixtures.ProtoWriter().apply { bytesField(1, drawableArchive) }.toByteArray()
        return Fixtures.ProtoWriter().apply {
            bytesField(1, shapeArchive)
            bytesField(4, reference(storageId))
        }.toByteArray()
    }

    /** TSD.GroupArchive: field 1 = TSD.DrawableArchive, field 2 = repeated child references. */
    private fun group(x: Float, y: Float, childIds: List<Long>): ByteArray {
        val drawableArchive = Fixtures.ProtoWriter().apply { bytesField(1, geometry(x, y)) }.toByteArray()
        return Fixtures.ProtoWriter().apply {
            bytesField(1, drawableArchive)
            childIds.forEach { bytesField(2, reference(it)) }
        }.toByteArray()
    }

    /** TSWP.StorageArchive: field 3 = repeated string. */
    private fun storage(vararg text: String): ByteArray =
        Fixtures.ProtoWriter().apply { text.forEach { stringField(3, it) } }.toByteArray()

    private fun deck(vararg objects: Triple<Long, Int, ByteArray>): File =
        Fixtures.writeKeynoteDir(Files.createTempDirectory(temp.toPath(), "deck").toFile(), objects.toList())

    /** A one-slide deck whose slide owns [drawables], optionally with speaker notes. */
    private fun oneSlideDeck(
        vararg drawables: Triple<Long, Int, ByteArray>,
        extras: List<Triple<Long, Int, ByteArray>> = emptyList(),
        noteId: Long? = null,
    ): File = deck(
        Triple(1L, 1, document(2L)),
        Triple(2L, 2, show(listOf(100L))),
        Triple(100L, 4, slideNode(200L)),
        Triple(200L, 5, slideWithDrawables(drawables.map { it.first }, noteId)),
        *drawables,
        *extras.toTypedArray(),
    )

    // ── The modern path ───────────────────────────────────────────────────────

    @Test
    fun `a slide's text box is read`() {
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(100f, 100f, 400L)),
            extras = listOf(Triple(400L, 2001, storage("Amazing grace how sweet the sound"))),
        )
        assertEquals(listOf("Amazing grace how sweet the sound"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `every slide is read, not just the first`() {
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(listOf(100L, 101L, 102L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(101L, 4, slideNode(201L)),
            Triple(102L, 4, slideNode(202L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(201L, 5, slideWithDrawables(listOf(301L))),
            Triple(202L, 5, slideWithDrawables(listOf(302L))),
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            Triple(301L, 2011, textShape(0f, 0f, 401L)),
            Triple(302L, 2011, textShape(0f, 0f, 402L)),
            Triple(400L, 2001, storage("Verse one")),
            Triple(401L, 2001, storage("Chorus")),
            Triple(402L, 2001, storage("Verse two")),
        )
        assertContentEquals(listOf("Verse one", "Chorus", "Verse two"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `a deck saved as a zip reads the same as a package directory`() {
        // Keynote writes both forms; ObjectIndex.load accepts either, and neither may be favoured.
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            Triple(400L, 2001, storage("Sung the same either way")),
        )
        val zip = File(temp, "zipped.key")
        java.util.zip.ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Index/Test.iwa"))
            out.write(Fixtures.buildIwa(objects))
            out.closeEntry()
        }
        assertEquals(listOf("Sung the same either way"), KeynoteText.slideTexts(zip))
    }

    @Test
    fun `paragraphs of one text box become separate lines`() {
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            // A real deck stores a paragraph break as a lone carriage return.
            extras = listOf(Triple(400L, 2001, storage("Amazing grace\rHow sweet the sound"))),
        )
        assertEquals(listOf("Amazing grace\nHow sweet the sound"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `text boxes are read top-to-bottom, not in the order they were created`() {
        // The parser hands drawables back in z-order — creation order — so a title typed after the
        // verse beneath it arrives second. Position is what makes the slide read as it looks.
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(0f, 500f, 400L)),
            Triple(301L, 2011, textShape(0f, 50f, 401L)),
            extras = listOf(
                Triple(400L, 2001, storage("the lower verse")),
                Triple(401L, 2001, storage("THE TITLE")),
            ),
        )
        assertEquals(listOf("THE TITLE\nthe lower verse"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `two boxes on one line are read left-to-right`() {
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(900f, 100f, 400L)),
            Triple(301L, 2011, textShape(100f, 100f, 401L)),
            extras = listOf(
                Triple(400L, 2001, storage("right")),
                Triple(401L, 2001, storage("left")),
            ),
        )
        assertEquals(listOf("left\nright"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `grouped text is included, positioned by the group's offset`() {
        // A group's children are stored relative to its origin. Without the parent offset the
        // grouped line below would sort as if it sat at y=10 and lead the slide.
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(0f, 100f, 400L)),
            Triple(301L, 3008, group(0f, 600f, listOf(302L))),
            extras = listOf(
                Triple(302L, 2011, textShape(0f, 10f, 402L)),
                Triple(400L, 2001, storage("first")),
                Triple(402L, 2001, storage("grouped and last")),
            ),
        )
        assertEquals(listOf("first\ngrouped and last"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `speaker notes are left out`() {
        // They are the presenter's words, not the audience's — a song must not gain them as verses.
        val file = oneSlideDeck(
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            extras = listOf(
                Triple(400L, 2001, storage("The sung line")),
                Triple(500L, 15, note(501L)),
                Triple(501L, 2001, storage("Remember to slow down here")),
            ),
            noteId = 500L,
        )
        val text = KeynoteText.slideTexts(file).single()
        assertEquals("The sung line", text)
        assertFalse(text.contains("slow down"), "the note is not part of the slide")
    }

    @Test
    fun `a slide with no text at all comes back blank rather than missing`() {
        // One entry per slide is the contract: the converter relies on the count to split sections.
        val file = deck(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(listOf(100L, 101L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(101L, 4, slideNode(201L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(201L, 5, slideWithDrawables(emptyList())),
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            Triple(400L, 2001, storage("Only slide one speaks")),
        )
        assertEquals(listOf("Only slide one speaks", ""), KeynoteText.slideTexts(file))
    }

    // ── The preview-PDF fallback ──────────────────────────────────────────────

    @Test
    fun `a deck that cannot be parsed natively falls back to its embedded preview PDF`() {
        // What an iWork '09 or password-protected document looks like here: readable container,
        // no usable IWA body, but the QuickLook preview Keynote always embeds.
        val previewBytes = Fixtures.createPdf(temp, pages = 2, name = "preview.pdf").readBytes()
        val file = Fixtures.createKeynoteZip(
            dir = temp,
            previewPdf = previewBytes,
            thumbnails = emptyMap(),
            slideIwaIds = listOf(1L, 2L),
        )
        val texts = KeynoteText.slideTexts(file)
        assertEquals(2, texts.size, "one entry per preview page")
        assertTrue(texts[0].contains("Page 1"), "got: '${texts[0]}'")
        assertTrue(texts[1].contains("Page 2"), "later pages are not dropped")
    }

    @Test
    fun `the native text wins when both it and a preview are present`() {
        val objects = listOf(
            Triple(1L, 1, document(2L)),
            Triple(2L, 2, show(listOf(100L))),
            Triple(100L, 4, slideNode(200L)),
            Triple(200L, 5, slideWithDrawables(listOf(300L))),
            Triple(300L, 2011, textShape(0f, 0f, 400L)),
            Triple(400L, 2001, storage("The real lyrics")),
        )
        val previewBytes = Fixtures.createPdf(temp, pages = 1, name = "both-preview.pdf").readBytes()
        val file = File(temp, "both.key")
        java.util.zip.ZipOutputStream(file.outputStream()).use { out ->
            out.putNextEntry(java.util.zip.ZipEntry("Index/Test.iwa"))
            out.write(Fixtures.buildIwa(objects))
            out.closeEntry()
            out.putNextEntry(java.util.zip.ZipEntry("QuickLook/Preview.pdf"))
            out.write(previewBytes)
            out.closeEntry()
        }
        assertEquals(listOf("The real lyrics"), KeynoteText.slideTexts(file))
    }

    @Test
    fun `a deck with neither readable IWA nor a preview yields nothing`() {
        val file = Fixtures.createKeynoteZip(
            dir = temp,
            previewPdf = null,
            thumbnails = emptyMap(),
            slideIwaIds = listOf(1L),
            name = "bare.key",
        )
        assertEquals(emptyList(), KeynoteText.slideTexts(file))
    }

    // ── Refusing to throw ─────────────────────────────────────────────────────

    @Test
    fun `a damaged file returns nothing rather than throwing`() {
        // Users do drag in half-downloaded files; the caller is documented never to see an exception.
        val file = File(temp, "broken.key").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        assertEquals(emptyList(), KeynoteText.slideTexts(file))
    }

    @Test
    fun `a file that does not exist returns nothing rather than throwing`() {
        assertEquals(emptyList(), KeynoteText.slideTexts(File(temp, "absent.key")))
    }

    @Test
    fun `a preview that is not a readable PDF returns nothing rather than throwing`() {
        val file = Fixtures.createKeynoteZip(
            dir = temp,
            previewPdf = byteArrayOf(9, 9, 9, 9),
            thumbnails = emptyMap(),
            slideIwaIds = listOf(1L),
            name = "bad-preview.key",
        )
        assertEquals(emptyList(), KeynoteText.slideTexts(file))
    }
}
