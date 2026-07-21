package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.utils.Constants
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

    // ── Showing on the first slide only ─────────────────────────────────────────

    @Test
    fun `first page shows the title on verse one`() {
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section("[Verse 1]")))
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
     * Documents a KNOWN GAP: the rule is "ends with 1", so verse 11, 21 and 31 all read as the
     * first page and the title reappears over them.
     *
     * Reachable in any song with eleven or more numbered sections — long hymns and psalm settings
     * do reach that. The fix is to match the trailing number as a whole (`\b1$`); this expectation
     * then becomes false.
     */
    @Test
    fun `verse eleven is mistaken for verse one -- known gap`() {
        assertTrue(
            shouldShowText(Constants.FIRST_PAGE, section("[Verse 11]")),
            "current behaviour: the title comes back over verse 11",
        )
        assertTrue(shouldShowText(Constants.FIRST_PAGE, section("[Verse 21]")))
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
        assertEquals(TextAlign.Start.toString(), textAlign(Constants.LEFT), "Start rather than Left, so RTL text still reads")
        assertEquals(TextAlign.End.toString(), textAlign(Constants.RIGHT))
        assertEquals(TextAlign.Center.toString(), textAlign(Constants.CENTER))
    }

    @Test
    fun `an unrecognised alignment centres rather than collapsing to one edge`() {
        listOf("", "Justify", "top").forEach { assertEquals(TextAlign.Center.toString(), textAlign(it)) }
    }
}
