package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
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
 * The behaviour that matters: header parsing, the chorus repetition after every verse and the
 * setting that turns it off, bilingual pairing by index, and the end-of-song marker.
 */
class LyricSectionsTest {

    private val vm = SongsViewModel(AppSettings())

    /** The same view model with the chorus auto-repeat switched off. */
    private val asWritten = SongsViewModel(
        AppSettings().let { it.copy(songSettings = it.songSettings.copy(autoRepeatChorus = false)) },
    )

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
        // Verse 1, Chorus, Verse 2, Chorus — the written chorus stays where it is and is repeated
        // after the verse that has none behind it.
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

    /**
     * A second, different chorus used to be **lost outright**: the pass dropped every authored
     * chorus and re-inserted `firstOrNull { chorus }` after each verse, so this song presented
     * "chorus one" twice and "chorus two" never (#403). Both are words someone wrote and expects to
     * sing.
     */
    @Test
    fun `a second chorus is never collapsed into the first`() {
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
        val choruses = sections.filter { it.type == Constants.SECTION_TYPE_CHORUS }.map { it.lines }
        assertTrue(listOf("chorus one") in choruses, "the first chorus must still be presented")
        assertTrue(listOf("chorus two") in choruses, "the second chorus must not be dropped")
        // Both are written before verse 2, so the repeat after it is the nearer one.
        assertEquals(
            listOf(listOf("v1"), listOf("chorus one"), listOf("chorus two"), listOf("v2"), listOf("chorus two")),
            sections.map { it.lines },
        )
    }

    @Test
    fun `the reporter's song keeps its authored order and its bridge`() {
        // Issue #403: [Verse 1] [Verse 2] {Chorus} [Bridge] presented as
        // Verse 1 · Chorus · Verse 2 · Chorus · Bridge · Chorus — a chorus behind the bridge, which
        // is a verse only because it is bracketed.
        val sections = vm.getLyricSections(
            song(
                listOf(
                    "[Verse 1]", "v1",
                    "[Verse 2]", "v2",
                    "{Chorus}", "c",
                    "[Bridge]", "b",
                ),
            ),
        )
        assertEquals(
            listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}", "[Bridge]"),
            sections.map { it.header },
        )
    }

    @Test
    fun `a bridge does not collect a chorus of its own`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "v1", "{Chorus}", "c", "[Bridge]", "b")),
        )
        assertEquals(listOf("[Verse 1]", "{Chorus}", "[Bridge]"), sections.map { it.header })
    }

    @Test
    fun `an intro, a tag and a pre-chorus are not verses either`() {
        val sections = vm.getLyricSections(
            song(
                listOf(
                    "[Intro]", "i",
                    "[Pre-Chorus]", "p",
                    "{Chorus}", "c",
                    "[Tag]", "t",
                ),
            ),
        )
        // Nothing here is a verse, so the chorus stays exactly where it was written.
        assertEquals(listOf("[Intro]", "[Pre-Chorus]", "{Chorus}", "[Tag]"), sections.map { it.header })
    }

    // ── Auto-repeat switched off ────────────────────────────────────────────────

    @Test
    fun `with the setting off the sections are presented as written`() {
        val lyrics = listOf("[Verse 1]", "v1", "[Verse 2]", "v2", "{Chorus}", "c", "[Bridge]", "b")
        val sections = asWritten.getLyricSections(song(lyrics))

        assertEquals(listOf("[Verse 1]", "[Verse 2]", "{Chorus}", "[Bridge]"), sections.map { it.header })
    }

    @Test
    fun `with the setting off a chorus before verse one keeps its place`() {
        val sections = asWritten.getLyricSections(
            song(listOf("{Chorus}", "c", "[Verse 1]", "v1", "[Verse 2]", "v2")),
        )
        assertEquals(listOf("{Chorus}", "[Verse 1]", "[Verse 2]"), sections.map { it.header })
        assertTrue(sections.last().isLastSection, "the marker still lands on the final section")
    }

    @Test
    fun `with the setting off both choruses survive in order`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "v1", "{Chorus 1}", "one", "[Verse 2]", "v2", "{Chorus 2}", "two")),
        )
        assertEquals(
            listOf(listOf("v1"), listOf("one"), listOf("v2"), listOf("two")),
            sections.map { it.lines },
        )
    }

    @Test
    fun `a chorus written after every verse is not repeated twice over`() {
        // Someone who writes each repeat out in full must not get it doubled with the setting on.
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "v1", "{Chorus}", "c", "[Verse 2]", "v2", "{Chorus}", "c")),
        )
        assertEquals(4, sections.size, "got ${sections.map { it.header }}")
    }

    @Test
    fun `a chorus written after all the verses is still repeated after each of them`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "v1", "[Verse 2]", "v2", "{Chorus}", "c")),
        )
        assertEquals(listOf("[Verse 1]", "{Chorus}", "[Verse 2]", "{Chorus}"), sections.map { it.header })
    }

    /**
     * A song that is nothing but a chorus still has to go on screen.
     *
     * The auto-repeat pass drops every original chorus and re-inserts it after each verse. With no
     * verse to attach it to, that used to leave **zero sections** — the operator selected the song
     * and the congregation saw nothing at all. Short worship refrains and choruses-only songbooks
     * are exactly the case, so this was not an edge.
     *
     * The auto-repeat has nothing to do without a verse, so the sections are returned as written.
     */
    @Test
    fun `a chorus-only song still renders its chorus`() {
        val sections = vm.getLyricSections(song(listOf("{Chorus}", "Only a refrain")))

        assertEquals(1, sections.size, "got $sections")
        assertEquals(listOf("Only a refrain"), sections.single().lines)
        assertEquals(Constants.SECTION_TYPE_CHORUS, sections.single().type)
        assertTrue(sections.single().isLastSection, "the only section is also the last")
    }

    @Test
    fun `several choruses and no verse keep their written order`() {
        val sections = vm.getLyricSections(
            song(listOf("{Chorus 1}", "first refrain", "{Chorus 2}", "second refrain")),
        )

        // Nothing to interleave them with, so neither is dropped nor repeated.
        assertEquals(listOf(listOf("first refrain"), listOf("second refrain")), sections.map { it.lines })
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
        // The chorus is written last and nothing is inserted after it, so it carries the marker.
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
