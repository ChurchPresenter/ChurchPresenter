@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import java.io.File
import java.nio.file.Files
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

/**
 * Export/import in the History view, and Export & Clear in the clear-all dialog — all three drive
 * `FileChooser.platformInstance`, a real native save/open dialog with nothing to click in a headless
 * test. Stood in for the same way `StatisticsContentTest` does for its own save dialog.
 *
 * None of the three report success or failure on screen, so what a test can observe is the
 * resulting file and the manager's own state, not a status message.
 *
 * Every flow runs in `coroutineScope.launch` around a *suspend* chooser call, so a click is not a
 * barrier and `waitForIdle()` does not await it. Tests that expect something to appear wait for that
 * thing; tests that expect nothing to change wait for [FakeChooser.answered] instead, which is a
 * positive signal that the handler ran rather than an assumption that it had time to.
 *
 * That signal is exact for a **cancelled** dialog — the handler's remaining work is
 * `if (path != null)`, so a null answer ends it. For the three cases where the dialog succeeds and
 * the *write or read* then fails there is no such signal: the failure is swallowed into
 * `CrashReporter` inside `withContext(Dispatchers.IO)` and changes nothing observable. Those tests
 * therefore assert at the last point the test can see rather than at true completion, which is
 * weaker than the rest of this file and is why it is written down here.
 *
 * See `QATabTestSupport.kt` for the harness.
 */
class QATabFileChooserTest {

    @AfterTest
    fun cleanUp() {
        unmockkObject(FileChooser.Companion)
    }

