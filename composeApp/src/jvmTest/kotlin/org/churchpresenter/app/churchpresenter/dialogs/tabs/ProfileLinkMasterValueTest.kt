package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.settings.OutputProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileLinkMasterValueTest {

    private val master = OutputProfile(id = "m", name = "Sanctuary", displayMode = "lowerThird")

    private fun link(
        profile: OutputProfile = OutputProfile(id = "p", name = "Lobby"),
        follows: OutputProfile? = master,
    ) =
        ProfileLink(
            profile = profile,
            master = follows,
            sectionMasters = emptyMap(),
            followers = emptyList(),
            onlyChanges = false,
            onRevert = {},
        )

    @Test
    fun `a followed row reads the master's value`() {
        assertEquals("lowerThird", link().masterValue(listOf("displayMode"), "On", "Off"))
    }

    @Test
    fun `a row the profile has taken over still shows what the master holds`() {
        val own = OutputProfile(id = "p", name = "Lobby", displayMode = "fullscreen", overrides = setOf("displayMode"))
        assertEquals("lowerThird", link(profile = own).masterValue(listOf("displayMode"), "On", "Off"))
    }

    @Test
    fun `a profile that follows nothing has no master value to show`() {
        assertNull(link(follows = null).masterValue(listOf("displayMode"), "On", "Off"))
    }

    @Test
    fun `a row with no paths, or a path the master does not store, shows nothing`() {
        assertNull(link().masterValue(emptyList(), "On", "Off"))
        assertNull(link().masterValue(listOf("noSuchSetting"), "On", "Off"))
    }

    @Test
    fun `a blank master value is not shown as one`() {
        val blank = link(follows = master.copy(name = ""))
        assertNull(blank.masterValue(listOf("name"), "On", "Off"))
    }
}
