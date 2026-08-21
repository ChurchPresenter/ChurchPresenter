@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Finding a song in the Songs tab.
 *
 * This is the tab's whole job during a service: an operator with a number called from the platform,
 * or half a remembered title, has to get to the right song in one go. So these cover what the search
 * box actually narrows the list to, and — as importantly — what it does *not* drop.
 *
 * The songs are written to disk and read back through the real loader, so nothing here can pass
 * against a fixture the app would not itself produce. See `SongsTabTestSupport` for why this tab is
 * reachable in a test at all.
 */
class SongsTabSearchTest {

    @Test
    fun `every song is listed before anything is searched for, grouped by songbook`() = songsTab { _, _ ->
        // Chorus Book sorts before Hymnal, and within a songbook the rows follow the song number as
        // text — so 1, 12, 2 rather than 1, 2, 12. Pinned as it stands rather than as one might wish.
        assertEquals(
            listOf("How Great Thou Art", "Amazing Grace", "Amazing Love", "Be Thou My Vision"),
            listedTitles(),
            "the tab opens on the whole library, across songbooks",
        )
    }

    @Test
    fun `a title search narrows the list to the matches`() = songsTab { _, _ ->
        search("Amazing")
        assertEquals(
            listOf("Amazing Grace", "Amazing Love"),
            listedTitles(),
            "both Amazing songs match; the others must go",
        )
    }

    @Test
    fun `search matches the middle of a title, not just the start`() = songsTab { _, _ ->
        // The operator remembers "Vision", not "Be Thou".
        search("Vision")
        assertEquals(listOf("Be Thou My Vision"), listedTitles())
    }

    @Test
    fun `search ignores case`() = songsTab { _, _ ->
        search("amazing grace")
        assertTrue(shows("Amazing Grace"), "a lowercase query must still find it")
    }

    @Test
    fun `a song number finds that song`() = songsTab { _, _ ->
        // A number called out from the platform is the most common way in.
        search("12")
        assertTrue(shows("Amazing Love"), "song 12 must be reachable by its number")
    }

    @Test
    fun `a query matching nothing empties the list rather than showing everything`() = songsTab { _, _ ->
        search("Zzzzz No Such Song")
        assertEquals(
            emptyList(),
            listedTitles(),
            "a failed search must not silently fall back to the full library",
        )
    }

    @Test
    fun `clearing the query brings the whole library back`() = songsTab { _, _ ->
        search("Amazing")
        assertEquals(2, listedTitles().size)

        search("")
        assertEquals(4, listedTitles().size, "an emptied box is the same as never having searched")
    }

    @Test
    fun `searching finds songs in every songbook, not only the first`() = songsTab { _, _ ->
        // "How Great Thou Art" lives in Chorus Book while the rest are in Hymnal.
        search("How Great")
        assertEquals(listOf("How Great Thou Art"), listedTitles())
    }

    @Test
    fun `a stray space around the query does not lose the song`() = songsTab { _, _ ->
        // Was issue #70: the query was matched raw, so a query pasted from a service plan or an
        // email came back empty with nothing on screen to explain why. Only the ends are trimmed.
        search("  Amazing Grace  ")
        assertTrue(shows("Amazing Grace"), "a leading and trailing space must not lose the match")

        search("Amazing Grace  ")
        assertTrue(shows("Amazing Grace"), "nor a trailing one alone")

        search("  Amazing")
        assertEquals(
            listOf("Amazing Grace", "Amazing Love"),
            listedTitles(),
            "a leading space must not change which songs match either",
        )
    }

    @Test
    fun `whitespace inside a query is still significant`() = songsTab { _, _ ->
        // Only the ends are trimmed. A doubled space in the middle is a different query, so this
        // still finds nothing — trimming must not quietly become "normalise all whitespace".
        search("Amazing  Grace")
        assertEquals(emptyList(), listedTitles(), "the inner double space is part of what was asked for")
    }

