package org.churchpresenter.showcontrol

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * An action's `type` is what a stored macro, a cue's action list and a MIDI/OSC mapping are written
 * in, so both references have to name every one. Fails when an action is added without its row in
 * `docs/SHOW_CONTROL.md` and `docs/CONTROL_IN.md`.
 */
class ActionsDocumentedTest {

    private fun doc(name: String): String =
        generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/$name") }
            .first { it.isFile }
            .readText()

    private fun assertDocumentsEveryAction(name: String) {
        val text = doc(name)
        val missing = ActionSerializer.typeNames.filter { "`$it`" !in text }

        assertTrue(missing.isEmpty(), "docs/$name does not document the action types: $missing")
    }

    @Test
    fun `the show control reference names every action type`() = assertDocumentsEveryAction("SHOW_CONTROL.md")

    @Test
    fun `the control-in reference names every action type`() = assertDocumentsEveryAction("CONTROL_IN.md")
}
