package engine.version

import engine.Config
import engine.bible.Script
import engine.bible.SpbLoader
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpbVersionIndexTest {

    private lateinit var dir: File
    private var savedRoot = ""
    private var savedCap = 0

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("ble-version").toFile()
        savedRoot = Config.bibleRoot
        savedCap = Config.versionMaxCorpusBibles
    }

    @AfterTest fun tearDown() {
        Config.bibleRoot = savedRoot
        Config.versionMaxCorpusBibles = savedCap
        dir.deleteRecursively()
    }

    /** Writes a minimal but structurally real `.spb`: header, book manifest, separator, verse rows. */
    private fun writeSpb(name: String, abbreviation: String, verses: List<Triple<String, Int, String>>): File {
        val f = File(dir, name)
        val sb = StringBuilder()
        sb.append("##Title:$abbreviation Test\n")
        sb.append("##Abbreviation:$abbreviation\n")
        sb.append("40\tMatthew\t28\n")
        sb.append("19\tPsalms\t150\n")
        sb.append("-----\n")
        for ((code, verse, text) in verses) {
            val book = code.substring(1, 4).toInt()
            val chapter = code.substring(5, 8).toInt()
            sb.append("$code\t$book\t$chapter\t$verse\t$text\n")
        }
        f.writeText(sb.toString(), Charsets.UTF_8)
        return f
    }

    private val cyrillicVerses = listOf(
        Triple("B019C023V001", 1, "Господь — Пастырь мой; я ни в чём не буду нуждаться."),
        Triple("B019C023V002", 2, "Он покоит меня на злачных пажитях и водит меня к водам тихим."),
        Triple("B040C018V013", 13, "И если случится найти её, истинно говорю вам, он радуется о ней более."),
    ) + (3..12).map { Triple("B019C023V%03d".format(it), it, "Стих номер $it с текстом.") }

    @Test fun `verse text is read back byte-exactly through multibyte cyrillic`() {
        // The offsets are BYTE offsets — a character-based scan lands mid-word in these files, so
        // this fixture is deliberately Cyrillic.
        val file = writeSpb("RUS_TST.spb", "TST", cyrillicVerses)
        val index = assertNotNull(SpbVersionIndex.build(file, mutableMapOf()))

        Config.bibleRoot = dir.absolutePath
        val parsed = assertNotNull(SpbLoader.loadAll().firstOrNull())
        for ((code, _, expected) in cyrillicVerses) {
            assertEquals(expected, index.text(assertNotNull(SpbVersionIndex.packCode(code))), "mismatch at $code")
            assertEquals(parsed.byCode[code]?.text, index.text(SpbVersionIndex.packCode(code)!!))
        }
        assertEquals(Script.CYRILLIC, index.script)
    }

    @Test fun `a file replaced after indexing serves nothing rather than garbage`() {
        val file = writeSpb("ENG_TST.spb", "TST", cyrillicVerses)
        val index = assertNotNull(SpbVersionIndex.build(file, mutableMapOf()))
        val code = SpbVersionIndex.packCode("B019C023V001")!!
        assertNotNull(index.text(code))

        file.writeText("completely different and much shorter content\n", Charsets.UTF_8)
        assertNull(index.text(code), "a stale index must refuse, not read whatever is now at that offset")
    }

    @Test fun `section header rows are not indexed as verses`() {
        val file = writeSpb(
            "ENG_HDR.spb", "HDR",
            listOf(Triple("B040C018V000", 0, "A section heading")) + cyrillicVerses,
        )
        val index = assertNotNull(SpbVersionIndex.build(file, mutableMapOf()))
        assertNull(index.text(SpbVersionIndex.packCode("B040C018V000")!!))
    }

    @Test fun `the corpus cap is honoured and deterministic`() {
        // Wholly distinct wording per file, so this measures the cap alone and not duplicate
        // collapse. (Note a numeric prefix would NOT be enough — the tokenizer drops digits.)
        val words = listOf("альфа бета гамма", "дельта эпсилон зета", "эта тета йота", "каппа лямбда мю")
        repeat(4) { i ->
            writeSpb("ENG_V$i.spb", "V$i", cyrillicVerses.map { it.copy(third = "${words[i]} текст стиха") })
        }
        Config.bibleRoot = dir.absolutePath
        Config.versionMaxCorpusBibles = 2
        val corpus = VersionCorpusLoader.load()
        assertEquals(listOf("V0", "V1"), corpus.labels, "the cap must take the first files by sorted name")
    }

    @Test fun `duplicate modules of one version collapse but distinct ones do not`() {
        // Two exports of the same translation differing only in punctuation would otherwise split
        // the vote and tie forever, so nothing would ever be reported. Needs a vocabulary big enough
        // to fingerprint: below MIN_FINGERPRINT_TOKENS the collapse declines to judge either way.
        // 120 distinct words, comfortably over MIN_FINGERPRINT_TOKENS.
        val words = ('a'..'l').flatMap { x -> ('a'..'j').map { y -> "lex$x$y" } }
        val rich = (0 until 40).map { i ->
            Triple("B019C023V%03d".format(i + 1), i + 1, (0..5).joinToString(" ") { words[(i * 6 + it) % words.size] } + ".")
        }
        val a = writeSpb("ENG_AAA.spb", "AAA", rich)
        val b = writeSpb("ENG_BBB.spb", "BBB", rich.map { it.copy(third = it.third.replace(".", ",")) })
        val c = writeSpb(
            "ENG_CCC.spb", "CCC",
            rich.mapIndexed { i, v -> v.copy(third = "текст${i} совсем другими незнакомыми выражениями оборотами лексикой перевода") },
        )
        val indexes = listOf(a, b, c).map { assertNotNull(SpbVersionIndex.build(it, mutableMapOf())) }
        val kept = VersionCorpusLoader.collapseDuplicates(indexes).map { it.label }
        assertTrue("AAA" in kept && "CCC" in kept, "distinct versions must survive, got $kept")
        assertTrue("BBB" !in kept, "a near-identical re-export must collapse, got $kept")
    }

    @Test fun `two modules declaring the same abbreviation collapse however much their text differs`() {
        // Real case: two exports of the Russian Synodal, one with "(22:1)" cross-reference prefixes
        // and different quote marks, measure only 0.929 similar — in among genuinely distinct pairs.
        // Their shared abbreviation is the reliable signal, and two candidates under one visible
        // label would be incoherent to report anyway.
        writeSpb("RUS_A.spb", "RST", cyrillicVerses)
        writeSpb("RUS_B.spb", "RST", cyrillicVerses.map { it.copy(third = "(22:1) «${it.third}» иными словами совсем") })
        Config.bibleRoot = dir.absolutePath
        assertEquals(listOf("RST"), VersionCorpusLoader.load().labels)
    }

    @Test fun `the selected bibles are indexed even when the cap would exclude them`() {
        repeat(3) { i ->
            writeSpb("ENG_A$i.spb", "A$i", cyrillicVerses.map { it.copy(third = "вариант $i уникальный текст стиха здесь") })
        }
        writeSpb("ZZZ_LAST.spb", "ZZZ", cyrillicVerses.map { it.copy(third = "совершенно другой перевод стиха") })
        Config.bibleRoot = dir.absolutePath
        Config.versionMaxCorpusBibles = 2
        // Sorted last, so a plain prefix cap would drop the very bible the operator has loaded.
        assertTrue("ZZZ" in VersionCorpusLoader.load(priorityFiles = listOf("ZZZ_LAST.spb")).labels)
    }

    @Test fun `a file with no abbreviation header is skipped`() {
        val f = File(dir, "ENG_BAD.spb")
        f.writeText("##Title:No abbreviation\n-----\nB040C018V013\t40\t18\t13\ttext\n", Charsets.UTF_8)
        assertNull(SpbVersionIndex.build(f, mutableMapOf()))
    }

    // ── Why a module is missing from the corpus ───────────────────────────────
    //
    // Real case: a 24-module folder on a network share produced a corpus of 3, and the loader said
    // nothing about the other 21 — indistinguishable from the feature being broken. Each drop is
    // reported with a reason that points at the right thing to fix.

    @Test fun `a module that is not a usable bible is reported, named and distinguished`() {
        writeSpb("ENG_GOOD.spb", "GOOD", cyrillicVerses)
        File(dir, "ENG_BAD.spb")
            .writeText("##Title:No abbreviation\n-----\nB040C018V013\t40\t18\t13\ttext\n", Charsets.UTF_8)
        Config.bibleRoot = dir.absolutePath

        val skips = mutableListOf<Pair<String, String>>()
        val corpus = VersionCorpusLoader.load(onSkip = { name, reason -> skips += name to reason })

        assertEquals(listOf("GOOD"), corpus.labels, "the usable module still loads")
        assertEquals(1, skips.size, "exactly the one bad file is reported, got $skips")
        assertEquals("ENG_BAD.spb", skips[0].first)
        // "not a usable module" sends the operator to the file's contents; the unreadable wording is
        // reserved for an I/O failure, which is a different fix entirely.
        assertTrue(skips[0].second.contains("not a usable module"), "got: ${skips[0].second}")
    }

    @Test fun `a module dropped by the cap says so rather than vanishing`() {
        repeat(3) { i ->
            writeSpb("ENG_C$i.spb", "C$i", cyrillicVerses.map { it.copy(third = "вариант $i уникальный текст стиха") })
        }
        Config.bibleRoot = dir.absolutePath
        Config.versionMaxCorpusBibles = 2

        val skips = mutableListOf<Pair<String, String>>()
        VersionCorpusLoader.load(onSkip = { name, reason -> skips += name to reason })

        assertEquals(listOf("ENG_C2.spb"), skips.map { it.first }, "the file past the cap, got $skips")
        assertTrue(skips[0].second.contains("cap"), "got: ${skips[0].second}")
    }

    @Test fun `a module merged into another is reported against the label that absorbed it`() {
        // Same abbreviation from two files: collapsed by the publisher's own word, and previously
        // the only trace was a corpus one shorter than the folder.
        writeSpb("RUS_A.spb", "RST", cyrillicVerses)
        writeSpb("RUS_B.spb", "RST", cyrillicVerses.map { it.copy(third = "(22:1) «${it.third}» иными словами") })
        Config.bibleRoot = dir.absolutePath

        val skips = mutableListOf<Pair<String, String>>()
        assertEquals(listOf("RST"), VersionCorpusLoader.load(onSkip = { n, r -> skips += n to r }).labels)

        assertEquals(listOf("RUS_B.spb"), skips.map { it.first }, "got $skips")
        assertTrue(skips[0].second.contains("merged"), "got: ${skips[0].second}")
        assertTrue(skips[0].second.contains("RST"), "names the label that absorbed it: ${skips[0].second}")
    }

    @Test fun `a corpus that loads cleanly reports no skips at all`() {
        writeSpb("ENG_AAA.spb", "AAA", cyrillicVerses.map { it.copy(third = "первый перевод уникальными словами здесь") })
        writeSpb("ENG_BBB.spb", "BBB", cyrillicVerses.map { it.copy(third = "второй совсем другой лексикой оборотами") })
        Config.bibleRoot = dir.absolutePath

        val skips = mutableListOf<String>()
        val corpus = VersionCorpusLoader.load(onSkip = { name, _ -> skips += name })

        assertEquals(listOf("AAA", "BBB"), corpus.labels)
        assertTrue(skips.isEmpty(), "nothing to report when every module made it, got $skips")
    }

    @Test fun `a malformed verse code is rejected rather than packed`() {
        assertNull(SpbVersionIndex.packCode("nonsense"))
        assertNull(SpbVersionIndex.packCode("BxxxCyyyVzzz"))
        assertEquals(40_018_013, SpbVersionIndex.packCode("B040C018V013"))
    }
}
