package converter.song

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.sql.DriverManager
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Each entry in the source rail driven the way the panel drives it: `describe` for the preview
 * list, `convert` for the run, and `outputNameFor` for what lands on disk.
 *
 * The registry is the only place that knows a format needs an output folder, writes one file or a
 * folder of them, or answers to two different kinds of input under one name — none of which the
 * individual converters can be asked about. A format that reads perfectly and is wired up wrong
 * here fails in the panel and nowhere else.
 */
class SongFormatAdaptersTest {

    private val temp: File = Files.createTempDirectory("converter-format-adapters").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    private fun out(name: String): File = File(temp, name).apply { mkdirs() }

    private fun file(name: String, body: String): File =
        File(temp, name).apply { parentFile.mkdirs(); writeText(body, Charsets.UTF_8) }

    // ── SongBeamer ────────────────────────────────────────────────────────────

    private fun sng(name: String) = file(
        name,
        "#Title=Amazing Grace\n#Author=John Newton\n#VerseOrder=Verse 1\n---\nVerse 1\nAmazing grace\n",
    )

    @Test
    fun `SongBeamer describes and converts one file at a time`() {
        val input = sng("grace.sng")
        assertEquals(SongPreviewInfo("Amazing Grace", sectionCount = 1, verseOrder = listOf("Verse 1")),
            SongBeamerFormat.describe(input))
        assertEquals("grace.song", SongBeamerFormat.outputNameFor(input))
        assertTrue(SongBeamerFormat.convert(input, out("sb")).outputFiles.single().exists())
    }

    // ── Free Worship / OpenLyrics ─────────────────────────────────────────────

    private fun openLyrics(name: String, title: String = "Amazing Grace") = file(
        name,
        """<?xml version="1.0" encoding="utf-8"?>
           <song version="0.8" xmlns="http://openlyrics.info/namespace/2009/song">
             <properties><titles><title>$title</title></titles><verseOrder>v1</verseOrder></properties>
             <lyrics><verse name="v1"><lines>Amazing grace how sweet the sound</lines></verse></lyrics>
           </song>""",
    )

    @Test
    fun `Free Worship writes one song beside its input when no folder is given`() {
        val input = openLyrics("freeworship/grace.xml")
        assertEquals("Amazing Grace", FreeWorshipFormat.describe(input).title)

        val written = FreeWorshipFormat.convert(input, null).outputFiles.single()
        assertEquals(input.parentFile, written.parentFile)
        assertEquals(FreeWorshipFormat.outputNameFor(input), written.name)
        assertTrue(written.readText().contains("Amazing grace how sweet the sound"))
    }

    // ── OpenLP, which is two formats under one name ───────────────────────────

