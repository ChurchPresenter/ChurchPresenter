@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.CalendarJoin
import org.churchpresenter.settings.CalendarSyncSettings
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarComputerDialogsTest {

    private val folder: File = Files.createTempDirectory("calendar-join-dialogs").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun join(expires: Instant = Instant.now().plus(1, ChronoUnit.DAYS)) = CalendarJoin(
        relay = "https://relay.example",
        instance = "inst-1",
        desktop = "desk-2",
        token = "t".repeat(32),
        key = "A".repeat(43),
        role = CalendarSyncSettings.ROLE_FOLLOWER,
        expires = expires.toString(),
    )

    /** The clipboard, file choosers and mail app, recorded. */
    private inner class FakeIo(
        private val clipboard: String? = null,
        private val opened: Pair<String, File>? = null,
        private val savedTo: String? = null,
    ) : JoinCodeIo {
        val copied = ArrayList<String>()
        val mailed = ArrayList<String>()
        val saved = ArrayList<CalendarJoin>()

        override fun copy(text: String): Boolean = copied.add(text)

        override fun paste(): String? = clipboard

        override suspend fun save(join: CalendarJoin, title: String, filter: FileNameExtensionFilter): String? {
            saved += join
            return savedTo
        }

        override suspend fun open(title: String, filter: FileNameExtensionFilter): Pair<String, File>? = opened

        override fun email(url: String) {
            mailed += url
        }
    }

    @Test
    fun `a computer needs a name before its code is made, then gets the role picked`() = runComposeUiTest {
        val made = ArrayList<Pair<String, String>>()
        val code = join()
        setContent {
            MaterialTheme {
                AddComputerContent(onCreate = { role, name -> code.also { made += role to name } }, onDismiss = {})
            }
        }
        onNodeWithText("Create join code").assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("  Office iMac ")
        onNodeWithText("View only").performClick()
        onNodeWithText("Create join code").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = WAIT_MS) { made.isNotEmpty() }
        assertEquals(listOf(CalendarSyncSettings.ROLE_VIEWER to "Office iMac"), made)
        onNodeWithText("Join code for Office iMac", ignoreCase = true).assertExists()
        onNodeWithText("Send this code to the other computer.").assertExists()
    }

    @Test
    fun `when the relay refuses, the dialog says so and stays on the form`() = runComposeUiTest {
        var dismissed = false
        setContent {
            MaterialTheme {
                AddComputerContent(onCreate = { _, _ -> null }, onDismiss = { dismissed = true })
            }
        }
        onNode(hasSetTextAction()).performTextInput("Office")
        onNodeWithText("Create join code").performClick()
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasText("Couldn't add the computer.")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `the code is copied, saved and mailed through the dialog's own hands, and Done closes it`() =
        runComposeUiTest {
            val io = FakeIo(savedTo = "/Users/me/Shared")
            val code = join()
            var dismissed = false
            setContent {
                MaterialTheme {
                    AddComputerContent(onCreate = { _, _ -> code }, onDismiss = { dismissed = true }, io = io)
                }
            }
            onNodeWithText("Create join code").assertIsNotEnabled()
            onNode(hasSetTextAction()).performTextInput("Office")
            onNodeWithText("Create join code").performClick()
            waitUntil(timeoutMillis = WAIT_MS) {
                onAllNodes(hasText("Copy code")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithText("Copy code").performClick()
            onNodeWithText("Copied").assertExists()
            onNodeWithText("Save as file…").performClick()
            waitUntil(timeoutMillis = WAIT_MS) { io.saved.isNotEmpty() }
            onNodeWithText("Saved to /Users/me/Shared").assertExists()
            onNodeWithText("Email…").performClick()
            onNodeWithText("Done").performClick()
            waitForIdle()
            assertEquals(listOf(code.toCode()), io.copied)
            assertEquals(listOf(code), io.saved)
            assertTrue(io.mailed.single().startsWith("mailto:?subject="))
            assertTrue(io.mailed.single().contains(code.toCode()))
            assertTrue(dismissed)
        }

    @Test
    fun `a pasted code is checked as it is typed, and Join waits for a good one`() = runComposeUiTest {
        val joined = ArrayList<CalendarJoin>()
        var dismissed = false
        setContent {
            MaterialTheme {
                JoinCalendarContent(
                    replacesOwn = false,
                    onJoin = { joined += it; true },
                    onDismiss = { dismissed = true },
                )
            }
        }
        onNodeWithText("Codes start with CPJ-").assertExists()
        onNodeWithText("Join").assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextInput("CPJ-nonsense")
        onNodeWithText("That isn't a join code.").assertExists()
        onNodeWithText("Join").assertIsNotEnabled()
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput(join(Instant.now().minus(1, ChronoUnit.DAYS)).toCode())
        onNodeWithText("That code has expired", substring = true).assertExists()
        onNode(hasSetTextAction()).performTextClearance()
        onNode(hasSetTextAction()).performTextInput(join().toCode())
        onNodeWithText("Looks good").assertExists()
        onNodeWithText("This computer stops syncing its own calendar.").assertDoesNotExist()
        onNodeWithText("Join").assertIsEnabled().performClick()
        waitUntil(timeoutMillis = WAIT_MS) { dismissed }
        assertEquals("desk-2", joined.single().desktop)
    }

    @Test
    fun `Paste takes the clipboard, and a join that fails says so and keeps the dialog`() = runComposeUiTest {
        var dismissed = false
        setContent {
            MaterialTheme {
                JoinCalendarContent(
                    replacesOwn = true,
                    onJoin = { false },
                    onDismiss = { dismissed = true },
                    io = FakeIo(clipboard = "  ${join().toCode()}\n"),
                )
            }
        }
        onNodeWithText("This computer stops syncing its own calendar.").assertExists()
        onNodeWithText("Paste").performClick()
        onNodeWithText("Looks good").assertExists()
        onNodeWithText("Join").performClick()
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasText("Couldn't join", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Cancel").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `a join file that worked is deleted, so it is not left lying in a shared folder`() = runComposeUiTest {
        val file = File(folder, CalendarJoin.FILE_NAME).apply { writeText(join().toJson()) }
        var dismissed = false
        setContent {
            MaterialTheme {
                JoinCalendarContent(
                    replacesOwn = false,
                    onJoin = { true },
                    onDismiss = { dismissed = true },
                    io = FakeIo(opened = file.readText() to file),
                )
            }
        }
        onNodeWithText("Open file…").performClick()
        waitUntil(timeoutMillis = WAIT_MS) {
            onAllNodes(hasText("Looks good")).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("Join").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { dismissed && !file.exists() }
        assertFalse(file.exists())
    }

    @Test
    fun `outside a test a dialog gets its own window, and with no screen the system clipboard has nothing`() =
        runComposeUiTest {
            var inPlace = true
            setContent { inPlace = LocalCalendarDialogInPlace.current }
            waitForIdle()
            assertFalse(inPlace)
            // Headless, as every test runs: the clipboard refuses rather than throwing.
            assertFalse(SystemJoinCodeIo.copy("CPJ-x"))
            assertEquals(null, SystemJoinCodeIo.paste())
        }

    private companion object {
        /** Only there to fail the test: each wait ends as soon as the dialog has moved on. */
        const val WAIT_MS = 5_000L
    }
}
