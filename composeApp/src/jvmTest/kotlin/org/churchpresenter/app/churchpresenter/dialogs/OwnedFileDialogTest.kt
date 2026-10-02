package org.churchpresenter.app.churchpresenter.dialogs

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OwnedFileDialogTest {

    @Test
    fun `the calendar's file dialogs are owned by it on macOS and Windows, not on Linux`() {
        assertTrue(usesOwnedFileDialog("Mac OS X"))
        assertTrue(usesOwnedFileDialog("Windows 11"))
        assertFalse(usesOwnedFileDialog("Linux"))
    }
}
