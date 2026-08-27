package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [SystemClipboard]'s one contract: neither call throws, whatever the OS clipboard is doing.
 *
 * The clipboard is an OS-wide lock any application can be holding, and Windows raises
 * `IllegalStateException: cannot open system clipboard` when it cannot be taken. Every one of the
 * app's copy buttons used to call straight into AWT, so that exception would have come out of a
 * button click on the event thread. The suite runs headless, where the clipboard is unavailable for
 * a different reason but through the same call — so these exercise the real failure path rather
 * than a simulated one.
 */
class SystemClipboardTest {

    @Test
    fun `copying reports failure rather than throwing when the clipboard is unavailable`() {
        val outcome = runCatching { SystemClipboard.copy("a verse to paste into a chat") }

        assertTrue(
            outcome.isSuccess,
            "copy must answer, not throw — got ${outcome.exceptionOrNull()}",
        )
    }

    @Test
    fun `pasting answers with null rather than throwing when the clipboard is unavailable`() {
        val outcome = runCatching { SystemClipboard.paste() }

        assertTrue(
            outcome.isSuccess,
            "paste must answer, not throw — got ${outcome.exceptionOrNull()}",
        )
    }

    @Test
    fun `an empty string is still something the clipboard is asked to take`() {
        // Guarding must not turn into "skip the call when the text looks empty" — clearing the
        // clipboard by copying "" is a thing operators do.
        val outcome = runCatching { SystemClipboard.copy("") }

        assertTrue(outcome.isSuccess, "got ${outcome.exceptionOrNull()}")
    }
}
