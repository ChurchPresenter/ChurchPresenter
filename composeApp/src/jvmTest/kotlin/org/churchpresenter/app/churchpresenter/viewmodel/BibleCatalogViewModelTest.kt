package org.churchpresenter.app.churchpresenter.viewmodel

import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.bibleformats.catalog.BibleCatalogOutcome
import org.churchpresenter.bibleformats.catalog.BibleInstallOutcome
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEventStore
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.BibleSource
import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import org.churchpresenter.bibleformats.catalog.InstallProgress
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Browsing an archive and installing a translation from it.
 *
 * What matters here is that the dialog can always say something specific. A catalogue that will not
 * load and an install that will not finish need different messages, and among installs a damaged
 * download, a Bible that won't convert and a folder that can't be written to all need different
 * advice — otherwise the only thing the operator can do is try again and watch it fail identically.
 * An install with no Bible folder chosen must not go near the network, since there is nowhere for
 * the file to land.
 *
 * The "installed" mark is an exact file-name match against the Bible folder: a Bible the user
 * obtained elsewhere and filed under a folder of their own is left alone rather than guessed at by
 * title, so those tests assert on names.
 *
 * The archive is a plain [FakeSource] rather than a mock — [BibleSource] is a two-method interface,
 * so a fake states outcomes directly and keeps working when the implementations change. The view
 * model runs on `Dispatchers.Main`, which on desktop is the Swing event queue, so [settle] drains
 * that queue rather than polling: `isLoading` is set inside the launched coroutine, and a poll can
 * pass before the work has even been dispatched. The tests that park a call mid-flight wait on
 * observable state instead.
 */
class BibleCatalogViewModelTest {

    private lateinit var dir: File
    private val created = mutableListOf<BibleCatalogViewModel>()

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-catalog-vm-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    /** A stand-in archive: states what each call answers, and counts how often it was asked. */
    private class FakeSource(
        var catalogOutcome: BibleCatalogOutcome = BibleCatalogOutcome.Success(emptyList()),
        var installOutcome: BibleInstallOutcome = BibleInstallOutcome.NetworkError,
    ) : BibleSource {
        override val sourceId = BibleSourceId.EBIBLE
        val catalogCalls = AtomicInteger()
        val installCalls = AtomicInteger()

        /** When set, the call parks here until completed — for testing mid-flight state. */
        var parkedCatalog: CompletableDeferred<BibleCatalogOutcome>? = null
        var parkedInstall: CompletableDeferred<BibleInstallOutcome>? = null
        var emitProgress: InstallProgress? = null

        override suspend fun catalog(nowMillis: Long): BibleCatalogOutcome {
            catalogCalls.incrementAndGet()
            return parkedCatalog?.await() ?: catalogOutcome
        }

        override suspend fun install(
            module: BibleModule,
            targetDir: File,
            onProgress: (InstallProgress) -> Unit,
        ): BibleInstallOutcome {
            installCalls.incrementAndGet()
            emitProgress?.let(onProgress)
            return parkedInstall?.await() ?: installOutcome
        }
    }

    private fun vm(
        source: BibleSource,
        storageDirectory: String = dir.absolutePath,
        usage: UsageEventStore = UsageEventStore { File(dir, "usage-events.json") },
    ) = BibleCatalogViewModel(source, storageDirectory, Dispatchers.Unconfined, usage).also { created.add(it) }

    @Test
    fun `a completed install is counted, a failed one is not`() {
        val usage = UsageEventStore { File(dir, "usage-events.json") }

        val failing = vm(FakeSource(installOutcome = BibleInstallOutcome.NetworkError), usage = usage)
        failing.install(module("ENG_ACV")) {}
        assertTrue(usage.unreported().isEmpty(), "a download that never arrived is not the catalogue being used")

        val working = vm(
            FakeSource(
                installOutcome = BibleInstallOutcome.Success(File(dir, "kjv.spb"), "KJV", 66, "Public domain")
            ),
            usage = usage,
        )
        working.install(module("ENG_ACV")) {}
        assertEquals(mapOf(UsageEvent.BIBLE_INSTALLED to 1), usage.unreported())
    }

