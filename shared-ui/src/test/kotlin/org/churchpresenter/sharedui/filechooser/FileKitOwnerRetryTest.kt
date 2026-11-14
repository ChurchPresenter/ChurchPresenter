package org.churchpresenter.sharedui.filechooser

import io.github.vinceglb.filekit.dialogs.FileKitDialogParent
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import java.awt.Window
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FileKitOwnerRetryTest {

    // A window cannot be built headless; FileKit only holds on to it here, so a stand-in does.
    private val owned: (String) -> FileKitDialogSettings = { title ->
        FileKitDialogSettings(title = title, parent = FileKitDialogParent.awt(mockk<Window>(relaxed = true)))
    }

    @Test
    fun `an owner FileKit cannot resolve is dropped and the dialog opens unowned`() = runBlocking {
        val seen = mutableListOf<FileKitDialogSettings>()

        val result = FileKitFileChooser.withOwnerRetry("Open", owned) { settings ->
            seen += settings
            require(settings.parent == null) { "no usable HWND" }
            "picked"
        }

        assertEquals("picked", result)
        assertNotNull(seen[0].parent)
        assertNull(seen[1].parent)
        assertEquals("Open", seen[1].title)
    }

    @Test
    fun `a dialog with no owner to drop fails as before`() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                FileKitFileChooser.withOwnerRetry("Open", { FileKitDialogSettings(title = it) }) {
                    throw IllegalArgumentException("something else")
                }
            }
        }
    }

    @Test
    fun `a dialog that opens with its owner opens once`() = runBlocking {
        var calls = 0
        FileKitFileChooser.withOwnerRetry("Save", owned) { calls++ }
        assertEquals(1, calls)
    }
}
