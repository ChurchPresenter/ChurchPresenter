package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A song or Bible section's paths on their own are exactly the ones a whole profile holding that
 * section has under its key, so a page probing a section reads the same paths it would have read
 * from a profile.
 */
class SectionProfilePathsTest {

    @Test
    fun `a song section's paths are the profile's songSettings paths`() {
        val song = SongSettings(marginTop = SongSettings().marginTop + 3)
        val profile = OutputProfile(id = "song", songSettings = song)

        assertEquals(profile.settingPaths().under("songSettings."), song.profilePaths())
    }

    @Test
    fun `a Bible section's paths are the profile's bibleSettings paths`() {
        val bible = BibleSettings(marginTop = BibleSettings().marginTop + 3)
        val profile = OutputProfile(id = "bible", bibleSettings = bible)

        assertEquals(profile.settingPaths().under("bibleSettings."), bible.profilePaths())
    }

    @Test
    fun `a section's paths are all under its own key`() {
        assertTrue(SongSettings().profilePaths().keys.all { it.startsWith("songSettings.") })
        assertTrue(BibleSettings().profilePaths().isNotEmpty())
    }

    private fun Map<String, *>.under(prefix: String) = filterKeys { it.startsWith(prefix) }
}