    /** Drains the Swing event queue, which is where the view model's coroutines run. */
    private fun settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    /**
     * Asserts [what] has already happened.
     *
     * The view model runs on an immediate dispatcher here, so a load or an install completes before
     * the call that started it returns — there is nothing to wait for. Where a test needs to observe
     * the in-flight state, it parks the source on a `CompletableDeferred` and completes it when
     * ready, so the signal is the work itself rather than elapsed time.
     *
     * This replaced a 5s `Thread.sleep` poll. That shape is what produced #24 and then #56: the
     * deadline expires under suite load and the failure names a timeout instead of a cause.
     */
    private fun awaitUntil(what: String, condition: () -> Boolean) {
        if (!condition()) throw AssertionError("expected $what to have happened synchronously")
    }

    private fun module(
        stem: String,
        language: String = "ENG",
        languageName: String = "",
        languageNativeName: String = "",
        identifier: String = stem.substringAfter('_', stem),
        displayName: String = stem,
        releaseDate: String = "2009-01-20",
    ) = BibleModule(
        sourceId = BibleSourceId.EBIBLE,
        downloadKey = stem,
        sizeBytes = 1000,
        language = language,
        languageName = languageName,
        languageNativeName = languageNativeName,
        identifier = identifier,
        displayName = displayName,
        releaseDate = releaseDate,
        fileStem = stem,
    )

    private fun catalogOf(vararg modules: BibleModule, stale: Boolean = false) =
        FakeSource(catalogOutcome = BibleCatalogOutcome.Success(modules.toList(), stale))

    private fun success(stem: String = "ENG_ACV") =
        BibleInstallOutcome.Success(File(dir, "$stem.spb"), "A Conservative Version", 66, "Public Domain")

    // --- loading the catalogue ---

    @Test
    fun `a fresh view model shows nothing and no error`() {
        val model = vm(FakeSource())

        assertTrue(model.modules.isEmpty())
        assertFalse(model.isLoading)
        assertNull(model.catalogError)
        assertFalse(model.isStale)
    }

    @Test
    fun `loading publishes the modules and the languages present`() {
        val model = vm(catalogOf(module("ENG_ACV"), module("ENG_KJV"), module("RUS_SYN", language = "RUS")))

        model.load()
        settle()

        assertEquals(3, model.modules.size)
        assertEquals(listOf("ENG" to 2, "RUS" to 1), model.languages.map { it.code to it.count })
        assertFalse(model.isLoading)
        assertNull(model.catalogError)
    }

    @Test
    fun `each language option carries the English name its modules were published with`() {
        val model = vm(
            catalogOf(
                module("ENG_ACV", languageName = "English"),
                module("RUS_SYN", language = "RUS", languageName = "Russian"),
            )
        )

        model.load()
        settle()

        assertEquals(
            listOf("English" to "ENG", "Russian" to "RUS"),
            model.languages.map { it.name to it.code }
        )
    }

    @Test
    fun `languages are ordered by the name they read as, not by their code`() {
        // DEU sorts before ELL by code, but "Greek" comes before "German" by name — which is the
        // order that matters, because the name is what the dropdown shows and filters on.
        val model = vm(
            catalogOf(
                module("DEU_LUT", language = "DEU", languageName = "German"),
                module("ELL_BYZ", language = "ELL", languageName = "Greek"),
            )
        )

        model.load()
        settle()

        assertEquals(listOf("German", "Greek"), model.languages.map { it.name })
    }

