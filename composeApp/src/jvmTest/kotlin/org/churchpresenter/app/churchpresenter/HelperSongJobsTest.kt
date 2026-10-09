package org.churchpresenter.app.churchpresenter

import org.churchpresenter.core.models.songs.SongItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HelperSongJobsTest {

    private val library = listOf(
        SongItem(number = "12", title = "Grace Alone"),
        SongItem(number = "245", title = "Amazing Grace"),
        SongItem(number = "300", title = "Amazing Grace (Chains Are Gone)"),
        SongItem(number = "7", title = "How Great Thou Art"),
    )

    private fun find(query: String) = findHelperSong(library, query)?.number

    @Test
    fun `a number finds that song`() = assertEquals("245", find("245"))

    @Test
    fun `an exact title wins over a longer one that starts the same`() = assertEquals("245", find("amazing grace"))

    @Test
    fun `the start of a title finds it`() = assertEquals("7", find("How Great"))

    @Test
    fun `words from inside a title find it when nothing starts with them`() = assertEquals("300", find("chains"))

    @Test
    fun `nothing like it finds nothing`() = assertNull(find("blessed assurance"))
}