    private fun openLpDatabase(name: String): File {
        val file = File(temp, name)
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "CREATE TABLE songs (id INTEGER PRIMARY KEY, title TEXT, lyrics TEXT, " +
                        "verse_order TEXT, copyright TEXT, ccli_number TEXT)"
                )
                statement.executeUpdate(
                    "INSERT INTO songs VALUES (1, 'Amazing Grace', " +
                        "'<song version=\"1.0\"><lyrics><verse type=\"v\" label=\"1\">" +
                        "<![CDATA[Amazing grace]]></verse></lyrics></song>', '', 'Public Domain', '22025')"
                )
            }
        }
        return file
    }

    @Test
    fun `OpenLP reads its database into a folder of songs`() {
        val input = openLpDatabase("songs.sqlite")
        assertEquals(1, OpenLpFormat.describe(input).songCount)
        assertEquals("songs", OpenLpFormat.outputNameFor(input))
        assertTrue(OpenLpFormat.convert(input, out("openlp-db")).outputFiles.isNotEmpty())
    }

    @Test
    fun `OpenLP reads an OpenLyrics export as a single song instead`() {
        val input = openLyrics("openlp/export.xml", title = "Be Thou My Vision")
        assertEquals("Be Thou My Vision", OpenLpFormat.describe(input).title)
        assertEquals("export.song", OpenLpFormat.outputNameFor(input))
        assertEquals("export.song", OpenLpFormat.convert(input, out("openlp-xml")).outputFiles.single().name)
    }

    @Test
    fun `OpenLP refuses to run without somewhere to put the songs`() {
        assertFailsWith<IllegalArgumentException> { OpenLpFormat.convert(openLpDatabase("nowhere.sqlite"), null) }
    }

    // ── OpenSong ──────────────────────────────────────────────────────────────

    @Test
    fun `OpenSong reads a file with no extension at all`() {
        val input = file(
            "opensong/Amazing Grace",
            "<song><title>Amazing Grace</title><presentation>V1</presentation>" +
                "<lyrics>[V1]\n Amazing grace\n</lyrics></song>",
        )
        assertTrue(OpenSongFormat.acceptsExtensionlessFiles)
        assertEquals("Amazing Grace", OpenSongFormat.describe(input).title)
        assertEquals("Amazing Grace.song", OpenSongFormat.outputNameFor(input))
        assertTrue(OpenSongFormat.convert(input, out("opensong-out")).outputFiles.single().exists())
    }

    @Test
    fun `an OpenSong file with no title of its own is named after itself`() {
        val input = file("opensong/Untitled Song", "<song><lyrics>[V1]\n A line\n</lyrics></song>")
        assertEquals("Untitled Song", OpenSongFormat.describe(input).title)
    }

    // ── FreeShow ──────────────────────────────────────────────────────────────

    private fun show(name: String, title: String = "Amazing Grace") = file(
        name,
        """["show-id",{"name":"$title","settings":{"activeLayout":"L1"},"meta":{},
           "slides":{"a":{"group":"Verse 1","items":[{"type":"text",
           "lines":[{"align":"","text":[{"value":"Amazing grace","style":""}]}]}]}},
           "layouts":{"L1":{"name":"Default","slides":[{"id":"a"}]}}}]""",
    )

    @Test
    fun `FreeShow converts one show into one song`() {
        val input = show("freeshow/grace.show")
        assertEquals(SongPreviewInfo("Amazing Grace", sectionCount = 1), FreeShowFormat.describe(input))
        assertEquals("grace.song", FreeShowFormat.outputNameFor(input))
        assertTrue(FreeShowFormat.convert(input, out("freeshow-out")).outputFiles.single().exists())
    }

    @Test
    fun `a show with no name of its own falls back to the file name`() {
        assertEquals("Untitled", FreeShowFormat.describe(show("freeshow/Untitled.show", title = "")).title)
    }

    // ── EasySlides ────────────────────────────────────────────────────────────

    private fun easySlides(name: String) = file(
        name,
        "<EasiSlides><Item><Title1>Amazing Grace</Title1><SongNumber>1</SongNumber>" +
            "<Sequence>1</Sequence><Contents>[V1]\nAmazing grace</Contents></Item>" +
            "<Item><Title1>Be Thou My Vision</Title1><Contents>[V1]\nBe thou my vision</Contents></Item></EasiSlides>",
    )

    @Test
    fun `EasySlides describes the whole library and fans it out into a folder`() {
        val input = easySlides("easyslides/export.xml")
        val preview = EasySlidesFormat.describe(input)
        assertEquals("Amazing Grace", preview.title)
        assertEquals(2, preview.songCount)
        assertEquals("export", EasySlidesFormat.outputNameFor(input))
        assertEquals(2, EasySlidesFormat.convert(input, out("easyslides-out")).outputFiles.size)
    }

    @Test
    fun `EasySlides refuses to run without an output folder`() {
        assertFailsWith<IllegalArgumentException> { EasySlidesFormat.convert(easySlides("easyslides/e2.xml"), null) }
    }

    // ── Quelea ────────────────────────────────────────────────────────────────

    private fun queleaSong(title: String) =
        "<song><title>$title</title><author>John Newton</author><sequence>v1</sequence>" +
            "<lyrics><section title=\"Verse 1\"><lyrics>Amazing grace</lyrics></section></lyrics></song>"

    private fun queleaPack(name: String): File =
        File(temp, name).apply {
            parentFile.mkdirs()
            ZipOutputStream(outputStream()).use { zip ->
                listOf("Amazing Grace", "Be Thou My Vision").forEachIndexed { i, title ->
                    zip.putNextEntry(ZipEntry("$i.xml"))
                    zip.write(queleaSong(title).toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun `Quelea describes a pack by its first song and its size`() {
        val input = queleaPack("quelea/songs.qsp")
        val preview = QueleaFormat.describe(input)
        assertEquals("Amazing Grace", preview.title)
        assertEquals(2, preview.songCount)
        assertEquals("songs", QueleaFormat.outputNameFor(input))
        assertEquals(2, QueleaFormat.convert(input, out("quelea-out")).outputFiles.size)
    }

    @Test
    fun `Quelea reads a loose song file the same way`() {
        val input = file("quelea/one.xml", queleaSong("Amazing Grace"))
        assertEquals(1, QueleaFormat.describe(input).songCount)
    }

    @Test
    fun `Quelea refuses to run without an output folder`() {
        assertFailsWith<IllegalArgumentException> { QueleaFormat.convert(queleaPack("quelea/p2.qsp"), null) }
    }

    // ── SoftProjector ─────────────────────────────────────────────────────────

    private fun sps(name: String) = file(
        name,
        "##SoftProjector\n##Hymns of Grace\n1#\$#Amazing Grace#\$#x#\$#tune#\$#John Newton#\$#composer#\$#Amazing grace\n",
    )

    @Test
    fun `SoftProjector describes the songbook and writes its songs into a folder`() {
        val input = sps("softprojector/book.sps")
        val preview = SoftProjectorFormat.describe(input)
        assertEquals("Hymns of Grace", preview.title)
        assertEquals(1, preview.songCount)
        assertEquals("Hymns of Grace", SoftProjectorFormat.outputNameFor(input))

        val result = SoftProjectorFormat.convert(input, out("sps-out"))
        assertTrue(result.outputFiles.single().name.endsWith(".song"))
    }

    @Test
    fun `SoftProjector refuses to run without an output folder`() {
        assertFailsWith<IllegalArgumentException> { SoftProjectorFormat.convert(sps("softprojector/b2.sps"), null) }
    }

    // ── MediaShout ────────────────────────────────────────────────────────────

    private fun mediaShoutScript(name: String): File {
        val rtf = """{\rtf1\ansi\pard Amazing grace}""".replace("\\", "\\\\")
        val model = """{"Cues":[{"Properties":{"Name":"Amazing Grace","Type":1},
            |"Pages":[{"Properties":{"Name":"Verse 1"},"Items":[{"TypeId":"VisualItem+Text",
            |"Properties":{"Text":"$rtf"}}]}]}]}""".trimMargin().replace("\n", "")
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { stream ->
                stream.putNextEntry(ZipEntry("scriptModel.json"))
                stream.write(model.toByteArray(Charsets.UTF_8))
                stream.closeEntry()
            }
        }.toByteArray()
        val header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
        header.put("sc7x".toByteArray(Charsets.US_ASCII))
        header.putInt(20).putInt(0).putInt(20).putInt(zip.size)
        return File(temp, name).apply { parentFile.mkdirs(); writeBytes(header.array() + zip) }
    }

    @Test
    fun `MediaShout describes a script by the number of songs in the service`() {
        val input = mediaShoutScript("mediashout/service.sc7x")
        assertEquals(1, MediaShoutFormat.describe(input).songCount)
        assertEquals("service", MediaShoutFormat.outputNameFor(input))
        assertEquals(listOf("Amazing Grace.song"),
            MediaShoutFormat.convert(input, out("ms-out")).outputFiles.map { it.name })
    }

    @Test
    fun `MediaShout refuses to run without an output folder`() {
        assertFailsWith<IllegalArgumentException> {
            MediaShoutFormat.convert(mediaShoutScript("mediashout/s2.sc7x"), null)
        }
    }

    // ── ProPresenter ──────────────────────────────────────────────────────────

    @Test
    fun `ProPresenter converts a version 6 document into one song`() {
        val input = File(javaClass.classLoader.getResource("propresenter/v6-amazing-grace.pro6")!!.toURI())
        assertEquals("Amazing Grace", ProPresenterFormat.describe(input).title)
        assertEquals("v6-amazing-grace.song", ProPresenterFormat.outputNameFor(input))

        val written = ProPresenterFormat.convert(input, out("pro-out")).outputFiles.single()
        assertTrue(written.readText().contains("Amazing grace how sweet the sound"))
    }

    // ── Documents ─────────────────────────────────────────────────────────────

    @Test
    fun `a document that cannot be read reports why instead of writing nothing quietly`() {
        val notAPdf = file("documents/notes.pdf", "this is not a PDF at all")
        val result = DocumentFormat.convert(notAPdf, out("doc-out"))
        assertTrue(result.outputFiles.isEmpty())
        assertTrue(result.errors.isNotEmpty(), "expected the failure to be reported")
    }

    @Test
    fun `documents refuse to run without an output folder`() {
        assertFailsWith<IllegalArgumentException> {
            DocumentFormat.convert(file("documents/n2.pdf", "not a PDF"), null)
        }
    }
}
