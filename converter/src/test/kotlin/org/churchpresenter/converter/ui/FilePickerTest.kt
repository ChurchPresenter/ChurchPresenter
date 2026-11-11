package org.churchpresenter.converter.ui

import kotlinx.coroutines.runBlocking
import javax.swing.JFileChooser
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `on Windows the chooser is warmed once, off the event thread`() = runBlocking {
        val threads = mutableListOf<String>()
        val warmUp = FileChooserWarmUp(os = "Windows 11") { threads += Thread.currentThread().name }

        warmUp.run()
        warmUp.run()

        assertEquals(1, threads.size, "a run warms the chooser once")
        assertTrue(threads.single().startsWith("DefaultDispatcher"), "on the IO pool, not ${threads.single()}")
    }

    @Test
    fun `elsewhere the chooser is not warmed`() = runBlocking {
        var built = false

        FileChooserWarmUp(os = "Mac OS X") { built = true }.run()

        assertFalse(built, "only the Windows shell is slow to answer the places bar")
    }

    @Test
    fun `a chooser that dies inside Swing counts as cancelled instead of crashing`() {
        // CHURCH-PRESENTER-DESKTOP-9R: the Windows file pane's selection repaint found no cell.
        assertFalse(approved { throw NullPointerException("Cannot read field \"x\" because \"<parameter1>\" is null") })
    }
}
