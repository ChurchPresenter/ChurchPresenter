package org.churchpresenter.profiles

import kotlinx.serialization.json.JsonPrimitive
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSection
import kotlin.test.Test
import kotlin.test.assertEquals

/** The lines of a linked profile's changes card, each against the master it is changed from. */
class ChangeLinesTest {

    private val sanctuary = OutputProfile(id = "s", name = "Sanctuary", displayMode = "lowerThird")
    private val livestream = OutputProfile(id = "l", name = "Livestream", bibleSettings = BibleSettings(marginTop = 7))

    private fun link(overrides: Set<String>) = ProfileLink(
        profile = OutputProfile(id = "p", name = "Sign", overrides = overrides),
        master = sanctuary,
        sectionMasters = mapOf(ProfileSection.BIBLE to livestream),
        followers = emptyList(),
        onlyChanges = false,
        onRevert = {},
    )

    @Test
    fun `changes are listed in path order`() {
        val lines = link(setOf("displayMode", "bibleSettings.marginTop")).changeLines(sanctuary)

        assertEquals(listOf("bibleSettings.marginTop", "displayMode"), lines.map { it.path })
    }

    @Test
    fun `a section's change is against that section's master`() {
        val line = link(setOf("bibleSettings.marginTop")).changeLines(sanctuary).single()

        assertEquals("Livestream", line.sourceName)
        assertEquals(JsonPrimitive(7), line.sourceValue)
        assertEquals(ProfilePage.Appearance(CustomizePane.BIBLE), line.page)
    }

    @Test
    fun `a change in a section with no master of its own is against the main master`() {
        val line = link(setOf("displayMode")).changeLines(sanctuary).single()

        assertEquals("Sanctuary", line.sourceName)
        assertEquals(JsonPrimitive("lowerThird"), line.sourceValue)
        assertEquals(pageForPath("displayMode"), line.page)
    }
}
