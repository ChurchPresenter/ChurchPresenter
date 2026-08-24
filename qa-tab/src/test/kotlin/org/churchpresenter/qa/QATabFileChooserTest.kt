@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Export and import in the History view, and Export & Clear in the clear-all dialog.
 *
 * The tab does not open a file dialog itself — it is handed `chooseExportFile`/`chooseImportFile`,
 * and everything that matters happens around them: building the export text, writing it, parsing an
 * imported line, and the rule that a cancelled or failed save **aborts** a clear rather than losing
 * the questions. Tests supply a temp path for "the operator picked this" and `null` for "cancelled".
 *
 * None of the three flows reports success or failure on screen, so what a test observes is the
 * resulting file and the manager's own state, not a status message.
 *
 * Every flow runs in `coroutineScope.launch` around a *suspend* chooser call, so a click is not a
 * barrier and `waitForIdle()` does not await it. Each test therefore waits for the thing it expects
 * — the file appearing, the queue emptying — rather than assuming the handler had time to run. The
 * cases where the dialog succeeds and the write or read then fails have no positive signal at all:
 * the failure is swallowed into `CrashReporter` inside `withContext(Dispatchers.IO)` and changes
 * nothing observable, so those tests wait on the one thing that is observable and assert the state
 * that must not have moved. That is weaker than the rest of this file, which is why it is written
 * down here.
 *
 * See `QATabTestSupport.kt` for the harness.
 */
class QATabFileChooserTest {

    private fun tempFile(prefix: String, name: String = "questions.txt") =
        File(Files.createTempDirectory(prefix).toFile(), name)

    private fun ComposeUiTest.openHistory() {
        onNodeWithText(QALabel.HISTORY, substring = true).performClick()
        waitForIdle()
    }

    // ── Export from History ──────────────────────────────────────────────────────

    @Test
    fun `exporting history writes every question to the chosen file`() {
        val dest = tempFile("cp-qa-export")

        qaTab(
            seed = { askAll("Exported question"); toggleSession() },
            exportTo = dest.toPath(),
        ) { _, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            waitUntil(timeoutMillis = 2_000) { dest.exists() }

            assertTrue(dest.readText().contains("Exported question"), dest.readText())
        }
    }

    @Test
    fun `the export carries each question's status and time, not just its text`() {
        val dest = tempFile("cp-qa-export-format")

        qaTab(
            seed = { askAll("Exported question"); toggleSession() },
            exportTo = dest.toPath(),
        ) { _, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            waitUntil(timeoutMillis = 2_000) { dest.exists() }

            val line = dest.readText().trim()
            assertTrue(
                Regex("""^\[\d{4}-\d{2}-\d{2} \d{2}:\d{2}] \[\w+] Exported question$""").matches(line),
                "an exported line is \"[date] [status] text\", not \"$line\"",
            )
        }
    }

    @Test
    fun `cancelling the export dialog leaves history untouched`() {
        qaTab(seed = { askAll("stays in history"); toggleSession() }, exportTo = null) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            waitForIdle()

            assertEquals(1, qa.history.size, "cancelling the save dialog must not touch history")
        }
    }

    // ── Import into the live queue ───────────────────────────────────────────────

    @Test
    fun `importing a file adds each line as a new question`() {
        val src = tempFile("cp-qa-import", "import.txt")
        src.writeText("[2024-01-01 10:00] [Pending] Imported one\n[2024-01-01 10:01] [Approved] Imported two\n")

        qaTab(
            seed = { askAll("prior session question"); toggleSession() },
            importFrom = src.toPath(),
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitUntil(timeoutMillis = 2_000) { qa.questions.size == 2 }

            assertTrue(qa.questions.any { it.text == "Imported one" }, "${qa.questions.map { it.text }}")
            assertTrue(qa.questions.any { it.text == "Imported two" })
        }
    }

    @Test
    fun `a line with no bracketed prefix is imported as it stands`() {
        val src = tempFile("cp-qa-import-plain", "import.txt")
        src.writeText("Just a bare question\n")

        // Seeded so History has a row: its actions bar, and so the Import button, is only drawn
        // when there is something in the list.
        qaTab(
            seed = { askAll("prior session question"); toggleSession() },
            importFrom = src.toPath(),
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitUntil(timeoutMillis = 2_000) { qa.questions.size == 1 }

            assertEquals("Just a bare question", qa.questions.single().text)
        }
    }

    @Test
    fun `importing skips blank lines`() {
        val src = tempFile("cp-qa-import-blank", "import.txt")
        src.writeText("[2024-01-01 10:00] [Pending] Kept line\n\n   \n")

        qaTab(
            seed = { askAll("prior session question"); toggleSession() },
            importFrom = src.toPath(),
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitUntil(timeoutMillis = 2_000) { qa.questions.size == 1 }

            assertEquals("Kept line", qa.questions.single().text)
        }
    }

    @Test
    fun `cancelling the import dialog adds nothing`() {
        qaTab(
            seed = { askAll("prior session question"); toggleSession() },
            importFrom = null,
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitForIdle()

            assertTrue(qa.questions.isEmpty())
        }
    }

    @Test
    fun `importing a file that cannot be read adds nothing and does not crash`() {
        val missing = tempFile("cp-qa-import-missing", "gone.txt")

        qaTab(
            seed = { askAll("prior session question"); toggleSession() },
            importFrom = missing.toPath(),
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.IMPORT_FROM_FILE)
            waitForIdle()

            assertTrue(qa.questions.isEmpty(), "a failed read must add nothing")
        }
    }

    @Test
    fun `exporting to a folder that does not exist does not crash`() {
        val dest = tempFile("cp-qa-export-bad", "no/such/folder/questions.txt")

        qaTab(
            seed = { askAll("Exported question"); toggleSession() },
            exportTo = dest.toPath(),
        ) { qa, _, _ ->
            openHistory()
            clickQaLabel(QALabel.EXPORT_TO_FILE)
            waitForIdle()

            assertFalse(dest.exists())
            assertEquals(1, qa.history.size, "the failed export must not touch history")
        }
    }

    // ── Export & Clear from the clear-all dialog ────────────────────────────────

    @Test
    fun `export and clear writes the live queue then clears it`() {
        val dest = tempFile("cp-qa-exportclear")

        qaTab(seed = { askAll("about to be cleared") }, exportTo = dest.toPath()) { qa, output, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            waitUntil(timeoutMillis = 2_000) { qa.questions.isEmpty() }

            assertTrue(dest.readText().contains("about to be cleared"), dest.readText())
            assertEquals(null, output.shownQuestion)
            assertFalse(output.qrShown)
            assertEquals(false, output.liveChanges.last(), "the output was cleared")
        }
    }

    @Test
    fun `cancelling the export-and-clear save dialog leaves the questions untouched`() {
        qaTab(seed = { askAll("must survive") }, exportTo = null) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            waitForIdle()

            assertEquals(1, qa.questions.size, "cancelling the save dialog must abort the clear")
        }
    }

    @Test
    fun `export and clear aborts when the save fails, leaving the queue intact`() {
        val dest = tempFile("cp-qa-exportclear-bad", "no/such/folder/questions.txt")

        qaTab(seed = { askAll("must survive a failed export") }, exportTo = dest.toPath()) { qa, _, _ ->
            clickQaLabel(QALabel.CLEAR_ALL)
            clickQaLabel(QALabel.EXPORT_AND_CLEAR)
            waitForIdle()

            assertFalse(dest.exists())
            assertEquals(1, qa.questions.size, "a failed export must abort the clear rather than lose questions")
        }
    }
}
