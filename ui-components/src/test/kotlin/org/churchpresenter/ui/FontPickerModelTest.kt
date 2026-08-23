package org.churchpresenter.ui

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The list the font picker draws: what a search leaves, how it is grouped, and where the highlight goes. */
class FontPickerModelTest {

    private fun face(
        name: String,
        category: FontCategory = FontCategory.SANS,
        cyrillic: Boolean = false,
        hebrew: Boolean = false,
        recommended: Boolean = false,
    ) = FontFace(name, category, cyrillic, hebrew, recommended)

    private val arial = face("Arial", cyrillic = true, hebrew = true, recommended = true)
    private val arialBlack = face("Arial Black", FontCategory.DISPLAY, cyrillic = true)
    private val georgia = face("Georgia", FontCategory.SERIF, cyrillic = true, recommended = true)
    private val papyrus = face("Papyrus", FontCategory.DISPLAY)
    private val installed = listOf(arial, arialBlack, georgia, papyrus)

    @BeforeTest
    fun clearRecents() = RecentFonts.clear()

    @AfterTest
    fun forgetRecents() = RecentFonts.clear()

    // --- searching ---

    @Test
    fun `an empty query leaves every family`() {
        assertEquals(installed, filterFonts(installed, ""))
    }

    @Test
    fun `a query matches anywhere in the name, in any case`() {
        assertEquals(listOf(arial, arialBlack), filterFonts(installed, "ARIAL"))
        assertEquals(listOf(arialBlack), filterFonts(installed, "black"))
    }

    // --- grouping ---

    @Test
    fun `untouched, the list leads with what is worth projecting`() {
        val groups = groupFonts(installed, query = "", recents = emptyList())

        assertEquals(listOf(FontGroupKind.RECOMMENDED, FontGroupKind.ALL), groups.map { it.kind })
        assertEquals(listOf(arial, georgia), groups[0].items)
        assertEquals(listOf(arialBlack, papyrus), groups[1].items)
    }

    @Test
    fun `a family used this session leads, and is not repeated further down`() {
        val groups = groupFonts(installed, query = "", recents = listOf("Georgia"))

        assertEquals(listOf(FontGroupKind.RECENT, FontGroupKind.RECOMMENDED, FontGroupKind.ALL), groups.map { it.kind })
        assertEquals(listOf(georgia), groups[0].items)
        // Georgia is recommended as well, and a picker that lists it twice has wasted the top row.
        assertEquals(listOf(arial), groups[1].items)
    }

    @Test
    fun `typing collapses the groups into one run of matches`() {
        val groups = groupFonts(installed, query = "arial", recents = listOf("Georgia"))

        assertEquals(listOf(FontGroupKind.MATCHES), groups.map { it.kind })
        assertEquals(listOf(arial, arialBlack), groups[0].items)
    }

    @Test
    fun `a query matching nothing leaves no groups at all`() {
        assertEquals(emptyList(), groupFonts(installed, query = "kiwi", recents = emptyList()))
    }

    @Test
    fun `a recent family that is no longer installed is simply skipped`() {
        val groups = groupFonts(installed, query = "", recents = listOf("Uninstalled", "Arial"))

        assertEquals(listOf(arial), groups.first { it.kind == FontGroupKind.RECENT }.items)
    }

    @Test
    fun `the arrow keys walk the groups in the order they are drawn`() {
        val groups = groupFonts(installed, query = "", recents = listOf("Papyrus"))

        assertEquals(listOf("Papyrus", "Arial", "Georgia", "Arial Black"), visibleFonts(groups).map { it.name })
    }

    // --- the highlight ---

    @Test
    fun `the highlight stays on a family that survived the search`() {
        assertEquals("Arial", highlightAfterFilter(listOf(arial, arialBlack), current = "Arial"))
    }

    @Test
    fun `the highlight moves to the first row when what it was on is filtered away`() {
        assertEquals("Arial", highlightAfterFilter(listOf(arial, arialBlack), current = "Georgia"))
    }

    @Test
    fun `nothing to highlight leaves it empty`() {
        assertEquals("", highlightAfterFilter(emptyList(), current = "Georgia"))
    }

    // --- what a family cannot draw ---

    @Test
    fun `a Cyrillic verse in a family without Cyrillic is reported`() {
        assertEquals(listOf(PreviewScript.CYRILLIC), missingScripts("В начале", papyrus))
        assertEquals(emptyList(), missingScripts("В начале", arial))
    }

    @Test
    fun `a Hebrew verse in a family without Hebrew is reported`() {
        assertEquals(listOf(PreviewScript.HEBREW), missingScripts("בְּרֵאשִׁית", georgia))
        assertEquals(emptyList(), missingScripts("בְּרֵאשִׁית", arial))
    }

    @Test
    fun `a Latin verse asks nothing of a family beyond Latin`() {
        assertEquals(emptyList(), missingScripts("In the beginning", papyrus))
    }

    @Test
    fun `both scripts missing are both reported`() {
        assertEquals(
            listOf(PreviewScript.CYRILLIC, PreviewScript.HEBREW),
            missingScripts("В начале · בְּרֵאשִׁית", papyrus),
        )
    }

    // --- what was picked this session ---

    @Test
    fun `the most recent pick leads the list`() {
        RecentFonts.record("Arial")
        RecentFonts.record("Georgia")

        assertEquals(listOf("Georgia", "Arial"), RecentFonts.names)
    }

    @Test
    fun `picking the same family again moves it up rather than repeating it`() {
        listOf("Arial", "Georgia", "Arial").forEach(RecentFonts::record)

        assertEquals(listOf("Arial", "Georgia"), RecentFonts.names)
    }

    @Test
    fun `only the last three are kept`() {
        listOf("Arial", "Georgia", "Papyrus", "Verdana").forEach(RecentFonts::record)

        assertEquals(listOf("Verdana", "Papyrus", "Georgia"), RecentFonts.names)
    }

    @Test
    fun `a blank name is not a pick`() {
        RecentFonts.record("")

        assertTrue(RecentFonts.names.isEmpty())
    }

    // --- the verses the preview quotes ---

    @Test
    fun `two translations that read alike are quoted once`() {
        val tidied = tidyPreviewLines(listOf("In the beginning ", "In the beginning", "В начале"))

        assertEquals(listOf("In the beginning", "В начале"), tidied)
    }

    @Test
    fun `blank verses are dropped and the box takes at most three`() {
        val tidied = tidyPreviewLines(listOf("one", "  ", "two", "three", "four"))

        assertEquals(listOf("one", "two", "three"), tidied)
    }
}
