package org.churchpresenter.dialogs

import org.churchpresenter.sharedui.testing.ComposeResourceEnvironmentTestSupport
import kotlinx.coroutines.runBlocking
import org.churchpresenter.server.RemoteLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The remote-activity toast's labels, worded on the desktop from the string resources the server
 * cannot reach. Read in the fixed English environment, so each expectation is the English wording.
 */
class RemoteLabelTextTest {

    private fun text(label: RemoteLabel): String =
        ComposeResourceEnvironmentTestSupport.withFixedEnvironment { runBlocking { label.text() } }

    @Test
    fun `a name the remote sent is shown exactly as it came`() {
        assertEquals("Easter Slides", text(RemoteLabel.Text("Easter Slides")))
        assertEquals("", text(RemoteLabel.EMPTY))
    }

    @Test
    fun `a song keeps the number as the song book writes it`() {
        assertEquals("Song 42", text(RemoteLabel.Song("42")))
        assertEquals("Song 12a", text(RemoteLabel.Song("12a")))
    }

    @Test
    fun `sections, slides and images are numbered`() {
        assertEquals("Section 2", text(RemoteLabel.Section(2)))
        assertEquals("Slide 3", text(RemoteLabel.Slide(3)))
        assertEquals("Image 1", text(RemoteLabel.Image(1)))
    }

    @Test
    fun `an upload's size is worded in whole kilobytes, rounded down`() {
        assertEquals("512 KB", text(RemoteLabel.Size(512L * 1024)))
        assertEquals("0 KB", text(RemoteLabel.Size(1023)))
        assertEquals("1 KB", text(RemoteLabel.Size(2047)))
    }
}
