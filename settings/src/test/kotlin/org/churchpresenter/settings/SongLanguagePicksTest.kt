package org.churchpresenter.settings

import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A profile's song languages are places in the Songs tab's display order, not the song's own slots
 * (#1029): moving Yoruba to the top puts Yoruba on every output showing the first place, and each
 * language is drawn with its place's look unless the profile's looks follow the language.
 */
class SongLanguagePicksTest {

    private val yorubaFirst = listOf(1, 0, 2, 3)

    private fun picks(
        mode: String = Constants.SONG_LANG_BOTH,
        places: List<Int> = emptyList(),
        available: Int = 2,
        order: List<Int> = yorubaFirst,
        followsPlace: Boolean = true,
    ) = songLanguagePicks(mode, places, available, order, followsPlace)

    @Test
    fun `the first place shows whichever language is first in the order`() {
        assertEquals(listOf(SongLanguagePick(slot = 1, look = 0)), picks(places = listOf(0)))
        assertEquals(listOf(SongLanguagePick(slot = 0, look = 1)), picks(places = listOf(1)))
    }

    @Test
    fun `the one-language modes name places too`() {
        assertEquals(listOf(1), picks(mode = Constants.SONG_LANG_PRIMARY).map { it.slot })
        assertEquals(listOf(0), picks(mode = Constants.SONG_LANG_SECONDARY).map { it.slot })
    }

    @Test
    fun `every language is drawn in the display order`() {
        assertEquals(listOf(1, 0), picks().map { it.slot })
        assertEquals(listOf(0, 1), picks().map { it.look }, "each in its place's look")
    }

    @Test
    fun `following the language, each keeps its own look`() {
        assertEquals(
            listOf(SongLanguagePick(slot = 1, look = 1)),
            picks(places = listOf(0), followsPlace = false),
        )
    }

    @Test
    fun `places count among the song's own languages`() {
        // Yoruba was moved ahead of English in a three-language song, so the order is 2,1,0. A
        // two-language song has no slot 2, and the Languages menu lists it as Yoruba then English.
        val order = listOf(2, 1, 0, 3)

        assertEquals(listOf(1), picks(places = listOf(0), order = order).map { it.slot })
        assertEquals(listOf(2, 1, 0), songLanguageOrder(order, available = 3), "a three-language song keeps all three")
    }

    @Test
    fun `a place the song has no language for shows the first language in that place's look`() {
        assertEquals(
            listOf(SongLanguagePick(slot = 0, look = 2)),
            picks(places = listOf(2), available = 2),
        )
        assertEquals(
            listOf(SongLanguagePick(slot = 0, look = 0)),
            picks(places = listOf(2), available = 2, followsPlace = false),
        )
    }

    @Test
    fun `an order never set leaves every place on its own slot`() {
        assertEquals(listOf(0, 2), songLanguageSelection(Constants.SONG_LANG_BOTH, listOf(0, 2), available = 4))
    }

    @Test
    fun `a song's own order fills in the slots the stored order leaves out`() {
        assertEquals(listOf(1, 0, 2), songLanguageOrder(listOf(1), available = 3))
        assertEquals(listOf(0, 1), songLanguageOrder(listOf(9, 0, 0), available = 2), "strays and repeats ignored")
    }
}