    @Test
    fun `a language with no published name is still listed, under its code`() {
        val model = vm(
            catalogOf(
                module("ENG_ACV", languageName = "English"),
                module("CZE_CZBKR", language = "CZE", languageName = ""),
            )
        )

        model.load()
        settle()

        // "CZE" sorts before "English" once the blank name falls back to the code.
        assertEquals(listOf("CZE" to "", "ENG" to "English"), model.languages.map { it.code to it.name })
    }

    @Test
    fun `searching by language name finds a translation`() {
        val model = vm(catalogOf(module("DEU_LUT", language = "DEU", languageName = "German")))

        model.load()
        settle()
        model.query = "german"

        assertEquals(listOf("DEU_LUT"), model.visibleModules.map { it.fileStem })
    }

    @Test
    fun `searching by the language's own name for itself finds a translation`() {
        // A Russian speaker would type "рус" long before they would think to try "Russian".
        val model = vm(
            catalogOf(module("RUS_SYN", language = "RUS", languageName = "Russian", languageNativeName = "русский"))
        )

        model.load()
        settle()
        model.query = "рус"

        assertEquals(listOf("RUS_SYN"), model.visibleModules.map { it.fileStem })
    }

    @Test
    fun `a language option carries both spellings of its name`() {
        val model = vm(
            catalogOf(module("RUS_SYN", language = "RUS", languageName = "Russian", languageNativeName = "русский"))
        )

        model.load()
        settle()

        val option = model.languages.single()
        assertEquals("Russian", option.name)
        assertEquals("русский", option.nativeName)
    }

    @Test
    fun `only names carried by more than one module are marked ambiguous`() {
        // Both archives publish some translations twice, under names that are identical. Those rows
        // show a date so they can be told apart; the rest stay uncluttered.
        val model = vm(
            catalogOf(
                module("CZE_CZBKR", language = "CZE", displayName = "CZECH BKR"),
                module("CZE_CZBKR_2", language = "CZE", displayName = "Czech BKR"),
                module("ENG_ACV", displayName = "A Conservative Version"),
            )
        )

        model.load()
        settle()

        assertEquals(setOf("czech bkr"), model.duplicateDisplayNames, "matched regardless of casing")
    }

    @Test
    fun `a catalogue with no repeated names marks nothing ambiguous`() {
        val model = vm(catalogOf(module("ENG_ACV", displayName = "ACV"), module("ENG_KJV", displayName = "KJV")))

        model.load()
        settle()

        assertTrue(model.duplicateDisplayNames.isEmpty())
    }

    @Test
    fun `a stale catalogue still shows its modules and says so`() {
        val model = vm(catalogOf(module("ENG_ACV"), stale = true))

        model.load()
        settle()

        assertTrue(model.isStale)
        assertEquals(1, model.modules.size)
    }

    @Test
    fun `each catalogue failure keeps its own cause`() {
        val cases = mapOf(
            BibleCatalogOutcome.NetworkError to BibleCatalogError.NETWORK_ERROR,
            BibleCatalogOutcome.RateLimited(null) to BibleCatalogError.RATE_LIMITED,
            BibleCatalogOutcome.Failure to BibleCatalogError.FAILURE,
        )

        cases.forEach { (outcome, expected) ->
            val model = vm(FakeSource(catalogOutcome = outcome))

            model.load()
            settle()

            assertEquals(expected, model.catalogError, "for $outcome")
            assertFalse(model.isLoading, "the spinner must stop for $outcome")
        }
    }

    @Test
    fun `retrying after a failure clears the stale error`() {
        val source = FakeSource(catalogOutcome = BibleCatalogOutcome.NetworkError)
        val model = vm(source)
        model.load()
        settle()
        assertEquals(BibleCatalogError.NETWORK_ERROR, model.catalogError)

        source.catalogOutcome = BibleCatalogOutcome.Success(listOf(module("ENG_ACV")))
        model.load()
        settle()

        assertNull(model.catalogError)
        assertEquals(1, model.modules.size)
    }

