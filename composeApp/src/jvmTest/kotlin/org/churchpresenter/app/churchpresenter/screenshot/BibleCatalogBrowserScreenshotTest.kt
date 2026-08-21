@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.bibleformats.catalog.BibleCatalogOutcome
import org.churchpresenter.bibleformats.catalog.BibleInstallOutcome
import org.churchpresenter.bibleformats.catalog.BibleModule
import org.churchpresenter.bibleformats.catalog.BibleSource
import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import org.churchpresenter.bibleformats.catalog.InstallProgress
import org.churchpresenter.app.churchpresenter.dialogs.BibleCatalogBrowserDialogContent
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogViewModel
import java.io.File
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * The Download Bibles browser, in both themes.
 *
 * Every state here is driven by a fake [BibleSource] rather than by the real archives: the live
 * catalogues are a network fetch of a thousand-odd translations that changes without notice, so a
 * real one would make these images depend on the day they were recorded — and on the machine having
 * a connection at all. The fake answers with a fixed handful, and can be parked mid-flight to hold
 * the loading and installing states still.
 *
 * The dialog is shot through its `…Content` composable: a `DialogWindow` is an OS window, which a
 * headless test cannot photograph.
 */
class BibleCatalogBrowserScreenshotTest {

    private val created = mutableListOf<BibleCatalogViewModel>()
    private val dirs = mutableListOf<File>()

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dirs.forEach { it.deleteRecursively() }
        dirs.clear()
    }

    // ── The catalogue ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a catalogue of translations`() = shoot("catalogue")

    /** Nothing to show: the archive answered, and had nothing in it. */
    @Test
    fun `an empty catalogue`() = shoot("empty", catalog = BibleCatalogOutcome.Success(emptyList()))

    /** Still fetching — the source is parked and never answers. */
    @Test
    fun `the catalogue loading`() = shoot("loading", parkCatalog = true)

    @Test
    fun `the archive unreachable`() = shoot("network_error", catalog = BibleCatalogOutcome.NetworkError)

    @Test
    fun `the archive refusing requests`() =
        shoot("rate_limited", catalog = BibleCatalogOutcome.RateLimited(resetEpochSeconds = null))

    @Test
    fun `the archive answering with an error`() = shoot("failed", catalog = BibleCatalogOutcome.Failure)

    /** Served from the copy on disk because the archive could not be reached. */
    @Test
    fun `a stale catalogue`() =
        shoot("stale", catalog = BibleCatalogOutcome.Success(MODULES, stale = true))

    // ── Finding one ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `searched by name`() = shoot("search") { typeSearch("Reina") }

    @Test
    fun `a search that finds nothing`() = shoot("search_empty") { typeSearch("zzzz") }

    // ── Installing ──────────────────────────────────────────────────────────────────────────────

    /** Every download is confirmed first, because the licence differs from one translation to the next. */
    @Test
    fun `the licence confirmation`() = shoot("confirm_install") { install(0) }

    /** Parked mid-install, so the progress bar and its phase hold still. */
    @Test
    fun `an install running`() = shoot(
        "installing",
        parkInstall = true,
        progress = InstallProgress(InstallPhase.CONVERTING, 0.45f),
    ) {
        install(0)
        confirm()
    }

    @Test
    fun `an install that failed`() = shoot(
        "install_failed",
        installOutcome = BibleInstallOutcome.NetworkError,
    ) {
        install(0)
        confirm()
    }

    /** The one failure that offers a way out: a slow link, and a Retry beside the message. */
    @Test
    fun `an install that kept stopping`() = shoot(
        "install_stalled",
        installOutcome = BibleInstallOutcome.DownloadStalled,
    ) {
        install(0)
        confirm()
    }

    /** One already in the Bible folder is badged rather than offered again. */
    @Test
    fun `one already installed`() = shoot("installed", installedFiles = listOf(MODULES[0].fileName))

    // ── Two archives ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the second archive's tab`() = shootTwoTabs("second_tab") {
        onAllNodesWithText(ZEFANIA)[0].performClick()
        waitForIdle()
    }

    // ── Driving ─────────────────────────────────────────────────────────────────────────────────

    private fun ComposeUiTest.typeSearch(text: String) {
        onAllNodes(hasSetTextAction())[0].performTextReplacement(text)
        settle()
        waitForIdle()
    }

    /** Clicks the download button on the nth row. */
    private fun ComposeUiTest.install(index: Int) {
        onAllNodesWithText(INSTALL)[index].performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.confirm() {
        onAllNodesWithText(CONFIRM)[0].performClick()
        settle()
        waitForIdle()
    }

    /**
     * Lets the view model's work land.
     *
     * It runs on an immediate dispatcher, but the fake still hops the Swing queue on its way back —
     * two passes is what the behaviour suite settles on for the same reason.
     */
    private fun ComposeUiTest.settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    // ── Harness ─────────────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        catalog: BibleCatalogOutcome = BibleCatalogOutcome.Success(MODULES),
        installOutcome: BibleInstallOutcome = installed(),
        parkCatalog: Boolean = false,
        parkInstall: Boolean = false,
        progress: InstallProgress? = null,
        installedFiles: List<String> = emptyList(),
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name) { mode, file ->
        val dir = bibleFolder(installedFiles)
        val source = FakeSource(
            catalogOutcome = catalog,
            installOutcome = installOutcome,
            parkedCatalog = if (parkCatalog) CompletableDeferred() else null,
            parkedInstall = if (parkInstall) CompletableDeferred() else null,
            emitProgress = progress,
        )
        val vm = BibleCatalogViewModel(source, dir.absolutePath, dispatcher = Dispatchers.Unconfined)
            .also { created += it }
        render(listOf(vm), listOf(EBIBLE), mode, file, drive)
    }

    private fun shootTwoTabs(name: String, drive: ComposeUiTest.() -> Unit) =
        stackedThemes(SECTION, name) { mode, file ->
            val dir = bibleFolder(emptyList())
            val models = listOf(MODULES, ZEFANIA_MODULES).map { modules ->
                BibleCatalogViewModel(
                    FakeSource(catalogOutcome = BibleCatalogOutcome.Success(modules)),
                    dir.absolutePath,
                    dispatcher = Dispatchers.Unconfined,
                ).also { created += it }
            }
            render(models, listOf(EBIBLE, ZEFANIA), mode, file, drive)
        }

    private fun render(
        models: List<BibleCatalogViewModel>,
        tabs: List<String>,
        mode: ThemeMode,
        file: File,
        drive: ComposeUiTest.() -> Unit,
    ) = runComposeUiTest {
        setContent {
            ChurchPresenterTheme(themeMode = mode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        BibleCatalogBrowserDialogContent(
                            viewModels = models,
                            tabLabels = tabs,
                            onDismiss = {},
                            onBibleInstalled = {},
                        )
                    }
                }
            }
        }
        settle()
        waitForIdle()
        drive()
        waitForIdle()
        captureTo(file)
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────────

    private class FakeSource(
        val catalogOutcome: BibleCatalogOutcome,
        val installOutcome: BibleInstallOutcome = BibleInstallOutcome.NetworkError,
        val parkedCatalog: CompletableDeferred<BibleCatalogOutcome>? = null,
        val parkedInstall: CompletableDeferred<BibleInstallOutcome>? = null,
        val emitProgress: InstallProgress? = null,
    ) : BibleSource {
        override val sourceId = BibleSourceId.EBIBLE
        override suspend fun catalog(nowMillis: Long) = parkedCatalog?.await() ?: catalogOutcome
        override suspend fun install(
            module: BibleModule,
            targetDir: File,
            onProgress: (InstallProgress) -> Unit,
        ): BibleInstallOutcome {
            emitProgress?.let(onProgress)
            return parkedInstall?.await() ?: installOutcome
        }
    }

    private fun installed() =
        BibleInstallOutcome.Success(File("kjv.spb"), "King James Version", 66, "Public domain")

    /** A real folder, so an already-installed module is badged from the disk as it would be. */
    private fun bibleFolder(installedFiles: List<String>): File {
        val dir = FIXTURES.absoluteFile
        dir.deleteRecursively()
        dir.mkdirs()
        dirs += dir
        installedFiles.forEach { File(dir, it).writeText("##Title:\tInstalled") }
        return dir
    }

    private companion object {
        const val SECTION = "bibleCatalogBrowser"

        const val EBIBLE = "eBible.org"
        const val ZEFANIA = "Zefania"
        /** The row's own button, and the licence dialog's — deliberately different strings. */
        const val INSTALL = "Download"
        const val CONFIRM = "I understand — Download"

        val FIXTURES: File = File("/tmp")
            .takeIf { it.isDirectory }
            ?.let { File(it, "churchpresenter-screenshots/catalog") }
            ?: File(System.getProperty("java.io.tmpdir"), "churchpresenter-screenshots/catalog")

        private fun module(
            identifier: String,
            displayName: String,
            language: String,
            languageName: String,
            copyright: String = "",
            sizeBytes: Long = 1_200_000,
            sourceId: BibleSourceId = BibleSourceId.EBIBLE,
        ) = BibleModule(
            sourceId = sourceId,
            downloadKey = identifier,
            language = language,
            languageName = languageName,
            languageNativeName = "",
            identifier = identifier,
            displayName = displayName,
            fileStem = "${language}_$identifier",
            copyright = copyright,
            sizeBytes = sizeBytes,
        )

        val MODULES = listOf(
            module("KJV", "King James Version", "ENG", "English", "Public domain"),
            module("ASV", "American Standard Version", "ENG", "English", "Public domain"),
            module("RVR60", "Reina-Valera 1960", "SPA", "Spanish", "© 1960 Sociedades Bíblicas Unidas"),
            module("LSG", "Louis Segond 1910", "FRA", "French", "Public domain"),
            module("SYN", "Синодальный перевод", "RUS", "Russian", "Public domain"),
        )

        val ZEFANIA_MODULES = listOf(
            module("ELB", "Elberfelder 1905", "DEU", "German", "Public domain", sourceId = BibleSourceId.ZEFANIA),
            module("UKR", "Українська Біблія", "UKR", "Ukrainian", "Public domain", sourceId = BibleSourceId.ZEFANIA),
        )
    }
}
