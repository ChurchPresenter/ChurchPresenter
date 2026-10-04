package org.churchpresenter.app.churchpresenter.data

import kotlinx.coroutines.runBlocking
import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.SettingsManager
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The primary Bible the Planning Center import looks scripture up in: what it finds, what it does
 * when the operator has no Bible set up, and that it reads the Bible once per import.
 *
 * With a Bible configured, the detection itself runs end to end in `PlanningCenterScriptureImportTest`.
 */
class PlanningCenterPrimaryBibleTest {

    private lateinit var home: File
    private var realHome: String? = null

    @BeforeTest
    fun setUp() {
        realHome = System.getProperty("user.home")
        home = Files.createTempDirectory("cp-pco-primary-bible").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        realHome?.let { System.setProperty("user.home", it) }
        home.deleteRecursively()
    }

    /** Points the operator's Bible settings at [storageDirectory] and [primaryBible]. */
    private fun configureBible(storageDirectory: String, primaryBible: String) {
        val manager = SettingsManager()
        manager.saveSettings(
            manager.loadSettings().let { settings ->
                settings.copy(
                    bibleSettings = settings.bibleSettings.copy(
                        storageDirectory = storageDirectory,
                        primaryBible = primaryBible,
                    ),
                )
            },
        )
    }

    private fun writePsalmsBible(dir: File): File = SpbFixture.spbFile(
        dir, name = "test.spb",
        content = SpbFixture.buildContent(
            title = "Test Bible",
            books = listOf(SpbFixture.Book(19, "Psalms", 1)),
            verses = (1..6).map { SpbFixture.Verse(19, 23, it, "Psalm twenty three verse $it") },
        ),
    )

    @Test
    fun `with no Bible set up nothing is detected`() {
        configureBible(storageDirectory = File(home, "bibles").absolutePath, primaryBible = "")

        val found = runBlocking { PlanningCenterPrimaryBible().detect("Psalms 23:1-6") }

        assertTrue(found.isEmpty(), "no primary Bible is configured, so there is nothing to look in; got $found")
    }

    @Test
    fun `with no Bible folder nothing is detected`() {
        configureBible(storageDirectory = "", primaryBible = "test.spb")

        val found = runBlocking { PlanningCenterPrimaryBible().detect("Psalms 23:1-6") }

        assertTrue(found.isEmpty(), "a Bible name without a folder to find it in cannot be loaded; got $found")
    }

    @Test
    fun `the Bible is read once and kept for the rest of the import`() {
        val bibles = File(home, "bibles").also { it.mkdirs() }
        val file = writePsalmsBible(bibles)
        configureBible(storageDirectory = bibles.absolutePath, primaryBible = "test.spb")
        val primary = PlanningCenterPrimaryBible()

        val first = runBlocking { primary.detect("Psalms 23:1-6") }
        assertEquals(1, first.size, "the reference should resolve against the Bible on disk; got $first")
        assertTrue(file.delete(), "the Bible file should be removable between the two lookups")
        val second = runBlocking { primary.detect("Psalms 23:1-6") }

        assertEquals(first, second, "the second lookup should use the Bible already in memory, not read the file again")
    }
}
