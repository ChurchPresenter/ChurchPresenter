package org.churchpresenter.settings

import kotlinx.serialization.json.Json
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A linked profile following another master than its main one in some sections: a Sign language
 * screen following Livestream for its Bible and Sanctuary for everything else.
 *
 * Each master holds a different value in every section the tests read -- the Bible fade, the song
 * word wrap, the caption line count -- so which master a section follows is read straight off the
 * follower's own stored values, which is what its outputs draw with.
 */
class SectionMastersTest {

    private fun sanctuary(fadeIn: Boolean = true, wordWrap: Boolean = true, lines: Int = 2) = OutputProfile(
        id = "main",
        name = "Sanctuary",
        bibleSettings = BibleSettings(fadeIn = fadeIn),
        songSettings = SongSettings(wordWrap = wordWrap),
        sttSettings = STTSettings(maxLines = lines),
    )

    private fun livestream(fadeIn: Boolean = false, wordWrap: Boolean = false, lines: Int = 5) = OutputProfile(
        id = "stream",
        name = "Livestream",
        displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
        bibleSettings = BibleSettings(fadeIn = fadeIn),
        songSettings = SongSettings(wordWrap = wordWrap),
        sttSettings = STTSettings(maxLines = lines),
    )

    private fun sign(
        sectionMasters: Map<String, String> = mapOf("bible" to "stream"),
        overrides: Set<String> = emptySet(),
        lines: Int = 2,
    ) = OutputProfile(
        id = "sign",
        name = "Sign language",
        parentId = "main",
        sectionMasters = sectionMasters,
        overrides = overrides,
        sttSettings = STTSettings(maxLines = lines),
    )

    private fun proj(vararg profiles: OutputProfile) =
        ProjectionSettings(outputProfiles = profiles.toList()).withLinksResolved()

    private fun ProjectionSettings.p(id: String) = outputProfiles.first { it.id == id }

    private val linked = proj(sanctuary(), sign(), livestream())

