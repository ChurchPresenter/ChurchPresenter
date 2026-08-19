package engine

import engine.bible.Script
import engine.bible.SpbLoader
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SpbLoaderFixtureTest {

    private lateinit var root: File
    private lateinit var savedRoot: String

    @BeforeTest
    fun useSyntheticBibles() {
        root = Files.createTempDirectory("spb-fixture").toFile()
        savedRoot = Config.bibleRoot
        Config.bibleRoot = root.absolutePath
    }

    @AfterTest
    fun restore() {
        Config.bibleRoot = savedRoot
        root.deleteRecursively()
    }

    private fun spb(name: String, title: String, abbrev: String, body: String) {
        File(root, name).writeText(
            """
            |##Title:$title
            |##Abbreviation:$abbrev
            |40${'\t'}Matthew${'\t'}28
            |-----
            |$body
            """.trimMargin(),
            Charsets.UTF_8,
        )
    }

    private fun verse(code: String, b: Int, c: Int, v: Int, text: String) =
        "$code\t$b\t$c\t$v\t$text"

    /** loadAll() discards a translation with fewer than 10 verses, so every fixture carries ballast. */
    private fun pad() = (1..12).joinToString("\n") { verse("B040C009V%03d".format(it), 40, 9, it, "filler $it") }

    @Test
    fun `a synthetic spb loads with its title, abbreviation and verses`() {
        spb("ENG_TST.spb", "Test Version", "TST", verse("B040C001V001", 40, 1, 1, "In the beginning") + "\n" + pad())

        val t = assertNotNull(SpbLoader.loadAll().firstOrNull())

        assertEquals("Test Version", t.title)
        assertEquals("TST", t.abbreviation)
        assertEquals("In the beginning", t.lookupVerse(40, 1, 1)?.text)
    }

    @Test
    fun `a file with no abbreviation is rejected`() {
        File(root, "broken.spb").writeText("##Title:No Abbrev\n-----\n", Charsets.UTF_8)

        assertTrue(SpbLoader.loadAll().isEmpty())
    }

    @Test
    fun `the book manifest is read from before the separator`() {
        spb("ENG_TST.spb", "Test", "TST", verse("B040C001V001", 40, 1, 1, "text") + "\n" + pad())

        val t = assertNotNull(SpbLoader.loadAll().firstOrNull())

        assertEquals(listOf(40 to "Matthew"), t.books.map { it.num to it.name })
    }

    @Test
    fun `a verse numbered zero is treated as a header`() {
        spb("ENG_TST.spb", "Test", "TST", verse("B040C001V000", 40, 1, 0, "Chapter heading") + "\n" + pad())

        val t = assertNotNull(SpbLoader.loadAll().firstOrNull())

        assertTrue(t.lookupVerse(40, 1, 0)?.isHeader == true)
    }

    @Test
    fun `malformed verse lines are skipped rather than failing the load`() {
        spb(
            "ENG_TST.spb", "Test", "TST",
            listOf(
                "not-a-verse-line",
                "B040\tx\t1\t1\tbad book number",
                verse("B040C001V001", 40, 1, 1, "good"),
                pad(),
            ).joinToString("\n"),
        )

        val t = assertNotNull(SpbLoader.loadAll().firstOrNull())

        assertEquals("good", t.lookupVerse(40, 1, 1)?.text)
    }

    @Test
    fun `two translations both load and keep distinct ids`() {
        spb("ENG_ONE.spb", "One", "ONE", verse("B040C001V001", 40, 1, 1, "first") + "\n" + pad())
        spb("RUS_TWO.spb", "Two", "TWO", verse("B040C001V001", 40, 1, 1, "второй") + "\n" + pad())

        val ids = SpbLoader.loadAll().map { it.id }

        assertEquals(2, ids.size)
        assertEquals(ids.size, ids.toSet().size, "ids must be unique: $ids")
    }

    @Test
    fun `an empty bible folder loads nothing rather than failing`() {
        assertTrue(SpbLoader.loadAll().isEmpty())
    }

    @Test
    fun `the chapter index groups verses of one chapter`() {
        spb(
            "ENG_TST.spb", "Test", "TST",
            listOf(
                verse("B040C001V001", 40, 1, 1, "one"),
                verse("B040C001V002", 40, 1, 2, "two"),
                verse("B040C002V001", 40, 2, 1, "next chapter"),
                pad(),
            ).joinToString("\n"),
        )

        val t = assertNotNull(SpbLoader.loadAll().firstOrNull())

        assertEquals(2, t.byChapter[40 to 1]?.size)
    }

    @Test
    fun `loadSelected with an empty list falls back to loading everything`() {
        spb("ENG_ONE.spb", "One", "ONE", verse("B040C001V001", 40, 1, 1, "first") + "\n" + pad())

        assertEquals(1, SpbLoader.loadSelected(emptyList()).size)
    }

    @Test
    fun `loadSelected picks a translation by file name`() {
        spb("ENG_ONE.spb", "One", "ONE", verse("B040C001V001", 40, 1, 1, "first") + "\n" + pad())
        spb("RUS_TWO.spb", "Two", "TWO", verse("B040C001V001", 40, 1, 1, "второй") + "\n" + pad())

        val picked = SpbLoader.loadSelected(listOf("RUS_TWO.spb"))

        assertEquals(listOf("Two"), picked.map { it.title })
    }

    @Test
    fun `loadSelected ignores a name that matches nothing`() {
        spb("ENG_ONE.spb", "One", "ONE", verse("B040C001V001", 40, 1, 1, "first") + "\n" + pad())

        assertTrue(SpbLoader.loadSelected(listOf("ENG_MISSING.spb")).isEmpty())
    }

    @Test
    fun `loadSelected does not load the same file twice`() {
        spb("ENG_ONE.spb", "One", "ONE", verse("B040C001V001", 40, 1, 1, "first") + "\n" + pad())

        assertEquals(1, SpbLoader.loadSelected(listOf("ENG_ONE.spb", "ENG_ONE.spb")).size)
    }

    @Test
    fun `loadSelected finds a file nested in a subfolder by relative path`() {
        val sub = File(root, "nested").apply { mkdirs() }
        File(sub, "ENG_SUB.spb").writeText(
            "##Title:Sub\n##Abbreviation:SUB\n40\tMatthew\t28\n-----\n" + pad(),
            Charsets.UTF_8,
        )

        assertEquals(listOf("Sub"), SpbLoader.loadSelected(listOf("nested/ENG_SUB.spb")).map { it.title })
    }

    @Test
    fun `a missing bible root loads nothing rather than throwing`() {
        Config.bibleRoot = File(root, "does-not-exist").absolutePath

        assertTrue(SpbLoader.loadAll().isEmpty())
        assertTrue(SpbLoader.loadSelected(listOf("x.spb")).isEmpty())
        assertTrue(SpbLoader.scanAllBookManifests().isEmpty())
    }

    @Test
    fun `the book manifest scan collects every book across files, deduplicated`() {
        spb("ENG_ONE.spb", "One", "ONE", pad())
        spb("RUS_TWO.spb", "Two", "TWO", pad())

        assertEquals(listOf(40 to "Matthew"), SpbLoader.scanAllBookManifests())
    }

    @Test
    fun `the manifest scan stops at the separator and ignores comment and blank lines`() {
        File(root, "ENG_HDR.spb").writeText(
            listOf(
                "##Title:Header Test",
                "##Abbreviation:HDR",
                "",
                "40\tMatthew\t28",
                "not-a-number\tBogus\t1",
                "41\t\t9",
                "-----",
                "99\tShouldBeIgnored\t1",
            ).joinToString("\n"),
            Charsets.UTF_8,
        )

        assertEquals(listOf(40 to "Matthew"), SpbLoader.scanAllBookManifests())
    }

    @Test
    fun `a cyrillic translation is detected as cyrillic and a latin one as latin`() {
        spb("RUS_TWO.spb", "Two", "TWO", (1..12).joinToString("\n") {
            verse("B040C009V%03d".format(it), 40, 9, it, "слово номер $it")
        })
        spb("ENG_ONE.spb", "One", "ONE", pad())

        val byId = SpbLoader.loadAll().associateBy { it.abbreviation }
        assertEquals(Script.CYRILLIC, byId.getValue("TWO").script)
        assertEquals(Script.LATIN, byId.getValue("ONE").script)
    }

    @Test
    fun `a translation with no letters at all is neither latin nor cyrillic`() {
        spb("XXX_NUM.spb", "Numbers", "NUM", (1..12).joinToString("\n") {
            verse("B040C009V%03d".format(it), 40, 9, it, "123 456 789")
        })

        assertEquals(Script.OTHER, SpbLoader.loadAll().single().script)
    }

    @Test
    fun `the language prefix of the file name drives the numbering scheme`() {
        assertEquals("hebrew", SpbLoader.numberingFor("ENG"))
        assertEquals("lxx", SpbLoader.numberingFor("RUS"))
        assertEquals("lxx", SpbLoader.numberingFor("rus"))
    }

    @Test
    fun `two files sharing an abbreviation get distinct suffixed ids`() {
        spb("ENG_A.spb", "First", "SAME", pad())
        spb("ENG_B.spb", "Second", "SAME", pad())

        val ids = SpbLoader.loadAll().map { it.id }

        assertEquals(ids.size, ids.toSet().size, "expected unique ids, got $ids")
        assertTrue(ids.any { it.endsWith("_2") }, "expected a suffixed id in $ids")
    }
}
