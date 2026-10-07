package org.churchpresenter.converter.ui

import javax.swing.JFileChooser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The guard every converter file picker opens its chooser through. */
class FilePickerTest {

    @Test
    fun `an approved chooser is approved`() {
        assertTrue(approved { JFileChooser.APPROVE_OPTION })
    }

    @Test
    fun `a cancelled chooser is not`() {
        assertFalse(approved { JFileChooser.CANCEL_OPTION })
    }

    @Test
    fun `a chooser that dies inside Swing counts as cancelled instead of crashing`() {
        // CHURCH-PRESENTER-DESKTOP-9R: the Windows file pane's selection repaint found no cell.
        assertFalse(approved { throw NullPointerException("Cannot read field \"x\" because \"<parameter1>\" is null") })
    }
}
