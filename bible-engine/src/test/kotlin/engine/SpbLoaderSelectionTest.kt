package engine

import engine.bible.SpbLoader
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * How ChurchPresenter's chosen bibles are resolved inside the bible root.
 *
 * Hermetic — builds its own tiny `.spb` files in a temp folder — unlike [SpbLoaderTest], which
 * needs a real installed collection.
 */
class SpbLoaderSelectionTest {

    private lateinit var dir: File
    private var savedRoot = ""

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("ble-select").toFile()
        savedRoot = Config.bibleRoot
        Config.bibleRoot = dir.absolutePath
    }

    @AfterTest fun tearDown() {
        Config.bibleRoot = savedRoot
        dir.deleteRecursively()
    }

    private fun spb(relative: String, abbreviation: String, text: String) {
        val f = File(dir, relative)
        f.parentFile.mkdirs()
        val sb = StringBuilder("##Title:$abbreviation Test\n##Abbreviation:$abbreviation\n40\tMatthew\t28\n-----\n")
        for (v in 1..20) sb.append("B040C001V%03d\t40\t1\t%d\t%s verse %d\n".format(v, v, text, v))
        f.writeText(sb.toString(), Charsets.UTF_8)
    }

    @Test fun `a bible in a subfolder is selected by its relative path`() {
        // ChurchPresenter stores the path relative to the bible root now that it scans subfolders,
        // because collections nest a folder per language and translation.
        spb("ENG/King James/kjv.spb", "KJV", "alpha")
        spb("RUS/synodal.spb", "RST", "beta")

        val loaded = SpbLoader.loadSelected(listOf("ENG/King James/kjv.spb"))
        assertEquals(listOf("KJV"), loaded.map { it.abbreviation })
    }

    @Test fun `a bare file name still selects a bible at the root`() {
        // What older settings hold, and still how a root-level file is named.
        spb("kjv.spb", "KJV", "alpha")
        assertEquals(listOf("KJV"), SpbLoader.loadSelected(listOf("kjv.spb")).map { it.abbreviation })
    }

    @Test fun `a bare file name still finds a bible that has moved into a subfolder`() {
        // Upgrade path: a setting saved before the recursive scan must not silently stop loading
        // just because the collection was reorganised underneath it.
        spb("ENG/kjv.spb", "KJV", "alpha")
        assertEquals(listOf("KJV"), SpbLoader.loadSelected(listOf("kjv.spb")).map { it.abbreviation })
    }

    @Test fun `same-named bibles in different folders are told apart by path`() {
        // The reason paths are matched before bare names: matching by name alone resolves to
        // whichever file the directory walk happened to reach last.
        spb("A/bible.spb", "AAA", "alpha")
        spb("B/bible.spb", "BBB", "beta")

        assertEquals(listOf("AAA"), SpbLoader.loadSelected(listOf("A/bible.spb")).map { it.abbreviation })
        assertEquals(listOf("BBB"), SpbLoader.loadSelected(listOf("B/bible.spb")).map { it.abbreviation })
    }

    @Test fun `selection order is preserved so primary stays first`() {
        spb("ENG/kjv.spb", "KJV", "alpha")
        spb("RUS/rst.spb", "RST", "beta")
        assertEquals(
            listOf("RST", "KJV"),
            SpbLoader.loadSelected(listOf("RUS/rst.spb", "ENG/kjv.spb")).map { it.abbreviation },
        )
    }

    @Test fun `a selection that no longer exists is skipped rather than failing the rest`() {
        spb("ENG/kjv.spb", "KJV", "alpha")
        val loaded = SpbLoader.loadSelected(listOf("gone/missing.spb", "ENG/kjv.spb"))
        assertEquals(listOf("KJV"), loaded.map { it.abbreviation })
    }

    @Test fun `loadAll finds bibles nested in subfolders`() {
        spb("ENG/King James/kjv.spb", "KJV", "alpha")
        spb("RUS/deep/nested/rst.spb", "RST", "beta")
        val all = SpbLoader.loadAll()
        assertEquals(setOf("KJV", "RST"), all.map { it.abbreviation }.toSet())
        assertNotNull(all.first().byCode["B040C001V001"])
        assertTrue(all.all { it.byBCV.isNotEmpty() })
    }
}
