package org.churchpresenter.bibletab

import org.churchpresenter.bible.SpbFixture
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleSyncMode
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BibleViewModelReplicaTranslationsTest {

    private lateinit var dir: File
    private lateinit var testHome: File
    private var realHome: String? = null
    private val built = mutableListOf<BibleViewModel>()

    private fun module(title: String, wording: String) = SpbFixture.buildContent(
        title = title,
        books = listOf(SpbFixture.Book(43, "John", 3)),
        verses = listOf(SpbFixture.Verse(43, 3, 16, wording)),
    )

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-bible-replica-list").toFile()
        realHome = System.getProperty("user.home")
        testHome = Files.createTempDirectory("cp-bible-replica-home").toFile()
        System.setProperty("user.home", testHome.absolutePath)
        SpbFixture.spbFile(dir, name = "local.spb", content = module("Local", "LOCAL wording"))
    }

    @AfterTest
    fun tearDown() {
        built.forEach { it.dispose() }
        realHome?.let { System.setProperty("user.home", it) }
        dir.deleteRecursively()
        testHome.deleteRecursively()
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $what")
            Thread.yield()
        }
    }

    private fun follower(settings: BibleSettings) = BibleViewModel(AppSettings(bibleSettings = settings))
        .also { built += it }

    @Test
    fun `a replica sent as a list of translations loads them under the primary's own names`() {
        val vm = follower(BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "local.spb"))
        awaitUntil("the local Bible") { vm.isFullyLoaded && vm.books.value.isNotEmpty() }

        vm.setInstanceLinkSource(
            active = true,
            mode = BibleSyncMode.FULL_REPLICA,
            fetchBibleFile = null,
            fetchSecondaryBibleFile = null,
            fetchBibleTranslations = {
                listOf(
                    "primary-a.spb" to module("Primary A", "PRIMARY A").toByteArray(),
                    "primary-b.spb" to module("Primary B", "PRIMARY B").toByteArray(),
                )
            },
        )
        val expected = listOf("primary-a.spb", "primary-b.spb")
        awaitUntil("both translations") { vm.loadedTranslations.value.map { it.fileName } == expected }

        assertEquals("Primary A", vm.primaryBible.value?.getBibleTitle())
        assertNotNull(vm.secondaryBible.value)
    }

    @Test
    fun `a configured translation whose file is missing is reported`() {
        val vm = follower(
            BibleSettings(storageDirectory = dir.absolutePath, primaryBible = "local.spb", secondaryBible = "gone.spb"),
        )
        awaitUntil("the load to finish") { vm.isFullyLoaded && vm.books.value.isNotEmpty() }

        val errors = vm.loadErrors.value
        assertTrue(
            errors.any { it.reason == BibleViewModel.MODULE_FILE_MISSING && it.resourcePath.endsWith("gone.spb") },
            errors.toString(),
        )
    }
}
