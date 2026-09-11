package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Which slides carry the song's title and number, and how the lyric is aligned.
 *
 * "First Page" is the setting most churches run: the title and number appear on the opening slide
 * of a song and then get out of the way. There is no marker in a lyric saying which section that
 * is, so it is decided from the section's own heading — and headings are free text typed by
 * whoever entered the song, in any language, so the rule is a set of string tests rather than a
 * lookup. Getting it wrong is quiet in both directions: the title never appears (the congregation
 * cannot tell which song is starting) or it appears on every slide (it covers the lyric all the
 * way through).
 *
 * Both helpers are private to `SongPresenter`, so they are reached by reflection on the file class.
 */
class SongPresenterTitleRuleTest {

    private val songPresenterKt =
        Class.forName("org.churchpresenter.app.churchpresenter.presenter.SongPresenterKt")

    private fun shouldShowText(display: String, section: LyricSection): Boolean =
        songPresenterKt
            .getDeclaredMethod("shouldShowText", String::class.java, LyricSection::class.java)
            .apply { isAccessible = true }
            .invoke(null, display, section) as Boolean

    /**
     * The chosen [TextAlign], named. `TextAlign` is a value class over `Int`, so reflection hands
     * back the raw int — it is turned back into the name Compose itself prints for it.
     */
    private fun textAlign(alignment: String): String {
        val raw = songPresenterKt
            .getDeclaredMethod("getTextAlign", String::class.java)
            .apply { isAccessible = true }
            .invoke(null, alignment) as Int
        return TextAlign::class.java
            .getMethod("toString-impl", Int::class.javaPrimitiveType)
            .invoke(null, raw) as String
    }

    private fun section(header: String?, type: String = Constants.SECTION_TYPE_VERSE) =
        LyricSection(header = header, type = type, lines = listOf("a line"))

    // ── Showing on every slide ──────────────────────────────────────────────────

    @Test
    fun `every page shows the title throughout the song`() {
        listOf(null, "[Verse 1]", "[Verse 4]", "{Chorus}", "[Bridge]").forEach { header ->
            assertTrue(
                shouldShowText(Constants.EVERY_PAGE, section(header)),
                "'$header' must still carry the title when every page is asked for",
            )
        }
    }

    // ── The first page, from the song's own order ───────────────────────────────

    private val verse1 = section("[Verse 1]")
    private val chorus = section("[Chorus]") // square brackets: typed as a verse, no number in it
    private val verse21 = section("[Verse 2.1]")
    private val song = listOf(verse1, chorus, verse21)

    private fun firstPageOf(section: LyricSection, all: List<LyricSection> = song, index: Int = -1) =
        shouldShowText(Constants.FIRST_PAGE, section, all, index)

    @Test
    fun `the first section of the song is the first page whatever it is headed`() {
        assertTrue(firstPageOf(verse1))
        assertTrue(firstPageOf(chorus, all = listOf(chorus, verse1)), "a song that opens on its chorus")
    }

    @Test
    fun `later sections are not, even when their heading reads like an opening one`() {
        // Both pass the heading rule -- no number, and a trailing 1 -- and both are mid-song.
        assertFalse(firstPageOf(chorus))
        assertFalse(firstPageOf(verse21))
    }

    @Test
    fun `the section is found by what it is, not by identity`() {
        // What goes out is stamped with the song's tempo and capo; the list is not.
        assertTrue(firstPageOf(verse1.copy(bpm = 96, capo = 2)))
        assertFalse(firstPageOf(chorus.copy(bpm = 96, capo = 2)))
    }

    @Test
    fun `the display index is trusted when it points at the section`() {
        // Two identical choruses: the index says which of them is on screen.
        val repeat = listOf(chorus, verse1, chorus)
        assertTrue(firstPageOf(chorus, all = repeat, index = 0))
        assertFalse(firstPageOf(chorus, all = repeat, index = 2))
    }

    @Test
    fun `a title slide in front does not take the first page from verse one`() {
        val titleSlide = LyricSection(type = Constants.SECTION_TYPE_TITLE_SLIDE, lines = listOf("Amazing Grace"))
        assertTrue(firstPageOf(verse1, all = listOf(titleSlide, verse1, chorus), index = 1))
    }

    @Test
    fun `a section not in the list falls back to the heading rule`() {
        val wholeSong = LyricSection(type = Constants.SECTION_TYPE_SONG, lines = listOf("all of it"))
        assertTrue(firstPageOf(wholeSong), "the whole-song slide is unheaded, so it is the first")
        assertFalse(firstPageOf(section("[Verse 3]"), all = emptyList()))
    }

    @Test
    fun `every page and none are unchanged by the order`() {
        assertTrue(shouldShowText(Constants.EVERY_PAGE, verse21, song, 2))
        assertFalse(shouldShowText(Constants.NONE, verse1, song, 0))
    }