    private class FakeChooser(private val picked: String?) : FileChooser() {
        /**
         * Bumped as the dialog answers. Every one of these flows runs in `coroutineScope.launch`
         * around a *suspend* chooser call, and `waitForIdle()` does not await an arbitrary
         * coroutine — so this is the signal a test waits on before reading the result.
         */
        @Volatile
        var answered: Int = 0
            private set

        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean
        ): List<NioPath>? = picked?.let { listOf(Path(it)) }.also { answered++ }

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String
        ): NioPath? = picked?.let { Path(it) }.also { answered++ }
    }

    private fun givenChooserReturns(picked: String?): FakeChooser {
        mockkObject(FileChooser.Companion)
        return FakeChooser(picked).also { every { FileChooser.platformInstance } returns it }
    }

    /**
     * Waits for the dialog to have answered. For a **cancelled** dialog this is the whole story: the
     * handler's remaining work is `if (path != null) { … }`, so once the chooser has returned null
     * the flow is finished and what follows is a settled assertion rather than a race.
     */
    private fun ComposeUiTest.awaitDialogAnswered(chooser: FakeChooser) =
        waitUntil("the file dialog to have been answered", 2_000) { chooser.answered == 1 }

    private fun ComposeUiTest.openHistory() {
        onNodeWithText(QALabel.HISTORY, substring = true).performClick()
        waitForIdle()
    }

    // ── Export from History ──────────────────────────────────────────────────────

    @Test
    fun `exporting history writes every question to the chosen file`() {
        val dest = File(Files.createTempDirectory("cp-qa-export").toFile(), "questions.txt")
        val question = "Exported question"
        givenChooserReturns(dest.absolutePath)

        qaTab(seed = { askAll(question); toggleSession() }) { _, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            waitForIdle()
            // The content, not `exists()`. The export is a `withContext(Dispatchers.IO)
            // { writeText(...) }`, and `writeText` creates the file before it fills it — so
            // waiting on existence returns the instant it is created and the read below can get an
            // empty file. It did, on CI, as an `AssertionError` whose message was the empty string
            // it had just read.
            waitUntil("the export to have been written", 2_000) {
                dest.exists() && dest.readText().contains(question)
            }

            assertTrue(dest.readText().contains(question), dest.readText())
        }
    }

    @Test
    fun `cancelling the export dialog leaves history untouched`() {
        val chooser = givenChooserReturns(null)

        qaTab(seed = { askAll("stays in history"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            awaitDialogAnswered(chooser)

            assertEquals(1, qa.history.size, "cancelling the save dialog must not touch history")
        }
    }

    // ── Import into the live queue ───────────────────────────────────────────────

    @Test
    fun `importing a file adds each line as a new question`() {
        val src = File(Files.createTempDirectory("cp-qa-import").toFile(), "import.txt")
        src.writeText("[2024-01-01 10:00] [Pending] Imported one\n[2024-01-01 10:01] [Approved] Imported two\n")
        givenChooserReturns(src.absolutePath)

        qaTab(seed = { askAll("prior session question"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitForIdle()
            waitUntil(timeoutMillis = 2_000) { qa.questions.size == 2 }

            assertTrue(qa.questions.any { it.text == "Imported one" }, "${qa.questions.map { it.text }}")
            assertTrue(qa.questions.any { it.text == "Imported two" })
        }
    }

    @Test
    fun `importing skips blank lines`() {
        val src = File(Files.createTempDirectory("cp-qa-import-blank").toFile(), "import.txt")
        src.writeText("[2024-01-01 10:00] [Pending] Kept line\n\n   \n")
        givenChooserReturns(src.absolutePath)

        qaTab(seed = { askAll("prior session question"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitForIdle()
            waitUntil(timeoutMillis = 2_000) { qa.questions.size == 1 }

            assertEquals("Kept line", qa.questions.single().text)
        }
    }

    @Test
    fun `cancelling the import dialog adds nothing`() {
        val chooser = givenChooserReturns(null)

        qaTab(seed = { askAll("prior session question"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            awaitDialogAnswered(chooser)

            assertTrue(qa.questions.isEmpty())
        }
    }

    @Test
    fun `importing a file that cannot be read adds nothing and does not crash`() {
        val missing = File(Files.createTempDirectory("cp-qa-import-missing").toFile(), "gone.txt")
        val chooser = givenChooserReturns(missing.absolutePath)

        qaTab(seed = { askAll("prior session question"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            awaitDialogAnswered(chooser)

            assertTrue(qa.questions.isEmpty(), "a failed read must add nothing")
        }
    }

    @Test
    fun `exporting to a folder that does not exist does not crash`() {
        val dest = File(Files.createTempDirectory("cp-qa-export-bad").toFile(), "no/such/folder/questions.txt")
        val chooser = givenChooserReturns(dest.absolutePath)

        qaTab(seed = { askAll("Exported question"); toggleSession() }) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            awaitDialogAnswered(chooser)

            assertFalse(dest.exists())
            assertEquals(1, qa.history.size, "the failed export must not touch history")
        }
    }

    // ── Export & Clear from the clear-all dialog ────────────────────────────────

    @Test
    fun `export and clear writes the live queue then clears it`() {
        val dest = File(Files.createTempDirectory("cp-qa-exportclear").toFile(), "questions.txt")
        givenChooserReturns(dest.absolutePath)

        qaTab(seed = { askAll("about to be cleared") }) { qa, presenter, reports ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            waitForIdle()
            waitUntil(timeoutMillis = 2_000) { qa.questions.isEmpty() }

            assertTrue(dest.readText().contains("about to be cleared"), dest.readText())
            assertEquals(null, presenter.displayedQuestion.value)
            assertFalse(presenter.showQRCodeOnDisplay.value)
            assertEquals(Presenting.NONE, reports.presenting.last())
        }
    }

    @Test
    fun `cancelling the export-and-clear save dialog leaves the questions untouched`() {
        val chooser = givenChooserReturns(null)

        qaTab(seed = { askAll("must survive") }) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            awaitDialogAnswered(chooser)

            assertEquals(1, qa.questions.size, "cancelling the save dialog must abort the clear")
        }
    }

    @Test
    fun `export and clear aborts when the save fails, leaving the queue intact`() {
        val dest = File(Files.createTempDirectory("cp-qa-exportclear-bad").toFile(), "no/such/folder/questions.txt")
        val chooser = givenChooserReturns(dest.absolutePath)

        qaTab(seed = { askAll("must survive a failed export") }) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            awaitDialogAnswered(chooser)

            assertFalse(dest.exists())
            assertEquals(1, qa.questions.size, "a failed export must abort the clear rather than lose questions")
        }
    }
}
