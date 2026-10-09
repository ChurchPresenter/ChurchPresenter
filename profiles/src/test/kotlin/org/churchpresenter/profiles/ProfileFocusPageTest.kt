package org.churchpresenter.profiles

import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which page of a profile a Wick tour opens Settings on. */
class ProfileFocusPageTest {

    private val screen = OutputProfile(id = "p", name = "Main")
    private val stage = OutputProfile(id = "s", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR)
    private val songs = ProfilePage.Appearance(CustomizePane.SONGS)
    private val background = ProfilePage.Appearance(CustomizePane.BACKGROUND)

    @Test
    fun `a named page is opened, when it lists the row or no row is named`() {
        assertEquals(ProfilePage.General, focusedPage(screen, "GENERAL", null))
        assertEquals(ProfilePage.Outputs, focusedPage(screen, "OUTPUTS", null))
        assertEquals(ProfilePage.Content, focusedPage(screen, "CONTENT", null))
        assertEquals(background, focusedPage(screen, "BACKGROUND", ROW))
        assertEquals(ProfilePage.General, focusedPage(screen, "GENERAL", "profile_name"))
    }

    @Test
    fun `a row on another page wins over the page named`() {
        assertEquals(ProfilePage.General, focusedPage(screen, "SONGS", "profile_name"))
    }

    @Test
    fun `with no page named, the first page listing the row is opened`() {
        assertEquals(ProfilePage.General, focusedPage(screen, null, "profile_name"))
        assertEquals(background, focusedPage(screen, null, ROW))
        assertEquals(background, focusedPage(screen, "BIBLE", ROW))
    }

    @Test
    fun `a page this profile does not have, with a row nobody lists, leads nowhere`() {
        assertNull(focusedPage(screen, null, null))
        assertNull(focusedPage(screen, "GONE", "not_a_string_key"))
        assertNull(focusedPage(stage, "SONGS", null))
        assertEquals(ProfilePage.General, focusedPage(screen, "GENERAL", "not_a_string_key"))
    }

    @Test
    fun `every page has the name a tour gives it`() {
        assertEquals("GENERAL", ProfilePage.General.stableName())
        assertEquals("OUTPUTS", ProfilePage.Outputs.stableName())
        assertEquals("CONTENT", ProfilePage.Content.stableName())
        assertEquals("SONGS", songs.stableName())
    }

    private companion object {
        const val ROW = "background_above_band_caption"
    }
}