    // ── Showing on the first slide only ─────────────────────────────────────────

    @Test
    fun `first page shows the title on verse one`() {
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section("[Verse 1]")))
    }

    @Test
    fun `first page means the first slide, not every slide of the first verse`() {
        // A verse broken by a manual [---] arrives as several sections all headed [Verse 1]; the
        // title belongs on the opening slide only, exactly as it does on verse 2 onwards.
        val opening = section("[Verse 1]").copy(slideIndex = 0, slideCount = 2)
        val continued = section("[Verse 1]").copy(slideIndex = 1, slideCount = 2)

        assertTrue(shouldShowText(Constants.FIRST_PAGE, opening))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, continued))
    }

    @Test
    fun `an unheaded song shows the title on its first slide only`() {
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section(null)))
        assertFalse(
            shouldShowText(Constants.FIRST_PAGE, section(null).copy(slideIndex = 1, slideCount = 2)),
        )
    }

    @Test
    fun `first page does not show it on later verses`() {
        listOf("[Verse 2]", "[Verse 3]", "[Verse 10]").forEach { header ->
            assertFalse(
                shouldShowText(Constants.FIRST_PAGE, section(header)),
                "'$header' would put the title back over the lyric mid-song",
            )
        }
    }

    @Test
    fun `a section with no heading at all is treated as the first`() {
        assertTrue(
            shouldShowText(Constants.FIRST_PAGE, section(null)),
            "a song entered as one unlabelled block would otherwise never show its title",
        )
    }

    @Test
    fun `a heading with no number in it is treated as the first`() {
        // "Verse", "Куплет", "Intro" — a song whose sections were never numbered still has to
        // announce itself.
        listOf("[Verse]", "[Куплет]", "Intro").forEach { header ->
            assertTrue(
                shouldShowText(Constants.FIRST_PAGE, section(header)),
                "'$header' names no position, so it cannot be ruled out as the opening slide",
            )
        }
    }

    @Test
    fun `a chorus never counts as the first page`() {
        // A chorus can be sung first, but the title belongs on the verse that opens the song.
        listOf("{Chorus}", "{Припев}", "{Chorus 1}").forEach { header ->
            assertFalse(
                shouldShowText(Constants.FIRST_PAGE, section(header, type = Constants.SECTION_TYPE_CHORUS)),
                "'$header' is a chorus and must not claim the title slide",
            )
        }
    }

    @Test
    fun `the brackets a heading is wrapped in do not change the decision`() {
        // The app wraps verses in [] and choruses in {}; hand-entered songs may use either or none.
        listOf("[Verse 1]", "{Verse 1}", "Verse 1", "  [Verse 1]  ").forEach { header ->
            assertTrue(shouldShowText(Constants.FIRST_PAGE, section(header)), "'$header' is still verse one")
        }
    }

    @Test
    fun `a heading in another language is judged by its number`() {
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section("[Куплет 1]")))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Куплет 2]")))
    }

    /**
     * Only verse *one* is the first page — not every verse whose number happens to end in a 1.
     *
     * The rule used to be `endsWith("1")`, so verses 11, 21 and 31 all read as the opening slide and
     * the title reappeared over them part-way through the song. Any hymn or psalm setting with
     * eleven or more numbered sections reaches it, and the failure is quiet: the title simply covers
     * the lyric again with nothing to say why.
     */
    @Test
    fun `a verse whose number merely ends in one is not the first page`() {
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Verse 11]")))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Verse 21]")))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Verse 31]")))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Куплет 11]")), "and in any language")
    }

    @Test
    fun `a zero-padded first verse is still the first page`() {
        // Numbering typed as 01/02 is read as a number, not as a string ending in "1".
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section("[Verse 01]")))
        assertFalse(shouldShowText(Constants.FIRST_PAGE, section("[Verse 011]")))
    }

    // ── Turning it off ──────────────────────────────────────────────────────────

    @Test
    fun `any other setting shows the title nowhere`() {
        listOf("Never", "", "some future option").forEach { display ->
            assertFalse(
                shouldShowText(display, section(null)),
                "'$display' is not a request to show it, and an unknown value must not turn it on",
            )
        }
    }

    // ── Alignment ───────────────────────────────────────────────────────────────

    @Test
    fun `the three alignments map to their text directions`() {
        assertEquals(
            TextAlign.Start.toString(),
            textAlign(Constants.LEFT),
            "Start rather than Left, so RTL text still reads",
        )
        assertEquals(TextAlign.End.toString(), textAlign(Constants.RIGHT))
        assertEquals(TextAlign.Center.toString(), textAlign(Constants.CENTER))
    }

    @Test
    fun `an unrecognised alignment centres rather than collapsing to one edge`() {
        listOf("", "Justify", "top").forEach { assertEquals(TextAlign.Center.toString(), textAlign(it)) }
    }
}
