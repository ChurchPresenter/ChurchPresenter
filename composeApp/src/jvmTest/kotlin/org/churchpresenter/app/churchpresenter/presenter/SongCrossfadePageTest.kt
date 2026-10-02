package org.churchpresenter.app.churchpresenter.presenter

import org.churchpresenter.core.models.songs.LyricSection
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [isRestatedAs]: when a section sent again is the slide already up rather than another one. */
class SongCrossfadePageTest {

    private val verse = LyricSection(header = "[Verse 1]", type = "verse", lines = listOf("Amazing grace"))

    @Test
    fun `the same section is restated`() = assertTrue(verse.isRestatedAs(verse.copy()))

    @Test
    fun `the same section with a new capo or tempo is restated`() {
        assertTrue(verse.isRestatedAs(verse.copy(capo = 3)))
        assertTrue(verse.copy(bpm = 90).isRestatedAs(verse.copy(bpm = 120, capo = 2)))
    }

    @Test
    fun `different words are another slide`() =
        assertFalse(verse.isRestatedAs(verse.copy(lines = listOf("How sweet the sound"))))

    @Test
    fun `another section with the same words is another slide`() =
        assertFalse(verse.isRestatedAs(verse.copy(header = "[Verse 2]")))
}