    @Test
    fun `a query of nothing but spaces lists everything, as an empty box does`() = songsTab { _, _ ->
        search("     ")
        assertEquals(4, listedTitles().size, "whitespace alone is not a search")
    }

    // ── Filter mode ─────────────────────────────────────────────────────────────

    @Test
    fun `switching to Starts With stops matching the middle of a title`() = songsTab { vm, _ ->
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText(SongsLabel.STARTS_WITH).performClick()
        waitForIdle()

        assertEquals(Constants.STARTS_WITH, vm.filterType.value)

        search("Vision")
        assertEquals(emptyList(), listedTitles(), "\"Vision\" is mid-title, not a start, in Starts With mode")

        search("Be Thou")
        assertEquals(listOf("Be Thou My Vision"), listedTitles())
    }

    @Test
    fun `switching to Exact Match requires the whole title`() = songsTab { vm, _ ->
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText(SongsLabel.EXACT_MATCH).performClick()
        waitForIdle()

        assertEquals(Constants.EXACT_MATCH, vm.filterType.value)

        search("Amazing")
        assertEquals(emptyList(), listedTitles(), "a partial title must not match in Exact Match mode")

        search("Amazing Grace")
        assertEquals(listOf("Amazing Grace"), listedTitles())
    }

    @Test
    fun `switching back to Contains restores the middle-of-title match`() = songsTab { vm, _ ->
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText(SongsLabel.EXACT_MATCH).performClick()
        waitForIdle()
        onNodeWithText("FILTER", substring = true).performClick()
        waitForIdle()
        onNodeWithText(SongsLabel.CONTAINS).performClick()
        waitForIdle()

        assertEquals(Constants.CONTAINS, vm.filterType.value)
        search("Vision")
        assertEquals(listOf("Be Thou My Vision"), listedTitles())
    }

    // ── Hidden rebuild ──────────────────────────────────────────────────────────

    @Test
    fun `three rapid clicks on the search button reload songs from disk`() = songsTab { _, reports ->
        val parser = SongFileParser()
        val book = File(reports.songsDir, "Hymnal")
        parser.writeSongFile(
            SongItem(number = "99", title = "Added After Load", songbook = "Hymnal"),
            File(book, "99 - Added After Load.song").absolutePath,
        )
        assertFalse(shows("Added After Load"), "not yet loaded — the file only just landed on disk")

        val searchButton = onNodeWithContentDescription("Search")
        searchButton.performClick()
        searchButton.performClick()
        searchButton.performClick()
        waitForIdle()

        assertTrue(shows("Added After Load"), "three rapid clicks must force a reload from disk")
    }

    @Test
    fun `two clicks alone do not trigger a reload`() = songsTab { _, reports ->
        val parser = SongFileParser()
        val book = File(reports.songsDir, "Hymnal")
        parser.writeSongFile(
            SongItem(number = "99", title = "Added After Load", songbook = "Hymnal"),
            File(book, "99 - Added After Load.song").absolutePath,
        )

        val searchButton = onNodeWithContentDescription("Search")
        searchButton.performClick()
        waitForIdle()
        searchButton.performClick()
        waitForIdle()

        assertFalse(shows("Added After Load"), "the threshold is three clicks, not two")
    }

    // ── What the tab shows around the list ──────────────────────────────────────

    @Test
    fun `the search box and the songbook filter are both offered`() = songsTab { _, _ ->
        assertTrue(shows(SongsLabel.SEARCH_PLACEHOLDER), "the empty box names itself")
        // DropdownSelector merges caption and value into one node, hence the substring match.
        assertTrue(showsContaining(SongsLabel.ALL_SONGBOOKS), "the songbook filter starts unrestricted")
        assertTrue(showsContaining(SongsLabel.CONTAINS), "and the filter mode starts on Contains")
    }

    @Test
    fun `an empty library lists nothing and does not claim to be searching`() = songsTab(songs = emptyList()) { _, _ ->
        assertEquals(emptyList(), listedTitles())
        assertFalse(shows("Amazing Grace"))
    }
}
