package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.app.churchpresenter.data.SongItem
import org.churchpresenter.app.churchpresenter.data.settings.AppSettings
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SongsViewModel.getLyricSections(song)` turns a stored song into the ordered sections the
 * operator clicks through and the audience sees. It is the pure overload extracted for the
 * "edit a song while it is live" fix, so it takes an explicit song rather than reading selection
 * state — which is exactly what makes it testable here.
 *
 * The behaviour that matters: header parsing, automatic chorus repetition after every verse,
 * bilingual pairing by index, and the end-of-song marker.
 */
class LyricSectionsTest {

    private val vm = SongsViewModel(AppSettings())

    private fun song(
        lyrics: List<String>,
        secondary: List<String> = emptyList(),
        title: String = "Test Song",
        secondaryTitle: String = "",
        number: String = "12",
    ) = SongItem(
        number = number,
        title = title,
        lyrics = lyrics,
        secondaryLyrics = secondary,
        secondaryTitle = secondaryTitle,
    )

    // ── Basic splitting ─────────────────────────────────────────────────────────

    @Test
    fun `a song with no lyrics produces no sections`() {
        assertTrue(vm.getLyricSections(song(emptyList())).isEmpty())
    }

    @Test
    fun `lyrics with no headers become a single section`() {
        val sections = vm.getLyricSections(song(listOf("Line one", "Line two")))
        assertEquals(1, sections.size)
        assertEquals(listOf("Line one", "Line two"), sections.single().lines)
        assertEquals(Constants.SECTION_TYPE_VERSE, sections.single().type, "untyped content counts as a verse")
    }

    @Test
    fun `each header starts a new section`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "First verse", "[Verse 2]", "Second verse")),
        )
        assertEquals(2, sections.size)
        assertEquals("[Verse 1]", sections[0].header)
        assertEquals(listOf("First verse"), sections[0].lines)
        assertEquals("[Verse 2]", sections[1].header)
        assertEquals(listOf("Second verse"), sections[1].lines)
    }

    @Test
    fun `blank lines are dropped`() {
        val sections = vm.getLyricSections(song(listOf("Line one", "", "   ", "Line two")))
        assertEquals(listOf("Line one", "Line two"), sections.single().lines)
    }

    @Test
    fun `title and song number are carried onto every section`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "a", "[Verse 2]", "b"), title = "Amazing Grace", number = "42"),
        )
        assertTrue(sections.all { it.title == "Amazing Grace" })
        assertTrue(sections.all { it.songNumber == 42 })
    }

    @Test
    fun `a non-numeric song number becomes zero rather than failing`() {
        // Some songbooks use letters or empty numbers.
        val sections = vm.getLyricSections(song(listOf("a line"), number = "A-7"))
        assertEquals(0, sections.single().songNumber)
    }

    // ── Chorus auto-repeat ──────────────────────────────────────────────────────

    @Test
    fun `the chorus is repeated after every verse`() {
        val sections = vm.getLyricSections(
            song(
                listOf(
                    "[Verse 1]", "First verse",
                    "{Chorus}", "The chorus",
                    "[Verse 2]", "Second verse",
                ),
            ),
        )
        // Verse 1, Chorus, Verse 2, Chorus — the chorus is never left where it was written.
        assertEquals(4, sections.size)
        assertEquals(listOf("First verse"), sections[0].lines)
        assertEquals(listOf("The chorus"), sections[1].lines)
        assertEquals(listOf("Second verse"), sections[2].lines)
        assertEquals(listOf("The chorus"), sections[3].lines)
        assertEquals(Constants.SECTION_TYPE_CHORUS, sections[1].type)
        assertEquals(Constants.SECTION_TYPE_CHORUS, sections[3].type)
    }

    @Test
    fun `a song with no chorus keeps its verses exactly as written`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "a", "[Verse 2]", "b", "[Verse 3]", "c")),
        )
        assertEquals(3, sections.size)
        assertTrue(sections.none { it.type == Constants.SECTION_TYPE_CHORUS })
    }

    @Test
    fun `only the first chorus is used when several are written`() {
        val sections = vm.getLyricSections(
            song(
                listOf(
                    "[Verse 1]", "v1",
                    "{Chorus}", "chorus one",
                    "{Chorus 2}", "chorus two",
                    "[Verse 2]", "v2",
                ),
            ),
        )
        val choruses = sections.filter { it.type == Constants.SECTION_TYPE_CHORUS }
        assertTrue(choruses.all { it.lines == listOf("chorus one") }, "the first chorus wins everywhere")
    }

    /**
     * Documents a CURRENT BUG. The repeat pass drops every original chorus section and re-inserts
     * it only after a verse — so a song written as nothing but a chorus (common for short worship
     * refrains and choruses-only songbooks) yields ZERO sections and puts nothing on screen at all.
     *
     * Not fixed here — this slate is tests only. A fix would keep the chorus when there is no
     * verse to attach it to.
     */
    @Test
    fun `a chorus-only song produces no sections at all -- known bug`() {
        val sections = vm.getLyricSections(song(listOf("{Chorus}", "Only a refrain")))
        assertTrue(
            sections.isEmpty(),
            "current behaviour: a chorus-only song renders nothing (got $sections)",
        )
    }

    // ── End-of-song marker ──────────────────────────────────────────────────────

    @Test
    fun `only the final section is marked as last`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "a", "{Chorus}", "c", "[Verse 2]", "b")),
        )
        assertTrue(sections.last().isLastSection)
        assertTrue(sections.dropLast(1).none { it.isLastSection }, "only one section may carry the marker")
    }

    @Test
    fun `a single-section song has that section marked as last`() {
        assertTrue(vm.getLyricSections(song(listOf("only line"))).single().isLastSection)
    }

    @Test
    fun `the repeated chorus, not the original position, carries the marker`() {
        // The chorus is moved to the end, so the last thing shown is a chorus.
        val sections = vm.getLyricSections(song(listOf("[Verse 1]", "v", "{Chorus}", "c")))
        assertEquals(Constants.SECTION_TYPE_CHORUS, sections.last().type)
        assertTrue(sections.last().isLastSection)
    }

    // ── Bilingual pairing ───────────────────────────────────────────────────────

    @Test
    fun `secondary lyrics are paired with primary sections by index`() {
        val sections = vm.getLyricSections(
            song(
                lyrics = listOf("[Verse 1]", "English one", "[Verse 2]", "English two"),
                secondary = listOf("[Verse 1]", "Russian one", "[Verse 2]", "Russian two"),
                secondaryTitle = "Русский",
            ),
        )
        assertEquals(2, sections.size)
        assertEquals(listOf("Russian one"), sections[0].secondaryLines)
        assertEquals(listOf("Russian two"), sections[1].secondaryLines)
        assertTrue(sections.all { it.secondaryTitle == "Русский" })
    }

    @Test
    fun `a song with no secondary lyrics has empty secondary lines`() {
        val sections = vm.getLyricSections(song(listOf("[Verse 1]", "a")))
        assertTrue(sections.single().secondaryLines.isEmpty())
    }

    @Test
    fun `a shorter secondary translation leaves the extra sections unpaired`() {
        // Half-translated songs are common; the untranslated sections must still show.
        val sections = vm.getLyricSections(
            song(
                lyrics = listOf("[Verse 1]", "one", "[Verse 2]", "two", "[Verse 3]", "three"),
                secondary = listOf("[Verse 1]", "uno"),
            ),
        )
        assertEquals(3, sections.size)
        assertEquals(listOf("uno"), sections[0].secondaryLines)
        assertTrue(sections[1].secondaryLines.isEmpty(), "untranslated sections must not drop out")
        assertTrue(sections[2].secondaryLines.isEmpty())
    }

    @Test
    fun `the primary section count drives the result, not the secondary`() {
        val sections = vm.getLyricSections(
            song(
                lyrics = listOf("[Verse 1]", "one"),
                secondary = listOf("[Verse 1]", "uno", "[Verse 2]", "dos", "[Verse 3]", "tres"),
            ),
        )
        assertEquals(1, sections.size, "extra secondary sections must not invent primary ones")
        assertFalse(sections.single().secondaryLines.isEmpty())
    }
}
