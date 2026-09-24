package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The profile list itself: making one, editing one, copying one, and refusing to delete one.
 *
 * An output follows exactly one profile by id and has no fallback left to resolve to, so every
 * function here is holding one invariant -- that no output can end up pointing at a profile that
 * is not in the list. A reused id or a deleted-while-in-use profile both break it, and the symptom
 * is a black screen mid-service rather than an error anyone sees while editing.
 */
class OutputProfilesTest {

    private fun profiles(vararg ids: String) =
        ProjectionSettings(outputProfiles = ids.map { OutputProfile(id = it) })

    private fun assignedTo(id: String, named: String = "Main") =
        ScreenAssignment(screenName = named, activeProfileId = id)

    // ── Making one ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a fresh profile is numbered past the list`() {
        val made = newOutputProfile(listOf(OutputProfile(id = "profile1"), OutputProfile(id = "profile2")))

        assertEquals("profile3", made.id)
        assertEquals("", made.name, "unnamed unless asked for a name")
    }

    @Test
    fun `an id already taken is stepped over rather than reused`() {
        // The count is not the high-water mark: deleting profile2 of three leaves profile3 behind
        // with a list of size 2, so the obvious "size + 1" collides on the very next New Profile.
        val existing = listOf(OutputProfile(id = "profile1"), OutputProfile(id = "profile3"))

        val made = newOutputProfile(existing, name = "Overflow")

        assertTrue(made.id !in existing.map { it.id })
        assertEquals("Overflow", made.name)
    }

    @Test
    fun `adding one puts it at the end`() {
        val added = profiles("profile1").addOutputProfile(OutputProfile(id = "profile9", name = "Stage"))

        assertEquals(listOf("profile1", "profile9"), added.outputProfiles.map { it.id })
    }

    // ── Editing one ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `updating one leaves every other profile alone`() {
        val updated = profiles("profile1", "profile2")
            .updateOutputProfile("profile2") { it.copy(bibleMode = Constants.SONG_LANG_OFF) }

        assertTrue(updated.outputProfiles[0].showBible, "the profile not named is untouched")
        assertEquals(false, updated.outputProfiles[1].showBible)
    }

    @Test
    fun `updating an id that names nothing changes nothing`() {
        val before = profiles("profile1")

        assertEquals(before, before.updateOutputProfile("profile7") { it.copy(name = "ghost") })
    }

    @Test
    fun `renaming one changes only its name`() {
        val renamed = profiles("profile1", "profile2").renameOutputProfile("profile1", "Auditorium")

        assertEquals("Auditorium", renamed.outputProfiles[0].name)
        assertEquals("", renamed.outputProfiles[1].name)
    }

    // ── Copying one ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a duplicate carries the settings and neither the id nor the name`() {
        val source = OutputProfile(id = "profile1", name = "Main", songMode = Constants.SONG_LANG_OFF)
        val settings = ProjectionSettings(outputProfiles = listOf(source))

        val copy = settings.duplicateOutputProfile("profile1", "Main (overflow)").outputProfiles.last()

        assertNotEquals(source.id, copy.id, "sharing an id would make every output follow both")
        assertEquals("Main (overflow)", copy.name)
        assertEquals(false, copy.showSongs, "the look is what there was to copy")
    }

    @Test
    fun `duplicating an id that names nothing changes nothing`() {
        val before = profiles("profile1")

        assertEquals(before, before.duplicateOutputProfile("profile7", "ghost"))
    }

    // ── Deleting one ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a profile nothing follows is deleted`() {
        val after = profiles("profile1", "profile2").deleteOutputProfile("profile2")

        assertEquals(listOf("profile1"), after.outputProfiles.map { it.id })
    }

    @Test
    fun `a profile an output still follows is kept`() {
        // The backstop, not the interface: the dialog is expected to name the outputs first. Left
        // to go through, the delete would point that screen at a profile that no longer exists.
        val settings = profiles("profile1", "profile2").copy(screenAssignments = listOf(assignedTo("profile2")))

        assertEquals(settings, settings.deleteOutputProfile("profile2"))
    }

    // ── Who is following it ────────────────────────────────────────────────────────────────────

    @Test
    fun `usage is counted across all three kinds of output`() {
        val settings = profiles("profile1").copy(
            screenAssignments = listOf(assignedTo("profile1"), assignedTo("profile1", named = "Overflow")),
            browserSourceOutputs = listOf(assignedTo("profile1", named = "Stream")),
            ndiOutputs = listOf(assignedTo("profile2", named = "Foyer")),
        )

        assertEquals(3, settings.outputProfileUsageCount("profile1"))
        assertEquals(1, settings.outputProfileUsageCount("profile2"))
        assertEquals(0, settings.outputProfileUsageCount("profile9"))
    }

    @Test
    fun `the followers are listed under whatever label the caller gives them`() {
        val settings = profiles("profile1").copy(
            screenAssignments = listOf(assignedTo("profile1", named = "Overflow")),
            ndiOutputs = listOf(assignedTo("profile1", named = "Foyer")),
        )

        val users = settings.outputProfileUsers("profile1") { it.screenName }

        assertEquals(listOf("Overflow", "Foyer"), users, "screens first, then browser sources, then NDI")
    }
}