    // ── Sections ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every path belongs to the section whose page edits it, and the display mode to none`() {
        assertEquals(ProfileSection.BIBLE, ProfileSection.of("bibleSettings.translations[kjv.spb].textFontSize"))
        assertEquals(ProfileSection.CONTENT, ProfileSection.of("showQA"))
        assertEquals(ProfileSection.BACKGROUND, ProfileSection.of("backgroundOverrides"))
        assertEquals(ProfileSection.STAGE, ProfileSection.of("stageMonitorSettings.zones"))
        assertNull(ProfileSection.of(DISPLAY_MODE_PATH))
        assertEquals(ProfileSection.QA, ProfileSection.byId("qa"))
        assertNull(ProfileSection.byId("nope"))
        // Every value a profile has lies in one section, but for the display mode.
        val unplaced = OutputProfile().settingPaths().keys.filter { ProfileSection.of(it) == null }
        assertEquals(listOf(DISPLAY_MODE_PATH), unplaced)
    }

    // ── Resolution ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a section takes its own master's values and every other section the main master's`() {
        val s = linked.p("sign")
        assertFalse(s.bibleSettings.fadeIn)
        assertTrue(s.songSettings.wordWrap)
        assertEquals(2, s.sttSettings.maxLines)
        // The display mode is always the main master's, never a section master's.
        assertEquals("fullscreen", s.displayMode)
        assertEquals(mapOf("bible" to "stream"), s.sectionMasters)
    }

    @Test
    fun `a section of its own keeps its values and nothing it changes there is an override`() {
        val p = proj(sanctuary(), sign(mapOf("captions" to OWN_SECTION), setOf("sttSettings.maxLines"), lines = 9))
        assertEquals(9, p.p("sign").sttSettings.maxLines)
        assertTrue(p.p("sign").overrides.isEmpty())
        val edited = p.editProfile("sign") { it.copy(sttSettings = it.sttSettings.copy(maxLines = 4)) }
        assertEquals(4, edited.p("sign").sttSettings.maxLines)
        assertTrue(edited.p("sign").overrides.isEmpty())
        // A change on the main master never reaches it.
        val masterEdit = edited.editProfile("main") { it.copy(sttSettings = it.sttSettings.copy(maxLines = 7)) }
        assertEquals(4, masterEdit.p("sign").sttSettings.maxLines)
    }

    @Test
    fun `a file written before sections could follow apart resolves exactly as it always did`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = """{"id":"sign","name":"Sign language","parentId":"main","overrides":["bibleSettings.fadeIn"],""" +
            """"bibleSettings":{"fadeIn":false}}"""
        val child = json.decodeFromString(OutputProfile.serializer(), old)
        assertTrue(child.sectionMasters.isEmpty())
        val master = sanctuary()
        val resolved = proj(master, child).p("sign")
        assertEquals(materialize(master, child), resolved)
        assertFalse(resolved.bibleSettings.fadeIn)
        assertTrue(resolved.songSettings.wordWrap)
    }

    @Test
    fun `a change on a section master reaches only the sections that follow it`() {
        val p = linked.editProfile("stream") {
            it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = true), songSettings = SongSettings(wordWrap = false))
        }
        assertTrue(p.p("sign").bibleSettings.fadeIn)
        assertTrue(p.p("sign").songSettings.wordWrap)
    }

    @Test
    fun `an edit in a followed section is an override that survives its master's changes`() {
        val edited = linked.editProfile("sign") { it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = true)) }
        assertEquals(setOf("bibleSettings.fadeIn"), edited.p("sign").overrides)
        val masterEdit = edited.editProfile("stream") { it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = false)) }
        assertTrue(masterEdit.p("sign").bibleSettings.fadeIn)
    }

    @Test
    fun `revert takes each value back from the master of its own section`() {
        val edited = linked.editProfile("sign") {
            it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = true), songSettings = SongSettings(wordWrap = false))
        }
        val reverted = edited.revertToMaster("sign", listOf("bibleSettings", "songSettings"))
        assertFalse(reverted.p("sign").bibleSettings.fadeIn)
        assertTrue(reverted.p("sign").songSettings.wordWrap)
        assertTrue(reverted.p("sign").overrides.isEmpty())
    }

    @Test
    fun `a section whose master is gone, linked or itself is its own, with its values kept`() {
        val gone = proj(sanctuary(), sign(mapOf("bible" to "nobody")))
        assertEquals(mapOf("bible" to OWN_SECTION), gone.p("sign").sectionMasters)
        val linkedMaster = livestream().copy(parentId = "main")
        val p = proj(sanctuary(), sign(), linkedMaster)
        assertEquals(OWN_SECTION, p.p("sign").sectionMasters["bible"])
        val self = proj(sanctuary(), sign(mapOf("bible" to "sign")))
        assertEquals(OWN_SECTION, self.p("sign").sectionMasters["bible"])
    }

    @Test
    fun `unknown sections and ones naming the main master are dropped, and a standalone profile has none`() {
        val p = proj(sanctuary(), sign(mapOf("bible" to "main", "later" to "stream")), livestream())
        assertTrue(p.p("sign").sectionMasters.isEmpty())
        val standalone = proj(sanctuary(), sign().copy(parentId = null), livestream())
        assertTrue(standalone.p("sign").sectionMasters.isEmpty())
    }

    // ── Who follows whom ────────────────────────────────────────────────────────────────────────

    @Test
    fun `a section master counts the profile as a follower, in those sections only`() {
        assertEquals(listOf("sign"), linked.linkedTo("stream").map { it.id })
        assertEquals(listOf("sign"), linked.linkedTo("main").map { it.id })
        val s = linked.p("sign")
        assertTrue(s.followsInSection("stream"))
        assertFalse(s.followsInSection("main"))
        assertEquals(listOf(ProfileSection.BIBLE), s.sectionsFollowing("stream"))
        assertEquals("stream", linked.masterFor(s, ProfileSection.BIBLE)?.id)
        assertEquals("main", linked.masterFor(s, ProfileSection.SONGS)?.id)
        assertNull(linked.masterFor(linked.p("main"), ProfileSection.BIBLE))
        val own = proj(sanctuary(), sign(mapOf("bible" to OWN_SECTION)), livestream())
        assertNull(own.masterFor(own.p("sign"), ProfileSection.BIBLE))
    }

    @Test
    fun `a follower's changes from a section master count only the sections following it`() {
        val edited = linked.editProfile("sign") {
            it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = true), songSettings = SongSettings(wordWrap = false))
        }.p("sign")
        assertEquals(1, edited.changesFrom("stream"))
        assertEquals(2, edited.changesFrom("main"))
    }

    @Test
    fun `one level only - a section master cannot follow and cannot be deleted while followed`() {
        assertSame(linked, linked.linkProfile("stream", "main", keepOwnValues = true))
        assertSame(linked, linked.deleteOutputProfile("stream"))
        val unlinked = linked.unlinkProfile("sign")
        assertEquals(3, unlinked.outputProfiles.size)
        assertTrue(unlinked.linkedTo("stream").isEmpty())
        assertEquals(listOf("main", "sign"), unlinked.deleteOutputProfile("stream").outputProfiles.map { it.id })
    }

    // ── Changing a section's master ─────────────────────────────────────────────────────────────

    @Test
    fun `another master for a section matches it, dropping the section's own values`() {
        val edited = proj(sanctuary(), sign(emptyMap()), livestream())
            .editProfile("sign") { it.copy(songSettings = SongSettings(wordWrap = false)) }
        val moved = edited.setSectionMaster("sign", ProfileSection.SONGS, "stream")
        assertEquals(mapOf("songs" to "stream"), moved.p("sign").sectionMasters)
        assertTrue(moved.p("sign").overrides.isEmpty())
        assertFalse(moved.p("sign").songSettings.wordWrap)
        // Back to the main master: the key goes, and the section takes the main master's again.
        val back = moved.setSectionMaster("sign", ProfileSection.SONGS, null)
        assertTrue(back.p("sign").sectionMasters.isEmpty())
        assertTrue(back.p("sign").songSettings.wordWrap)
        assertEquals(back, moved.setSectionMaster("sign", ProfileSection.SONGS, "main"))
    }

    @Test
    fun `Own keeps the values the section has`() {
        val own = linked.setSectionMaster("sign", ProfileSection.BIBLE, OWN_SECTION)
        assertEquals(OWN_SECTION, own.p("sign").sectionMasters["bible"])
        assertFalse(own.p("sign").bibleSettings.fadeIn)
        val masterEdit = own.editProfile("stream") { it.copy(bibleSettings = it.bibleSettings.copy(fadeIn = true)) }
        assertFalse(masterEdit.p("sign").bibleSettings.fadeIn)
        assertTrue(masterEdit.linkedTo("stream").isEmpty())
    }

    @Test
    fun `a section master is refused when it is linked, the profile itself, missing, or the profile follows nothing`() {
        val withChild = proj(sanctuary(), sign(emptyMap()), livestream().copy(parentId = "main"))
        assertSame(withChild, withChild.setSectionMaster("sign", ProfileSection.BIBLE, "stream"))
        assertSame(linked, linked.setSectionMaster("sign", ProfileSection.BIBLE, "sign"))
        assertSame(linked, linked.setSectionMaster("sign", ProfileSection.BIBLE, "nobody"))
        assertSame(linked, linked.setSectionMaster("stream", ProfileSection.BIBLE, "main"))
        assertSame(linked, linked.setSectionMaster("nobody", ProfileSection.BIBLE, "main"))
    }

    // ── Leaving the link ────────────────────────────────────────────────────────────────────────

    @Test
    fun `unlinking forgets every master and keeps every value`() {
        val freed = linked.unlinkProfile("sign").p("sign")
        assertNull(freed.parentId)
        assertTrue(freed.sectionMasters.isEmpty())
        assertFalse(freed.bibleSettings.fadeIn)
        assertTrue(freed.songSettings.wordWrap)
    }

    @Test
    fun `a duplicate follows nothing, in any section`() {
        val copy = linked.duplicateOutputProfile("sign", "Sign copy").outputProfiles.last()
        assertNull(copy.parentId)
        assertTrue(copy.sectionMasters.isEmpty())
        assertFalse(copy.bibleSettings.fadeIn)
    }

    @Test
    fun `section masters are identity, never a setting that differs`() {
        assertFalse(linked.p("sign").settingPaths().keys.any { it.startsWith("sectionMasters") })
        assertEquals(linked.p("sign").sectionMasters, linked.p("sign").defaultBaseline().sectionMasters)
    }
}
