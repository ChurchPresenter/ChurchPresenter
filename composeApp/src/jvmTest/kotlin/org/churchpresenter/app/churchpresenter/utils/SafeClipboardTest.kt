package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import kotlinx.coroutines.runBlocking
import java.awt.datatransfer.StringSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [SafeClipboard]'s one contract: the failure Compose's own paste path does not survive becomes an
 * answer instead of an exception, and nothing else changes.
 *
 * The reported crash was `TextFieldSelectionManager.paste` -> `AwtPlatformClipboard.getClipEntry`
 * -> `WClipboard.openClipboard0`, at **fatal** — an operator pressed Ctrl+V and the app died. The
 * delegate here throws exactly what Windows throws, so these exercise the real shape rather than a
 * simulated one, on every platform.
 */
@OptIn(ExperimentalComposeUiApi::class)
class SafeClipboardTest {

    private companion object {
        const val WINDOWS_MESSAGE = "cannot open system clipboard"
    }

    private class Throwing : Clipboard {
        // The exact exception `sun.awt.windows.WClipboard.openClipboard0` raises. `error()` is
        // what detekt asks for and produces the same IllegalStateException.
        override suspend fun getClipEntry(): ClipEntry = error(WINDOWS_MESSAGE)

        override suspend fun setClipEntry(clipEntry: ClipEntry?): Unit = error(WINDOWS_MESSAGE)

        override val nativeClipboard: Any get() = "native"
    }

    private class Recording(var entry: ClipEntry? = null) : Clipboard {
        var setCalls = 0
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            setCalls++
            entry = clipEntry
        }
        override val nativeClipboard: Any get() = "native"
    }

    @Test
    fun `a clipboard another application is holding yields null instead of killing the app`() =
        runBlocking {
            assertNull(SafeClipboard(Throwing()).getClipEntry())
        }

    @Test
    fun `a copy onto a locked clipboard is dropped rather than thrown`() = runBlocking {
        val outcome = runCatching {
            SafeClipboard(Throwing()).setClipEntry(ClipEntry(StringSelection("a verse")))
        }

        assertTrue(outcome.isSuccess, "got ${outcome.exceptionOrNull()}")
    }

    @Test
    fun `a working clipboard is delegated to untouched`() = runBlocking {
        val real = Recording()
        val safe = SafeClipboard(real)
        val entry = ClipEntry(StringSelection("a verse"))

        safe.setClipEntry(entry)

        assertEquals(1, real.setCalls, "the write reached the real clipboard")
        assertEquals(entry, safe.getClipEntry(), "and reading gives back what was written")
    }

    @Test
    fun `clearing the clipboard is still passed through`() = runBlocking {
        // Guarding must not turn into "skip the call when there is nothing to write" — clearing is
        // a thing the platform asks for, and null is how it asks.
        val real = Recording(entry = ClipEntry(StringSelection("stale")))

        SafeClipboard(real).setClipEntry(null)

        assertEquals(1, real.setCalls)
        assertNull(real.entry)
    }

    @Test
    fun `the native clipboard is handed over as-is`() {
        // Wrapping it would misrepresent what the caller was given; the failure this class exists
        // for happens on use, not on access.
        assertEquals("native", SafeClipboard(Recording()).nativeClipboard)
    }
}
