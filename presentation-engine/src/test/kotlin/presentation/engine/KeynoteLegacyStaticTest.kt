package presentation.engine

import presentation.engine.keynote.KeynoteStaticSupport
import java.awt.Color
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Static extraction from a **legacy (pre-IWA) Keynote document** — the `index.apxl` format Keynote
 * '09 and earlier wrote.
 *
 * Such a document puts its per-slide thumbnails in `thumbs/` as `st<n>.jpg` / `st<n>-<m>.jpg`,
 * which matches neither the `Data/` location nor the `st-` prefix the modern rule looks for. The
 * result was that a legacy deck reported *no* thumbnails and failed to open at all — with a
 * message saying there were none, while nine sat in the archive.
 *
 * Their names cannot be sorted into slide order: a real document runs
 * `st2-1, st2-2, st3, st4, st5, st2, st7, st6, st186`, where `st2` is the sixth slide and `st186`
 * the ninth. The apxl states the mapping itself, in document order, so that is what is read — the
 * fixtures below reproduce that exact shape, taken from a real Keynote '09 document.
 */
class KeynoteLegacyStaticTest {

    private val temp: File = Files.createTempDirectory("keynote-legacy-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** `<key:slide>` with its own thumbnail reference and notes, as the real format nests them. */
    private fun slideXml(thumbPath: String, notes: String = ""): String = """
        <key:slide sfa:ID="BGSlide-1" key:depth="0">
          <key:page sfa:ID="page-1">
            <sf:drawables><sf:media><sf:content><sf:image-media>
              <sf:filtered-image><sf:unfiltered>
                <sf:data sfa:ID="SFEData-99" sf:path="a-photo-on-the-slide.jpg"/>
              </sf:unfiltered></sf:filtered-image>
            </sf:image-media></sf:content></sf:media></sf:drawables>
          </key:page>
          <key:thumbnails sfa:ID="NSMutableArray-1">
            <key:binary sfa:ID="SFRImageBinary-1">
              <sf:size sfa:w="180" sfa:h="102"/>
              <sf:data sfa:ID="SFEData-1" sf:path="$thumbPath" sf:displayname="thumbs/display.jpg"/>
            </key:binary>
          </key:thumbnails>
          <key:notes><sf:text><sf:p>$notes</sf:p></sf:text></key:notes>
        </key:slide>
    """.trimIndent()

    // The XML declaration must be the very first characters — a leading newline makes the
    // document unparseable, which is silent here because the parser is deliberately lenient.
    private fun apxl(vararg slides: String): String = """<?xml version="1.0"?>
        <key:presentation xmlns:sfa="http://developer.apple.com/namespaces/sfa"
                          xmlns:sf="http://developer.apple.com/namespaces/sf"
                          xmlns:key="http://developer.apple.com/namespaces/keynote2">
          <key:size sfa:w="1280" sfa:h="720"/>
          <key:master-slide sfa:ID="BGMaster-0">
            <key:thumbnails><key:binary>
              <sf:data sfa:ID="SFEData-m" sf:path="thumbs/mt0-0.jpg"/>
            </key:binary></key:thumbnails>
          </key:master-slide>
          <key:slide-list sfa:ID="BGSlideList-0">${slides.joinToString("\n")}</key:slide-list>
        </key:presentation>
    """.trimIndent()

    /** A legacy `.key` zip: an apxl manifest plus whichever thumbnail files [present] names. */
    private fun legacyKey(apxlXml: String, present: List<String>, name: String = "legacy.key"): File {
        val file = File(temp, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("index.apxl"))
            zip.write(apxlXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (entry in present) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write(Fixtures.jpegBytes(180, 102, Color.BLUE))
                zip.closeEntry()
            }
        }
        return file
    }

    // ── The order the document declares ───────────────────────────────────────

    @Test
    fun `thumbnails come back in the order the document declares, not name order`() {
        // Exactly the sequence a real Keynote '09 deck produced. Sorting by name would put st2
        // first and st186 sixth; both are wrong.
        val names = listOf(
            "thumbs/st2-1.jpg", "thumbs/st2-2.jpg", "thumbs/st3.jpg", "thumbs/st4.jpg",
            "thumbs/st5.jpg", "thumbs/st2.jpg", "thumbs/st7.jpg", "thumbs/st6.jpg", "thumbs/st186.jpg",
        )
        val file = legacyKey(apxl(*names.map { slideXml(it) }.toTypedArray()), names)

        assertEquals(names, KeynoteStaticSupport.analyze(file).orderedThumbnailEntries)
    }