    @Test
    fun `a load while one is already running is not duplicated`() {
        val source = FakeSource()
        val parked = CompletableDeferred<BibleCatalogOutcome>()
        source.parkedCatalog = parked
        val model = vm(source)

        model.load()
        awaitUntil("the first load to start") { model.isLoading }
        model.load()
        parked.complete(BibleCatalogOutcome.Success(listOf(module("ENG_ACV"))))
        awaitUntil("the load to finish") { !model.isLoading }

        assertEquals(1, source.catalogCalls.get())
    }

    // --- filtering ---

    @Test
    fun `the query matches display name, identifier and language`() {
        val model = vm(
            catalogOf(
                module("ENG_ACV", displayName = "A Conservative Version", identifier = "ACV"),
                module("RUS_SYN", language = "RUS", displayName = "Synodal", identifier = "SYN"),
            )
        )
        model.load()
        settle()

        model.query = "conservative"
        assertEquals(listOf("ENG_ACV"), model.visibleModules.map { it.fileStem })

        model.query = "SYN"
        assertEquals(listOf("RUS_SYN"), model.visibleModules.map { it.fileStem })

        model.query = "rus"
        assertEquals(listOf("RUS_SYN"), model.visibleModules.map { it.fileStem })

        model.query = "   "
        assertEquals(2, model.visibleModules.size)
    }

    @Test
    fun `the language filter and the query compose`() {
        val model = vm(
            catalogOf(
                module("ENG_KJV", displayName = "King James"),
                module("ENG_ACV", displayName = "A Conservative Version"),
                module("RUS_SYN", language = "RUS", displayName = "Synodal King"),
            )
        )
        model.load()
        settle()

        model.selectedLanguage = "ENG"
        assertEquals(listOf("ENG_KJV", "ENG_ACV"), model.visibleModules.map { it.fileStem })

        model.query = "king"
        assertEquals(
            listOf("ENG_KJV"),
            model.visibleModules.map { it.fileStem },
            "picking a language must not throw the search away",
        )
    }

    // --- installed detection ---

    @Test
    fun `only Bible files in the storage folder itself count as installed`() {
        File(dir, "ENG_ACV.spb").writeText("##Title:\tACV")
        File(dir, "notes.txt").writeText("not a Bible")
        File(dir, "nested").apply { mkdirs() }.resolve("RUS_SYN.spb").writeText("##Title:\tSynodal")
        val model = vm(FakeSource())

        model.refreshInstalled()

        assertEquals(setOf("ENG_ACV.spb"), model.installedFiles)
        assertTrue(model.isInstalled(module("ENG_ACV")))
        assertFalse(model.isInstalled(module("RUS_SYN")), "a copy filed under the user's own folder is left alone")
    }

    @Test
    fun `with no storage folder nothing is reported as installed`() {
        val model = vm(FakeSource(), storageDirectory = "")

        model.refreshInstalled()

        assertTrue(model.installedFiles.isEmpty())
    }

    // --- installing ---

    @Test
    fun `installing without a storage folder never reaches the network`() {
        val source = FakeSource(installOutcome = success())
        val model = vm(source, storageDirectory = "")
        var installed: String? = null

        model.install(module("ENG_ACV")) { installed = it }
        settle()

        assertEquals(BibleDownloadError.NO_DIRECTORY, model.installError)
        assertNull(installed)
        assertEquals(0, source.installCalls.get())
    }

    @Test
    fun `a successful install reports the file name and what was installed`() {
        val model = vm(FakeSource(installOutcome = success()))
        var installed: String? = null

        model.install(module("ENG_ACV")) { installed = it }
        settle()

        assertEquals("ENG_ACV.spb", installed, "the settings tab stores this exact name as the primary Bible")
        assertTrue(model.isInstalled(module("ENG_ACV")))
        assertEquals("A Conservative Version", model.lastInstalled?.title)
        assertEquals(66, model.lastInstalled?.books)
        assertEquals("Public Domain", model.lastInstalled?.rights)
        assertNull(model.installingKey)
        assertNull(model.installError)
    }

