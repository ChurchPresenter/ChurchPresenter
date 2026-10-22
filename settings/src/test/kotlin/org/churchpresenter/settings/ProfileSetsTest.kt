package org.churchpresenter.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Profile sets: which profile every output uses, saved under a name and put back in one click. */
class ProfileSetsTest {

    private val profiles = listOf(OutputProfile(id = "fs"), OutputProfile(id = "ltd"), OutputProfile(id = "vert"))

    private fun proj(vararg screens: String, ndi: List<String> = emptyList()) = ProjectionSettings(
        outputProfiles = profiles,
        screenAssignments = screens.map { ScreenAssignment(activeProfileId = it) },
        ndiOutputs = ndi.map { ScreenAssignment(activeProfileId = it) },
    )

    private fun ProjectionSettings.used() =
        (screenAssignments + browserSourceOutputs + ndiOutputs + omtOutputs).map { it.activeProfileId }

    @Test
    fun `a saved set holds every output's profile, each kind by its own key`() {
        val withOmt = proj("fs", "vert", ndi = listOf("ltd")).copy(
            browserSourceOutputs = listOf(ScreenAssignment(activeProfileId = "ltd")),
            omtOutputs = listOf(ScreenAssignment(activeProfileId = "fs")),
        )
        val set = withOmt.withProfileSetSaved("Sunday").profileSets.single()
        assertEquals("Sunday", set.name)
        assertEquals(
            mapOf(
                "screen:0" to "fs", "screen:1" to "vert", "browserSource:0" to "ltd", "ndi:0" to "ltd", "omt:0" to "fs",
            ),
            set.profiles,
        )
    }

    @Test
    fun `an output with no profile is left out of the set`() {
        val start = proj("fs").copy(
            screenAssignments = listOf(ScreenAssignment(), ScreenAssignment(activeProfileId = "fs")),
        )
        assertEquals(mapOf("screen:1" to "fs"), start.withProfileSetSaved("").profileSets.single().profiles)
    }

    @Test
    fun `each new set has an id no other set has`() {
        val two = proj("fs").withProfileSetSaved("a").withProfileSetSaved("b")
        val pruned = two.withoutProfileSet("set1").withProfileSetSaved("c")
        assertEquals(listOf("set2", "set3"), pruned.profileSets.map { it.id })
    }

    @Test
    fun `applying a set puts every output back`() {
        val saved = proj("fs", "vert", ndi = listOf("ltd")).withProfileSetSaved("Sunday")
        val changed = saved.copy(
            screenAssignments = saved.screenAssignments.map { it.copy(activeProfileId = "ltd") },
            ndiOutputs = saved.ndiOutputs.map { it.copy(activeProfileId = "fs") },
        )
        assertEquals(listOf("fs", "vert", "ltd"), changed.withProfileSetApplied("set1").used())
    }

    @Test
    fun `an output or a profile that is gone is passed over, and an output the set does not name kept`() {
        val gone = mapOf("screen:0" to "ltd", "screen:1" to "deleted", "screen:5" to "fs")
        val set = ProfileSet(id = "s", profiles = gone)
        val start = proj("fs", "fs", "vert").copy(profileSets = listOf(set))
        assertEquals(listOf("ltd", "fs", "vert"), start.withProfileSetApplied("s").used())
    }

    @Test
    fun `a set that is not there changes nothing`() {
        val start = proj("fs", "vert")
        assertSame(start, start.withProfileSetApplied("nope"))
    }

    @Test
    fun `a set is applied while every output it names that is still here matches`() {
        val set = ProfileSet(id = "s", profiles = mapOf("screen:0" to "fs", "screen:1" to "vert", "screen:9" to "ltd"))
        assertTrue(proj("fs", "vert").isProfileSetApplied(set))
        assertFalse(proj("fs", "ltd").isProfileSetApplied(set))
        val nothingHere = ProfileSet(profiles = mapOf("screen:9" to "fs"))
        assertFalse(proj("fs", "vert").isProfileSetApplied(nothingHere), "nothing to match")
    }

    @Test
    fun `a set is renamed and deleted by its id alone`() {
        val two = proj("fs").withProfileSetSaved("a").withProfileSetSaved("b")
        assertEquals(listOf("a", "Youth"), two.withProfileSetRenamed("set2", "Youth").profileSets.map { it.name })
        assertEquals(listOf("b"), two.withoutProfileSet("set1").profileSets.map { it.name })
    }

    @Test
    fun `sets are offered with two outputs or more and two profiles or more`() {
        assertFalse(proj("fs").offersProfileSets(), "one output")
        assertTrue(proj("fs", ndi = listOf("ltd")).offersProfileSets(), "outputs of any kind count together")
        assertFalse(proj("fs", "vert").copy(outputProfiles = profiles.take(1)).offersProfileSets(), "one profile")
    }

    @Test
    fun `the button shows only while switched on and sets are offered`() {
        assertFalse(proj("fs", "vert").showsProfileSetsButton(), "off by default")
        assertTrue(proj("fs", "vert").copy(showProfileSetsButton = true).showsProfileSetsButton())
        assertFalse(proj("fs").copy(showProfileSetsButton = true).showsProfileSetsButton())
    }

    @Test
    fun `settings written before sets read as none, the button off`() {
        val decoded = settingsJson.decodeFromString(ProjectionSettings.serializer(), """{"screenAssignments":[]}""")
        assertTrue(decoded.profileSets.isEmpty())
        assertFalse(decoded.showProfileSetsButton)
    }
}
