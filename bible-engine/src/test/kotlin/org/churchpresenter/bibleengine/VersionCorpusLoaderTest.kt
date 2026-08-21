package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.version.VersionCorpusLoader
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VersionCorpusLoaderTest {

    private lateinit var root: File
    private lateinit var savedRoot: String
    private val savedCap = Config.versionMaxCorpusBibles
    private val savedLabels = Config.versionCorpusLabels

    @BeforeTest
    fun useTempRoot() {
        root = Files.createTempDirectory("version-corpus").toFile()
        savedRoot = Config.bibleRoot
        Config.bibleRoot = root.absolutePath
    }

    @AfterTest
    fun restore() {
        Config.bibleRoot = savedRoot
        Config.versionMaxCorpusBibles = savedCap
        Config.versionCorpusLabels = savedLabels
        root.deleteRecursively()
    }

    private fun spb(name: String, abbrev: String, wordSeed: String, dir: File = root) {
        // >= 50 distinct tokens, or the fingerprint is too sparse for the similarity collapse.
        val verses = (1..24).joinToString("\n") { i ->
            "B040C009V%03d\t40\t9\t$i\t$wordSeed alpha$i beta$i gamma$i delta$i epsilon$i".format(i)
        }
        File(dir, name).writeText(
            "##Title:$abbrev Title\n##Abbreviation:$abbrev\n40\tMatthew\t28\n-----\n$verses\n",
            Charsets.UTF_8,
        )
    }

    @Test
    fun `a missing bible root yields an empty corpus`() {
        Config.bibleRoot = File(root, "nope").absolutePath

        assertTrue(VersionCorpusLoader.load().labels.isEmpty())
    }

    @Test
    fun `an empty folder yields an empty corpus`() {
        assertTrue(VersionCorpusLoader.load().labels.isEmpty())
    }

    @Test
    fun `each usable module contributes a label`() {
        spb("ENG_A.spb", "AAA", "alpha")
        spb("ENG_B.spb", "BBB", "bravo")

        assertEquals(2, VersionCorpusLoader.load().labels.size)
    }

    @Test
    fun `the loaded labels are published onto Config`() {
        spb("ENG_A.spb", "AAA", "alpha")

        val corpus = VersionCorpusLoader.load()

        assertEquals(corpus.labels, Config.versionCorpusLabels)
    }

    @Test
    fun `a module with no abbreviation header is skipped with a reason`() {
        File(root, "broken.spb").writeText("##Title:No Abbrev\n-----\n", Charsets.UTF_8)
        val skips = mutableListOf<Pair<String, String>>()

        VersionCorpusLoader.load(onSkip = { f, r -> skips.add(f to r) })

        assertTrue(skips.any { it.first == "broken.spb" }, "expected a skip for broken.spb, got $skips")
    }

    @Test
    fun `a module under ten verses is skipped`() {
        File(root, "tiny.spb").writeText(
            "##Title:Tiny\n##Abbreviation:TIN\n40\tMatthew\t28\n-----\nB040C009V001\t40\t9\t1\tonly one\n",
            Charsets.UTF_8,
        )
        val skips = mutableListOf<String>()

        VersionCorpusLoader.load(onSkip = { f, _ -> skips.add(f) })

        assertTrue("tiny.spb" in skips, "expected tiny.spb skipped, got $skips")
    }

    @Test
    fun `the module cap drops the surplus and reports each one`() {
        spb("ENG_A.spb", "AAA", "alpha")
        spb("ENG_B.spb", "BBB", "bravo")
        spb("ENG_C.spb", "CCC", "charlie")
        Config.versionMaxCorpusBibles = 2
        val skips = mutableListOf<String>()

        val corpus = VersionCorpusLoader.load(onSkip = { f, r -> if ("cap" in r) skips.add(f) })

        assertEquals(2, corpus.labels.size)
        assertEquals(listOf("ENG_C.spb"), skips)
    }

    @Test
    fun `a priority file survives the cap even when it sorts last`() {
        spb("ENG_A.spb", "AAA", "alpha")
        spb("ENG_B.spb", "BBB", "bravo")
        spb("ENG_Z.spb", "ZZZ", "zulu")
        Config.versionMaxCorpusBibles = 1

        val corpus = VersionCorpusLoader.load(priorityFiles = listOf("ENG_Z.spb"))

        assertEquals(listOf("ZZZ"), corpus.labels.map { it.take(3) })
    }

    @Test
    fun `a priority entry given as a relative path is honoured`() {
        val sub = File(root, "ENG/King James").apply { mkdirs() }
        spb("kjv.spb", "KJV", "kingly", dir = sub)
        spb("ENG_A.spb", "AAA", "alpha")
        Config.versionMaxCorpusBibles = 1

        val corpus = VersionCorpusLoader.load(priorityFiles = listOf("ENG/King James/kjv.spb"))

        assertEquals(listOf("KJV"), corpus.labels.map { it.take(3) })
    }

    @Test
    fun `two modules declaring the same abbreviation collapse to one`() {
        spb("ENG_A.spb", "SAME", "alpha")
        spb("ENG_A_COPY.spb", "SAME", "alpha")
        val merged = mutableListOf<String>()

        val corpus = VersionCorpusLoader.load(onSkip = { f, r -> if ("merged" in r) merged.add(f) })

        assertEquals(1, corpus.labels.size, "expected the duplicate to be merged away")
        assertEquals(1, merged.size)
    }
}
