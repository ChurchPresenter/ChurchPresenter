package ui

import converter.song.SongFormatConverters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The "Convert from" rail.
 *
 * The rail deliberately lists only formats that actually convert — an entry with no converter behind
 * it would open a panel that cannot do anything, which is why the two lists are checked against each
 * other here rather than trusted to stay in step by hand.
 */
class SongSourcesTest {

    @Test
    fun `every rail entry has a converter behind it, and every converter an entry`() {
        assertEquals(
            SongFormatConverters.all.map { it.id }.toSet(),
            SongSources.all.map { it.id }.toSet()
        )
    }

    @Test
    fun `ids are unique`() {
        val ids = SongSources.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, ids.toString())
    }

    @Test
    fun `an unknown id falls back to the default rather than throwing`() {
        assertSame(SongSources.default, SongSources.byId("songshowplus"))
        assertSame(SongSources.default, SongSources.byId(""))
    }

    @Test
    fun `an empty query lists everything, in rail order`() {
        assertEquals(SongSources.all, SongSources.matching(""))
        assertEquals(SongSources.all, SongSources.matching("   "))
    }

    @Test
    fun `search matches the product name regardless of case`() {
        assertEquals(listOf("songbeamer"), SongSources.matching("songbeamer").map { it.id })
        assertEquals(listOf("songbeamer"), SongSources.matching("SongBeamer").map { it.id })
        assertEquals(listOf("songbeamer"), SongSources.matching("BEAM").map { it.id })
    }

    @Test
    fun `search matches a partial name with a space in it`() {
        assertEquals(listOf("freeworship"), SongSources.matching("free wor").map { it.id })
    }

    @Test
    fun `search matches the extension, which is how people look for a file they have`() {
        assertEquals(listOf("songbeamer"), SongSources.matching(".sng").map { it.id })
        assertEquals(listOf("softprojector"), SongSources.matching("sps").map { it.id })
        assertEquals(listOf("quelea"), SongSources.matching(".qsp").map { it.id })
    }

    @Test
    fun `an extension several apps share lists all of them, in rail order`() {
        assertEquals(
            listOf("easyslides", "freeworship", "openlp", "opensong", "quelea"),
            SongSources.matching(".xml").map { it.id }
        )
    }

    @Test
    fun `a query matching nothing returns empty, which is what drives the no-match message`() {
        assertTrue(SongSources.matching("songshow plus").isEmpty())
    }

    @Test
    fun `every entry resolves a description and an accepts line`() {
        for (source in SongSources.all) {
            assertTrue(source.description.isNotBlank(), source.id)
            assertTrue(source.accepts.isNotBlank(), source.id)
        }
    }

    @Test
    fun `song formats are listed alphabetically, so the rail can be scanned by name`() {
        val names = SongSources.all.filter { it.group == SourceGroup.SONGS }.map { it.name }
        assertEquals(names.sortedBy { it.replace(" ", "").lowercase() }, names)
    }

    @Test
    fun `the default is named rather than whichever entry happens to sort first`() {
        assertEquals("songbeamer", SongSources.default.id)
    }

    @Test
    fun `every group in use has a label`() {
        for (group in SongSources.all.map { it.group }.distinct()) {
            assertTrue(SongSources.groupLabel(group).isNotBlank(), group.name)
        }
    }

    @Test
    fun `initials are the two characters the rail tile is sized for`() {
        for (source in SongSources.all) {
            assertEquals(2, source.initials.length, "${source.id}: ${source.initials}")
        }
    }
}