    @Test
    fun `each install failure keeps its own cause and installs nothing`() {
        val cases = mapOf(
            BibleInstallOutcome.NetworkError to BibleDownloadError.NETWORK_ERROR,
            BibleInstallOutcome.DownloadStalled to BibleDownloadError.DOWNLOAD_STALLED,
            BibleInstallOutcome.HttpError(404) to BibleDownloadError.HTTP_ERROR,
            BibleInstallOutcome.ChecksumMismatch to BibleDownloadError.CHECKSUM_MISMATCH,
            BibleInstallOutcome.CorruptArchive to BibleDownloadError.CORRUPT_ARCHIVE,
            BibleInstallOutcome.ConversionFailed to BibleDownloadError.CONVERSION_FAILED,
            BibleInstallOutcome.WriteFailed to BibleDownloadError.WRITE_FAILED,
            BibleInstallOutcome.NoDirectory to BibleDownloadError.NO_DIRECTORY,
        )

        cases.forEach { (outcome, expected) ->
            val model = vm(FakeSource(installOutcome = outcome))
            var installed: String? = null

            model.install(module("ENG_ACV")) { installed = it }
            settle()

            assertEquals(expected, model.installError, "for $outcome")
            assertNull(installed, "nothing should be reported installed for $outcome")
            assertNull(model.lastInstalled, "for $outcome")
            assertFalse(model.isInstalled(module("ENG_ACV")), "for $outcome")
            assertNull(model.installingKey)
        }
    }

    @Test
    fun `retrying a stalled download runs the same install again`() {
        val source = FakeSource(installOutcome = BibleInstallOutcome.DownloadStalled)
        val model = vm(source)
        var installed: String? = null

        model.install(module("ENG_ACV")) { installed = it }
        settle()
        assertEquals(BibleDownloadError.DOWNLOAD_STALLED, model.installError)

        source.installOutcome = BibleInstallOutcome.Success(File("ENG_ACV.spb"), "A Conservative Version", 66, "PD")
        model.retryLastInstall { installed = it }
        settle()

        assertEquals(2, source.installCalls.get())
        assertNull(model.installError)
        assertEquals("ENG_ACV.spb", installed)
    }

    @Test
    fun `a retry with nothing to retry does nothing`() {
        val source = FakeSource()
        val model = vm(source)

        model.retryLastInstall {}
        settle()

        assertEquals(0, source.installCalls.get())
    }

    @Test
    fun `the phase and progress are published while installing and cleared afterwards`() {
        val source = FakeSource()
        source.emitProgress = InstallProgress(InstallPhase.CONVERTING, 0.7f)
        val parked = CompletableDeferred<BibleInstallOutcome>()
        source.parkedInstall = parked
        val model = vm(source)
        val target = module("ENG_ACV")

        model.install(target) {}
        awaitUntil("the install to report progress") { model.installProgress == 0.7f }
        assertEquals(target.key, model.installingKey)
        assertEquals(InstallPhase.CONVERTING, model.installPhase)

        parked.complete(success())
        awaitUntil("the install to finish") { model.installingKey == null }

        assertEquals(0f, model.installProgress)
        assertNull(model.installPhase)
    }

    @Test
    fun `a second click while an install runs is ignored`() {
        val source = FakeSource()
        val parked = CompletableDeferred<BibleInstallOutcome>()
        source.parkedInstall = parked
        val model = vm(source)

        model.install(module("ENG_ACV")) {}
        awaitUntil("the install to start") { model.installingKey != null }
        model.install(module("ENG_KJV")) {}

        parked.complete(success())
        awaitUntil("the install to finish") { model.installingKey == null }

        // Cancelling a conversion midway would leave a half-written file, so the second click must
        // not queue one.
        assertEquals(1, source.installCalls.get())
    }
}
