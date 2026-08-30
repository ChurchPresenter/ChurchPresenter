package org.churchpresenter.app.churchpresenter.viewmodel

import org.churchpresenter.core.models.songs.SongBackgroundType
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

    // ── Manual slide breaks ─────────────────────────────────────────────────────

    @Test
    fun `a break splits a section into slides that keep its name`() {
        // Issue #404: the only way to break a long chorus used to be to start a new section, which
        // put the wrong name on the second half.
        val sections = asWritten.getLyricSections(
            song(listOf("{Chorus}", "first half", "[---]", "second half")),
        )

        assertEquals(2, sections.size)
        assertEquals(listOf(listOf("first half"), listOf("second half")), sections.map { it.lines })
        assertTrue(sections.all { it.header == "{Chorus}" }, "both slides are still the chorus")
        assertTrue(sections.all { it.type == Constants.SECTION_TYPE_CHORUS })
        assertEquals(listOf(0, 1), sections.map { it.slideIndex })
        assertTrue(sections.all { it.slideCount == 2 })
    }

    @Test
    fun `a break is never a section of its own`() {
        // It parsed as a header named "---", so the reporter's editor showed a `---` badge and
        // counted the break as a section.
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "a", "[---]", "b")),
        )
        assertTrue(sections.none { it.header?.contains("-") == true })
        assertTrue(sections.none { it.lines.any { line -> line.contains("---") } })
    }

    @Test
    fun `a section can be split three ways`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "one", "[---]", "two", "[---]", "three")),
        )
        assertEquals(listOf(listOf("one"), listOf("two"), listOf("three")), sections.map { it.lines })
        assertEquals(listOf(0, 1, 2), sections.map { it.slideIndex })
        assertTrue(sections.all { it.slideCount == 3 })
    }

    @Test
    fun `a break with nothing behind it costs no blank slide`() {
        // Leading, doubled and trailing markers are what someone editing by hand actually leaves.
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "[---]", "one", "[---]", "[---]", "two", "[---]")),
        )
        assertEquals(listOf(listOf("one"), listOf("two")), sections.map { it.lines })
        assertTrue(sections.all { it.slideCount == 2 })
    }

    @Test
    fun `a break outside any section splits the untitled body`() {
        val sections = asWritten.getLyricSections(song(listOf("one", "[---]", "two")))
        assertEquals(listOf(listOf("one"), listOf("two")), sections.map { it.lines })
        assertTrue(sections.all { it.header == null })
    }

    @Test
    fun `a song of nothing but breaks produces nothing`() {
        assertTrue(asWritten.getLyricSections(song(listOf("[---]", "[---]"))).isEmpty())
    }

    @Test
    fun `an unsplit section is slide one of one`() {
        val sections = asWritten.getLyricSections(song(listOf("[Verse 1]", "a", "[Verse 2]", "b")))
        assertTrue(sections.all { it.slideIndex == 0 && it.slideCount == 1 })
    }

    @Test
    fun `two sections split the same way are numbered apart`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "a", "[---]", "b", "[Verse 2]", "c", "[---]", "d")),
        )
        assertEquals(listOf(0, 1, 0, 1), sections.map { it.slideIndex })
        assertEquals(listOf("[Verse 1]", "[Verse 1]", "[Verse 2]", "[Verse 2]"), sections.map { it.header })
    }

    @Test
    fun `only the last slide of the last section carries the end-of-song marker`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "a", "[---]", "b")),
        )
        assertTrue(sections.last().isLastSection)
        assertTrue(sections.dropLast(1).none { it.isLastSection })
    }

    @Test
    fun `a split chorus is repeated whole`() {
        // The auto-repeat works in sections, so a two-slide chorus is sung as two slides each time.
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "v1", "{Chorus}", "c1", "[---]", "c2", "[Verse 2]", "v2")),
        )
        assertEquals(
            listOf(listOf("v1"), listOf("c1"), listOf("c2"), listOf("v2"), listOf("c1"), listOf("c2")),
            sections.map { it.lines },
        )
        assertEquals(listOf(0, 0, 1, 0, 0, 1), sections.map { it.slideIndex })
    }

    @Test
    fun `a split verse collects one chorus, not one per slide`() {
        val sections = vm.getLyricSections(
            song(listOf("[Verse 1]", "v1a", "[---]", "v1b", "{Chorus}", "c")),
        )
        assertEquals(listOf(listOf("v1a"), listOf("v1b"), listOf("c")), sections.map { it.lines })
    }

    @Test
    fun `a break inside a chord-only section does not resurrect it as a blank slide`() {
        // The intro has no words, so it is folded onto the verse and the break inside it goes with
        // it — an empty slide would be a black screen the operator has to click past.
        val sections = asWritten.getLyricSections(
            song(listOf("[Intro]", "[Cm] [Bb]", "[---]", "[Ab] [G]", "[Verse 1]", "one")),
        )
        assertEquals(1, sections.size, "got ${sections.map { it.header }}")
        assertEquals("[Verse 1]", sections.single().header)
        assertEquals(listOf("one"), sections.single().lines)
    }

    @Test
    fun `a split section divides its chart along with its words`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "[G]one", "[---]", "[C]two")),
        )
        assertEquals(listOf(listOf("[G]one"), listOf("[C]two")), sections.map { it.chordLines })
        assertEquals(listOf(listOf("one"), listOf("two")), sections.map { it.lines })
    }

    // ── Per-section backgrounds ─────────────────────────────────────────────────

    @Test
    fun `a section's own background reaches the section, and only that one`() {
        val sections = asWritten.getLyricSections(
            song(
                listOf(
                    "[Verse 1]", "v1",
                    "{Chorus}",
                    "[background: color]",
                    "[background-color: #2a1130]",
                    "[background-dim: 65]",
                    "c",
                ),
            ),
        )

        assertEquals(2, sections.size)
        assertFalse(sections[0].background.isCustom, "the verse said nothing, so it inherits")
        assertEquals(SongBackgroundType.COLOR, sections[1].background.type)
        assertEquals("#2a1130", sections[1].background.color)
        assertEquals(65, sections[1].background.dim)
    }

    @Test
    fun `a directive is never a lyric, a chart line or a section`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "[background: color]", "[background-color: #101010]", "a line")),
        )

        assertEquals(1, sections.size, "got ${sections.map { it.header }}")
        assertEquals(listOf("a line"), sections.single().lines)
        assertTrue(sections.single().chordLines.isEmpty())
    }

    @Test
    fun `a section background covers every slide of that section`() {
        val sections = asWritten.getLyricSections(
            song(listOf("{Chorus}", "[background: color]", "[background-color: #101010]", "c1", "[---]", "c2")),
        )

        assertEquals(2, sections.size)
        assertTrue(sections.all { it.background.color == "#101010" })
    }

    @Test
    fun `a directive written after a break styles the slides from there on`() {
        // Written mid-section it applies from where it is, which is the only reading that lets one
        // slide of a section differ from the one before it.
        val sections = asWritten.getLyricSections(
            song(listOf("{Chorus}", "c1", "[---]", "[background: color]", "[background-color: #101010]", "c2")),
        )

        assertFalse(sections[0].background.isCustom)
        assertEquals("#101010", sections[1].background.color)
    }

    @Test
    fun `a background does not leak into the section after it`() {
        val sections = asWritten.getLyricSections(
            song(listOf("[Verse 1]", "[background: color]", "[background-color: #101010]", "a", "[Verse 2]", "b")),
        )

        assertEquals("#101010", sections[0].background.color)
        assertFalse(sections[1].background.isCustom, "each section states its own or inherits")
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
    fun `matching breaks in both languages pair slide for slide`() {
        val sections = asWritten.getLyricSections(
            song(
                lyrics = listOf("{Chorus}", "English one", "[---]", "English two"),
                secondary = listOf("{Chorus}", "Russian one", "[---]", "Russian two"),
            ),
        )
        assertEquals(listOf(listOf("Russian one"), listOf("Russian two")), sections.map { it.secondaryLines })
    }

    /**
     * A break in one language and not the other cannot be paired, and the question is only how far
     * the damage spreads. Pairing runs per section rather than on one running index, so the extra
     * slide comes out untranslated and **verse 2 still meets verse 2** — where a flat index would
     * have slid every later section under the wrong translation.
     */
    @Test
    fun `a break in one language only leaves that section short, not the rest of the song`() {
        val sections = asWritten.getLyricSections(
            song(
                lyrics = listOf("[Verse 1]", "one", "[---]", "one and a half", "[Verse 2]", "two"),
                secondary = listOf("[Verse 1]", "uno", "[Verse 2]", "dos"),
            ),
        )
        assertEquals(3, sections.size)
        assertEquals(listOf("uno"), sections[0].secondaryLines)
        assertTrue(sections[1].secondaryLines.isEmpty(), "the untranslated extra slide")
        assertEquals(listOf("dos"), sections[2].secondaryLines, "verse 2 still meets its translation")
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
