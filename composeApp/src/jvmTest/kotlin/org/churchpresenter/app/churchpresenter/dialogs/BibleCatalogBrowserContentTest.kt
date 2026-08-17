@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.app.churchpresenter.data.BibleCatalogOutcome
import org.churchpresenter.app.churchpresenter.data.BibleInstallOutcome
import org.churchpresenter.app.churchpresenter.data.BibleModule
import org.churchpresenter.app.churchpresenter.data.BibleSource
import org.churchpresenter.app.churchpresenter.data.BibleSourceId
import org.churchpresenter.app.churchpresenter.data.InstallPhase
import org.churchpresenter.app.churchpresenter.data.InstallProgress
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogViewModel
import java.io.File
import java.nio.file.Files
import javax.swing.SwingUtilities
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BibleCatalogBrowserContentTest {

    private lateinit var dir: File
    private val created = mutableListOf<BibleCatalogViewModel>()

    @BeforeTest
    fun createDir() {
        dir = Files.createTempDirectory("cp-bible-catalog-dialog-test").toFile()
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        dir.deleteRecursively()
    }

    private class FakeSource(
        var catalogOutcome: BibleCatalogOutcome = BibleCatalogOutcome.Success(emptyList()),
        var installOutcome: BibleInstallOutcome = BibleInstallOutcome.Success(
            File("x.spb"),
            "Installed Title",
            66,
            "public domain",
        ),
        var parkedCatalog: CompletableDeferred<BibleCatalogOutcome>? = null,
        var parkedInstall: CompletableDeferred<BibleInstallOutcome>? = null,
        var emitProgress: InstallProgress? = null,
        /**
         * Puts the installed file in the target folder, as a real source does.
         *
         * Off by default because most tests here only care about what the dialog says. It matters
         * wherever something re-reads the folder afterwards — switching tabs does — since a fake
         * that reported success without leaving a file would look like an install that vanished.
         */
        var writesInstalledFile: Boolean = false,
    ) : BibleSource {
        override val sourceId = BibleSourceId.EBIBLE

        var installCalls = 0
            private set

        override suspend fun catalog(nowMillis: Long) = parkedCatalog?.await() ?: catalogOutcome
        override suspend fun install(
            module: BibleModule,
            targetDir: File,
            onProgress: (InstallProgress) -> Unit,
        ): BibleInstallOutcome {
            installCalls++
            emitProgress?.let(onProgress)
            val outcome = parkedInstall?.await() ?: installOutcome
            if (writesInstalledFile && outcome is BibleInstallOutcome.Success) {
                targetDir.mkdirs()
                File(targetDir, module.fileName).writeText("##Title:\t${module.displayName}")
            }
            return outcome
        }
    }

    private fun module(
        identifier: String = "ACV",
        displayName: String = "A Conservative Version",
        language: String = "ENG",
        languageName: String = "English",
        languageNativeName: String = "",
        fileStem: String = "ENG_ACV",
        copyright: String = "",
        sizeBytes: Long = 0,
        sourceId: BibleSourceId = BibleSourceId.EBIBLE,
    ) = BibleModule(
        sourceId = sourceId,
        downloadKey = identifier,
        language = language,
        languageName = languageName,
        languageNativeName = languageNativeName,
        identifier = identifier,
        displayName = displayName,
        fileStem = fileStem,
        copyright = copyright,
        sizeBytes = sizeBytes,
    )

    private fun settle() = repeat(2) { SwingUtilities.invokeAndWait { } }

    private fun dialog(
        catalogOutcome: BibleCatalogOutcome = BibleCatalogOutcome.Success(emptyList()),
        installOutcome: BibleInstallOutcome = BibleInstallOutcome.Success(
            File("x.spb"),
            "Installed Title",
            66,
            "public domain",
        ),
        source: FakeSource = FakeSource(catalogOutcome, installOutcome),
        block: ComposeUiTest.(dismissed: () -> Int, installed: () -> String?) -> Unit,
    ) {
        val vm = BibleCatalogViewModel(
            source,
            dir.absolutePath,
            dispatcher = Dispatchers.Unconfined,
        ).also { created.add(it) }
        var dismissed = 0
        var installedFileName: String? = null
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BibleCatalogBrowserDialogContent(
                        viewModels = listOf(vm),
                        tabLabels = listOf("eBible.org"),
                        onDismiss = { dismissed++ },
                        onBibleInstalled = { installedFileName = it },
                    )
                }
            }
            settle()
            waitForIdle()
            block({ dismissed }, { installedFileName })
        }
    }

    /**
     * The browser with both archives as tabs, each backed by its own view model over the same Bible
     * folder — which is what makes an install on one of them everyone's business.
     */
    private fun twoTabDialog(
        firstTab: BibleCatalogOutcome,
        secondTab: BibleCatalogOutcome,
        block: ComposeUiTest.(selectSecondTab: ComposeUiTest.() -> Unit) -> Unit,
    ) {
        fun vm(outcome: BibleCatalogOutcome) = BibleCatalogViewModel(
            FakeSource(outcome, writesInstalledFile = true), dir.absolutePath, dispatcher = Dispatchers.Unconfined
        ).also { created.add(it) }

        val models = listOf(vm(firstTab), vm(secondTab))
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BibleCatalogBrowserDialogContent(
                        viewModels = models,
                        tabLabels = listOf("eBible.org", "Zefania"),
                        onDismiss = {},
                        onBibleInstalled = {},
                    )
                }
            }
            settle()
            waitForIdle()
            block {
                onNodeWithText("Zefania").performClick()
                settle()
                waitForIdle()
            }
        }
    }

    @Test
    fun `installing on one tab counts towards the header straight away`() {
        // Every tab lists the same Bible folder, so the count must not wait for the tab that ran the
        // install to be the one being looked at.
        twoTabDialog(
            firstTab = BibleCatalogOutcome.Success(listOf(module(identifier = "ACV", fileStem = "ENG_ACV"))),
            secondTab = BibleCatalogOutcome.Success(
                listOf(module(identifier = "SYN", displayName = "Synodal", language = "RUS", fileStem = "RUS_SYN"))
            ),
        ) { selectSecondTab ->
            onNodeWithText("0 installed").assertExists()

            selectSecondTab()
            onNodeWithText("Download").performClick()
            onNodeWithText("I understand — Download").performClick()
            settle()
            waitForIdle()

            onNodeWithText("1 installed").assertExists()
        }
    }

    @Test
    fun `a Bible installed on one tab shows as installed on the other`() {
        val shared = module(identifier = "ACV", fileStem = "ENG_ACV")
        twoTabDialog(
            firstTab = BibleCatalogOutcome.Success(listOf(shared)),
            secondTab = BibleCatalogOutcome.Success(listOf(shared)),
        ) { selectSecondTab ->
            selectSecondTab()
            onNodeWithText("Download").performClick()
            onNodeWithText("I understand — Download").performClick()
            settle()
            waitForIdle()
            onNodeWithText("OK").performClick()
            waitForIdle()

            // Back on the first tab the same file is on disk, so it must not still offer to download it.
            onNodeWithText("eBible.org").performClick()
            settle()
            waitForIdle()

            onNodeWithText("Re-download").assertExists()
            onNodeWithText("Download").assertDoesNotExist()
        }
    }

    @Test
    fun `an empty catalogue shows the empty-state message`() = dialog { _, _ ->
        onNodeWithText("No Bibles match this filter").assertExists()
    }

    @Test
    fun `a network error shows its own message and a retry button`() =
        dialog(catalogOutcome = BibleCatalogOutcome.NetworkError) { _, _ ->
        onNodeWithText("Couldn't reach the Bible archive — check your connection").assertExists()
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `a listed module shows its name and a Download button`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
    ) { _, _ ->
        onNodeWithText("A Conservative Version").assertExists()
        onNodeWithText("Download").assertExists()
    }

    @Test
    fun `searching filters the module list by name`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(
            module(),
            module(identifier = "KJV", displayName = "King James Version", fileStem = "ENG_KJV"),
        )),
    ) { _, _ ->
        onNodeWithText("Filter by name or language…").performTextInput("King James")
        waitForIdle()

        onNodeWithText("King James Version").assertExists()
        onNodeWithText("A Conservative Version").assertDoesNotExist()
    }

    @Test
    fun `clicking Download opens the licence confirmation`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Copyright and licensing").assertExists()
    }

    @Test
    fun `cancelling the licence confirmation installs nothing`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
    ) { _, installed ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Cancel").performClick()

        onNodeWithText("Copyright and licensing").assertDoesNotExist()
        assertEquals(null, installed())
    }

    @Test
    fun `accepting the licence installs the module and reports success`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
    ) { _, installed ->
        onNodeWithText("Download").performClick()
        onNodeWithText("I understand — Download").performClick()
        settle()
        waitForIdle()

        assertEquals("ENG_ACV.spb", installed())
        onNodeWithText("Installed \"Installed Title\" — 66 books.").assertExists()
    }

    @Test
    fun `dismissing the installed notice closes it`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("I understand — Download").performClick()
        settle()
        waitForIdle()

        onAllNodes(hasText("OK")).onLast().performClick()
        waitForIdle()

        onNodeWithText("Installed \"Installed Title\" — 66 books.").assertDoesNotExist()
    }

    @Test
    fun `an already-installed module shows the Installed badge and a re-download option`() {
        val installedFile = File(dir, "ENG_ACV.spb").apply { writeText("##spDataVersion:\t1\n") }
        dialog(catalogOutcome = BibleCatalogOutcome.Success(listOf(module()))) { _, _ ->
            onNodeWithText("Installed").assertExists()
            onNodeWithText("Re-download").assertExists()
        }
        installedFile.delete()
    }

    @Test
    fun `the Done button dismisses the dialog`() = dialog { dismissed, _ ->
        onNodeWithText("Done").performClick()
        assertEquals(1, dismissed())
    }

    @Test
    fun `switching tabs shows the other archive's catalogue`() {
        val ebible =
            FakeSource(
                catalogOutcome = BibleCatalogOutcome.Success(listOf(module(displayName = "A Conservative Version"))),
            )
        val zefania = FakeSource(
            catalogOutcome = BibleCatalogOutcome.Success(
                listOf(module(
                    identifier = "ZEF",
                    displayName = "Zefania Sample",
                    fileStem = "ENG_ZEF",
                    sourceId = BibleSourceId.ZEFANIA,
                ))
            )
        )
        val vm1 = BibleCatalogViewModel(
            ebible,
            dir.absolutePath,
            dispatcher = Dispatchers.Unconfined,
        ).also { created.add(it) }
        val vm2 = BibleCatalogViewModel(
            zefania,
            dir.absolutePath,
            dispatcher = Dispatchers.Unconfined,
        ).also { created.add(it) }
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BibleCatalogBrowserDialogContent(
                        viewModels = listOf(vm1, vm2),
                        tabLabels = listOf("eBible.org", "Zefania Archive"),
                        onDismiss = {},
                        onBibleInstalled = {},
                    )
                }
            }
            settle()
            waitForIdle()
            onNodeWithText("A Conservative Version").assertExists()

            onNodeWithText("Zefania Archive").performClick()
            settle()
            waitForIdle()

            onNodeWithText("Zefania Sample").assertExists()
            onNodeWithText("A Conservative Version").assertDoesNotExist()
        }
    }

    @Test
    fun `while the catalogue is loading a spinner is shown`() {
        val parked = CompletableDeferred<BibleCatalogOutcome>()
        dialog(source = FakeSource(parkedCatalog = parked)) { _, _ ->
            onNodeWithText("Loading the list of Bibles…").assertExists()

            parked.complete(BibleCatalogOutcome.Success(emptyList()))
            settle()
            waitForIdle()

            onNodeWithText("Loading the list of Bibles…").assertDoesNotExist()
        }
    }

    @Test
    fun `clicking Retry reloads the catalogue`() = dialog(catalogOutcome = BibleCatalogOutcome.Failure) { _, _ ->
        onNodeWithText("Couldn't load the list of Bibles. Please try again.").assertExists()

        onNodeWithText("Retry").performClick()
        waitForIdle()

        onNodeWithText("Couldn't load the list of Bibles. Please try again.").assertExists()
    }

    @Test
    fun `a rate-limited catalogue shows its own message`() =
        dialog(catalogOutcome = BibleCatalogOutcome.RateLimited(null)) { _, _ ->
        onNodeWithText("GitHub is limiting requests right now. Please try again in a few minutes.").assertExists()
    }

    @Test
    fun `a stale catalogue shows the offline notice`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(emptyList(), stale = true),
    ) { _, _ ->
        onNodeWithText("Showing the Bible list from your last visit — you appear to be offline.").assertExists()
    }

    @Test
    fun `a module with copyright and file size shows both in the list`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module(
            copyright = "Public Domain",
            sizeBytes = 2L * 1024 * 1024,
        ))),
    ) { _, _ ->
        onNodeWithText("Public Domain").assertExists()
        onNodeWithText("2.0 MB", substring = true).assertExists()
    }

    private val twoLanguages = BibleCatalogOutcome.Success(
        listOf(
            module(
                identifier = "ACV", displayName = "A Conservative Version",
                language = "ENG", languageName = "English", fileStem = "ENG_ACV"
            ),
            module(
                identifier = "RVA", displayName = "Reina Valera",
                language = "SPA", languageName = "Spanish", fileStem = "SPA_RVA"
            ),
        )
    )

    @Test
    fun `picking a language from the dropdown filters the list`() = dialog(catalogOutcome = twoLanguages) { _, _ ->
        onNodeWithText("All languages").performClick()
        waitForIdle()
        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).performClick()
        waitForIdle()

        onNodeWithText("Reina Valera").assertExists()
        onNodeWithText("A Conservative Version").assertDoesNotExist()
    }

    /**
     * Opens the language menu and returns its text field.
     *
     * "All languages" is on screen twice once the menu is open — the field and the first option —
     * and the dialog's own search box is editable too, so the field is identified as the editable
     * one holding focus, which the click just gave it.
     */
    private fun ComposeUiTest.openLanguageMenu(): SemanticsNodeInteraction {
        onNodeWithText("All languages").performClick()
        waitForIdle()
        return onNode(isEditable() and isFocused())
    }

    @Test
    fun `no clear button is shown until a language has been picked`() = dialog(catalogOutcome = twoLanguages) { _, _ ->
        // "All languages" is the unfiltered state, so there is nothing there to undo.
        onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun `clearing the language filter puts every translation back`() = dialog(catalogOutcome = twoLanguages) { _, _ ->
        openLanguageMenu()
        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).performClick()
        waitForIdle()
        onNodeWithText("A Conservative Version").assertDoesNotExist()

        onNodeWithContentDescription("Clear").performClick()
        waitForIdle()

        onNodeWithText("A Conservative Version").assertExists()
        onNodeWithText("Reina Valera").assertExists()
        onNodeWithText("All languages").assertExists()
        onNodeWithContentDescription("Clear").assertDoesNotExist()
    }

    @Test
    fun `the language dropdown lists every language until something is typed`() =
        dialog(catalogOutcome = twoLanguages) { _, _ ->
        openLanguageMenu()

        // Focus clears the field, so the menu opens on the whole list rather than on the one row
        // the current pick happens to match.
        onNode(hasTextExactly("English · ENG (1)") and hasClickAction()).assertExists()
        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).assertExists()
    }

    @Test
    fun `typing the English name narrows the language dropdown`() = dialog(catalogOutcome = twoLanguages) { _, _ ->
        openLanguageMenu().performTextInput("span")
        waitForIdle()

        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).assertExists()
        onNode(hasTextExactly("English · ENG (1)") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `typing the language code narrows the dropdown just as the name does`() =
        dialog(catalogOutcome = twoLanguages) { _, _ ->
        openLanguageMenu().performTextInput("spa")
        waitForIdle()

        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).assertExists()
        onNode(hasTextExactly("English · ENG (1)") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `typing into the language field replaces the current pick rather than editing it`() = dialog(
        catalogOutcome = twoLanguages,
    ) { _, _ ->
        // Clicking places a caret mid-word, so a field that kept its text would splice the
        // keystrokes into "All languages" and match nothing.
        openLanguageMenu().performTextInput("spanish")
        waitForIdle()

        onNode(hasTextExactly("Spanish · SPA (1)") and hasClickAction()).performClick()
        waitForIdle()

        onNodeWithText("Reina Valera").assertExists()
        onNodeWithText("A Conservative Version").assertDoesNotExist()
    }

    @Test
    fun `a language whose own name differs shows both spellings`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(
                module(
                    identifier = "SYN", displayName = "Synodal", language = "RUS",
                    languageName = "Russian", languageNativeName = "русский", fileStem = "RUS_SYN"
                )
            )
        ),
    ) { _, _ ->
        openLanguageMenu()

        onNode(hasTextExactly("Russian · русский · RUS (1)") and hasClickAction()).assertExists()
    }

    @Test
    fun `an autonym that only repeats the English name is not shown twice`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(
                module(
                    identifier = "ZAM", displayName = "Zamenhof", language = "EPO",
                    // Differing only in case still counts as the same name.
                    languageName = "Esperanto", languageNativeName = "esperanto", fileStem = "EPO_ZAM"
                )
            )
        ),
    ) { _, _ ->
        openLanguageMenu()

        onNode(hasTextExactly("Esperanto · EPO (1)") and hasClickAction()).assertExists()
    }

    @Test
    fun `typing a language's own name narrows the dropdown`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(
                module(
                    identifier = "ACV", displayName = "A Conservative Version", language = "ENG",
                    languageName = "English", fileStem = "ENG_ACV"
                ),
                module(
                    identifier = "SYN", displayName = "Synodal", language = "RUS",
                    languageName = "Russian", languageNativeName = "русский", fileStem = "RUS_SYN"
                ),
            )
        ),
    ) { _, _ ->
        openLanguageMenu().performTextInput("рус")
        waitForIdle()

        onNode(hasTextExactly("Russian · русский · RUS (1)") and hasClickAction()).assertExists()
        onNode(hasTextExactly("English · ENG (1)") and hasClickAction()).assertDoesNotExist()
    }

    @Test
    fun `a language with no published name falls back to its bare code`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(module(
                identifier = "CSP",
                displayName = "Cesky",
                language = "CZE",
                languageName = "",
                fileStem = "CZE_CSP",
            ))
        ),
    ) { _, _ ->
        onNodeWithText("All languages").performClick()
        waitForIdle()

        onNode(hasTextExactly("CZE (1)") and hasClickAction()).assertExists()
    }

    @Test
    fun `the licence dialog names the module's own copyright and the archive's licence note`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module(copyright = "Public Domain"))),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Copyright").assertExists()
        // The row behind the dialog still shows its own copyright line, so this text now
        // appears twice: once in the list row, once in the dialog's metadata field.
        onAllNodes(hasText("Public Domain")).assertCountEquals(2)
        onNodeWithText(
            "eBible.org lists this translation as redistributable and publishes the copyright shown above."
        ).assertExists()
    }

    @Test
    fun `the licence dialog explains that Zefania publishes no licence details`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module(sourceId = BibleSourceId.ZEFANIA))),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("community-contributed", substring = true).assertExists()
    }

    @Test
    fun `the licence dialog explains that the Holy Bible XML archive has not checked its copyrights`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(module(sourceId = BibleSourceId.BEBLIA, copyright = "Public Domain"))
        ),
    ) { _, _ ->
        onNodeWithText("Download").performClick()

        // Unlike Zefania, this archive states a copyright up front — and still cannot vouch for it,
        // which is why the badge is the unverified one rather than "Redistributable".
        onNodeWithText("Licence unverified".uppercase()).assertExists()
        onNodeWithText("none of the statements have been checked", substring = true).assertExists()
    }

    @Test
    fun `the licence dialog warns when book names will come out in English`() = dialog(
        // Mizo: real verse text, but no book-name table in the app, and the archive's files name
        // books by number alone.
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(module(sourceId = BibleSourceId.BEBLIA, language = "LUS", languageName = "Lushai"))
        ),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Book names will appear in English", substring = true).assertExists()
    }

    @Test
    fun `a translation in a language the app has book names for gets no such warning`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(module(sourceId = BibleSourceId.BEBLIA, language = "ENG"))
        ),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Book names will appear in English", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the warning is only about that archive, not about every source`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(
            listOf(module(sourceId = BibleSourceId.ZEFANIA, language = "LUS"))
        ),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("Book names will appear in English", substring = true).assertDoesNotExist()
    }

    @Test
    fun `a third archive gets its own tab, listing its own translations`() {
        fun vm(outcome: BibleCatalogOutcome) = BibleCatalogViewModel(
            FakeSource(outcome, writesInstalledFile = true), dir.absolutePath, dispatcher = Dispatchers.Unconfined
        ).also { created.add(it) }

        val models = listOf(
            vm(BibleCatalogOutcome.Success(listOf(module(displayName = "From eBible", fileStem = "ENG_A")))),
            vm(BibleCatalogOutcome.Success(listOf(module(displayName = "From Zefania", fileStem = "ENG_B")))),
            vm(BibleCatalogOutcome.Success(listOf(module(displayName = "From Holy Bible XML", fileStem = "ENG_C")))),
        )
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    BibleCatalogBrowserDialogContent(
                        viewModels = models,
                        tabLabels = listOf("eBible.org", "Zefania Archive", "Holy Bible XML"),
                        onDismiss = {},
                        onBibleInstalled = {},
                    )
                }
            }
            settle()
            waitForIdle()

            onNodeWithText("From eBible").assertExists()
            onNodeWithText("From Holy Bible XML").assertDoesNotExist()

            onNodeWithText("Holy Bible XML").performClick()
            settle()
            waitForIdle()

            onNodeWithText("From Holy Bible XML").assertExists()
            onNodeWithText("From eBible").assertDoesNotExist()
        }
    }

    @Test
    fun `re-downloading an installed module warns before overwriting it`() {
        val installedFile = File(dir, "ENG_ACV.spb").apply { writeText("##spDataVersion:\t1\n") }
        dialog(catalogOutcome = BibleCatalogOutcome.Success(listOf(module()))) { _, _ ->
            onNodeWithText("Re-download").performClick()
            onNodeWithText("is already in your Bible folder", substring = true).assertExists()
        }
        installedFile.delete()
    }

    @Test
    fun `an install with no stated rights omits the rights line`() = dialog(
        catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
        installOutcome = BibleInstallOutcome.Success(File(dir, "ENG_ACV.spb"), "Installed Title", 66, ""),
    ) { _, _ ->
        onNodeWithText("Download").performClick()
        onNodeWithText("I understand — Download").performClick()
        settle()
        waitForIdle()

        onNodeWithText("Installed \"Installed Title\" — 66 books.").assertExists()
        onNodeWithText("Copyright:", substring = true).assertDoesNotExist()
    }

    @Test
    fun `each install phase shows its own label while installing`() {
        val phases = listOf(
            InstallPhase.DOWNLOADING to "Downloading…",
            InstallPhase.EXTRACTING to "Extracting…",
            InstallPhase.CONVERTING to "Converting…",
            InstallPhase.INSTALLING to "Installing…",
        )
        phases.forEach { (phase, label) ->
            val parked = CompletableDeferred<BibleInstallOutcome>()
            val source = FakeSource(
                catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
                parkedInstall = parked,
                emitProgress = InstallProgress(phase, 0.5f),
            )
            dialog(source = source) { _, _ ->
                onNodeWithText("Download").performClick()
                onNodeWithText("I understand — Download").performClick()
                waitForIdle()

                onNodeWithText(label).assertExists()

                parked.complete(BibleInstallOutcome.NetworkError)
                settle()
                waitForIdle()
            }
        }
    }

    @Test
    fun `a stalled download offers a retry that installs on the second try`() {
        val source = FakeSource(
            catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
            installOutcome = BibleInstallOutcome.DownloadStalled,
        )
        dialog(source = source) { _, _ ->
            onNodeWithText("Download").performClick()
            onNodeWithText("I understand — Download").performClick()
            settle()
            waitForIdle()

            source.installOutcome = BibleInstallOutcome.Success(File("x.spb"), "Installed Title", 66, "")
            onNodeWithText("Retry").performClick()
            settle()
            waitForIdle()

            assertEquals(2, source.installCalls)
            onNodeWithText("Installed \"Installed Title\" — 66 books.").assertExists()
        }
    }

    @Test
    fun `only a stalled download offers a retry`() {
        // A conversion failure will fail the same way next time; offering to repeat it wastes a wait.
        dialog(
            catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
            installOutcome = BibleInstallOutcome.ConversionFailed,
        ) { _, _ ->
            onNodeWithText("Download").performClick()
            onNodeWithText("I understand — Download").performClick()
            settle()
            waitForIdle()

            onNodeWithText("Retry").assertDoesNotExist()
        }
    }

    @Test
    fun `each install failure shows its own message`() {
        val cases = listOf(
            BibleInstallOutcome.NetworkError to "Download failed — check your connection",
            BibleInstallOutcome.DownloadStalled to
                "The download kept stopping — your connection may be slow or restricted.",
            BibleInstallOutcome.HttpError(500) to "Download failed. Please try again.",
            BibleInstallOutcome.ChecksumMismatch to "The download was incomplete and was discarded. Please try again.",
            BibleInstallOutcome.CorruptArchive to
                "The downloaded file was damaged and was discarded. Please try again.",
            BibleInstallOutcome.ConversionFailed to "This Bible couldn't be converted and was not installed.",
            BibleInstallOutcome.WriteFailed to
                "Couldn't write to your Bible folder — check it still exists and has space.",
            BibleInstallOutcome.NoDirectory to "Choose a Bible storage folder in Appearance settings first.",
        )
        cases.forEach { (outcome, message) ->
            dialog(
                catalogOutcome = BibleCatalogOutcome.Success(listOf(module())),
                installOutcome = outcome,
            ) { _, _ ->
                onNodeWithText("Download").performClick()
                onNodeWithText("I understand — Download").performClick()
                settle()
                waitForIdle()

                onNodeWithText(message).assertExists()
            }
        }
    }
}