    @Test
    fun `a legacy document with thumbnails is no longer reported as having none`() {
        val names = listOf("thumbs/st2.jpg", "thumbs/st3.jpg")
        val file = legacyKey(apxl(*names.map { slideXml(it) }.toTypedArray()), names)

        val analysis = KeynoteStaticSupport.analyze(file)
        assertTrue(!analysis.hasPreviewPdf, "legacy documents carry no preview PDF")
        assertTrue(analysis.orderedThumbnailEntries.isNotEmpty(), "but they do carry thumbnails")
    }

    @Test
    fun `the master slide's thumbnail is not mistaken for a slide`() {
        // Masters are decorations, not slides; including one would add a phantom slide.
        val names = listOf("thumbs/st2.jpg")
        val file = legacyKey(apxl(slideXml("thumbs/st2.jpg")), names + "thumbs/mt0-0.jpg")

        assertEquals(names, KeynoteStaticSupport.analyze(file).orderedThumbnailEntries)
    }

    @Test
    fun `a photo on the slide is not mistaken for the slide's thumbnail`() {
        // A slide's page content carries its own sf:data references; taking the first one found
        // anywhere in the slide would hand back a photo instead of the thumbnail.
        val file = legacyKey(
            apxl(slideXml("thumbs/st9.jpg")),
            listOf("thumbs/st9.jpg", "a-photo-on-the-slide.jpg"),
        )
        assertEquals(listOf("thumbs/st9.jpg"), KeynoteStaticSupport.analyze(file).orderedThumbnailEntries)
    }

    @Test
    fun `a declared thumbnail the archive does not contain is skipped`() {
        // A stale reference must not become a blank slide in the middle of the deck.
        val file = legacyKey(
            apxl(slideXml("thumbs/st2.jpg"), slideXml("thumbs/missing.jpg"), slideXml("thumbs/st4.jpg")),
            listOf("thumbs/st2.jpg", "thumbs/st4.jpg"),
        )
        assertEquals(
            listOf("thumbs/st2.jpg", "thumbs/st4.jpg"),
            KeynoteStaticSupport.analyze(file).orderedThumbnailEntries,
        )
    }

    @Test
    fun `notes still come back alongside the thumbnails`() {
        val file = legacyKey(
            apxl(slideXml("thumbs/st2.jpg", notes = "Opening remarks"), slideXml("thumbs/st3.jpg", notes = "Reading")),
            listOf("thumbs/st2.jpg", "thumbs/st3.jpg"),
        )
        val analysis = KeynoteStaticSupport.analyze(file)
        assertEquals(listOf("Opening remarks", "Reading"), analysis.notes)
        assertEquals(2, analysis.orderedThumbnailEntries.size)
    }

    @Test
    fun `a document with no thumbnails at all still reports none`() {
        val file = legacyKey(apxl(), emptyList())
        assertTrue(KeynoteStaticSupport.analyze(file).orderedThumbnailEntries.isEmpty())
    }

    @Test
    fun `a malformed manifest yields nothing rather than throwing`() {
        val file = legacyKey("<key:presentation><not closed", listOf("thumbs/st2.jpg"))
        assertTrue(KeynoteStaticSupport.analyze(file).orderedThumbnailEntries.isEmpty())
    }

    // ── The modern path is untouched ──────────────────────────────────────────

    @Test
    fun `a modern document still uses its own Data thumbnails`() {
        // The legacy branch is only consulted when the modern rule found nothing, so a deck
        // carrying both must behave exactly as it did before.
        val file = File(temp, "modern.key")
        ZipOutputStream(file.outputStream()).use { zip ->
            for (id in listOf(1L, 2L)) {
                zip.putNextEntry(ZipEntry("Index/Slide-$id.iwa")); zip.write(ByteArray(4)); zip.closeEntry()
            }
            for (n in listOf(1, 2)) {
                zip.putNextEntry(ZipEntry("Data/st-UUID-$n.jpg"))
                zip.write(Fixtures.jpegBytes(180, 102, Color.RED))
                zip.closeEntry()
            }
            // A legacy manifest alongside it must not win.
            zip.putNextEntry(ZipEntry("index.apxl"))
            zip.write(apxl(slideXml("thumbs/st99.jpg")).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("thumbs/st99.jpg"))
            zip.write(Fixtures.jpegBytes(180, 102, Color.GREEN))
            zip.closeEntry()
        }

        val entries = KeynoteStaticSupport.analyze(file).orderedThumbnailEntries
        assertTrue(entries.all { it.startsWith("Data/") }, "the modern thumbnails won: $entries")
        assertEquals(2, entries.size)
    }
}
