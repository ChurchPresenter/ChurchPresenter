@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * The recent-files bar in the Media tab: which files it offers and in what order.
 *
 * Same shape as [PicturesTabRecentFoldersTest], and the same reasoning. [RecentMediaFiles] renders
 * the bar straight off two `mutableStateListOf`s, so seeding those drives it with no file I/O and no
 * `user.home` swap.
 *
 * `add`/`togglePin`/`clear` themselves — and the JSON they read and write — are covered separately in
 * [RecentMediaFilesLogicTest], which repoints [RecentMediaFiles.file] and [RecentMediaFiles.pinnedFile]
 * at a temp dir rather than clicking through this bar; nothing here clicks a chip, a star or the clear
 * button.
 *
 * `clear` here also **keeps pinned files**, same as the pictures bar, which is why the ordering tests
 * below treat pinned and recent as overlapping sets rather than as two disjoint lists.
 */
class MediaTabRecentFilesTest {

    private lateinit var savedPaths: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedPaths = RecentMediaFiles.paths.toList()
        savedPinned = RecentMediaFiles.pinned.toList()
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentMediaFiles.paths.clear()
        RecentMediaFiles.paths.addAll(savedPaths)
        RecentMediaFiles.pinned.clear()
        RecentMediaFiles.pinned.addAll(savedPinned)
    }

    private fun seed(paths: List<String> = emptyList(), pinned: List<String> = emptyList()) {
        RecentMediaFiles.paths.addAll(paths)
        RecentMediaFiles.pinned.addAll(pinned)
    }

    /** Chips are labelled with the file name, so the paths differ but the leaf name is the label. */
    private fun path(name: String) = "/Volumes/Services/media/$name.mp4"

    private companion object {
        /** Exactly as `Res.string.recent` spells it — asserting on "Recent" would never match. */
        const val BAR_LABEL = "Recent:"
    }

    @Test
    fun `no recent files means no bar at all`() {
        mediaTab { _, _ ->
            waitForIdle()
            assertFalse(
                showsExactly(BAR_LABEL),
                "an empty bar would take a strip of height from the player for nothing"
            )
        }
    }

    @Test
    fun `recent files are offered by file name`() {
        seed(paths = listOf(path("advent-promo"), path("baptism")))

        mediaTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("advent-promo.mp4"), renderedText().toString())
            assertTrue(showsExactly("baptism.mp4"), renderedText().toString())
        }
    }

    @Test
    fun `a network url is offered by its full address, not a bare file name`() {
        seed(paths = listOf("https://example.org/stream.m3u8"))

        mediaTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("https://example.org/stream.m3u8"), renderedText().toString())
        }
    }

    @Test
    fun `pinned files come before the merely recent ones`() {
        seed(
            paths = listOf(path("opened-today"), path("opened-yesterday")),
            pinned = listOf(path("every-week")),
        )

        mediaTab { _, _ ->
            waitForIdle()
            val shown = renderedText()
            val pinnedAt = shown.indexOf("every-week.mp4")
            val recentAt = shown.indexOf("opened-today.mp4")
            assertTrue(pinnedAt >= 0 && recentAt >= 0, "both should be on screen: $shown")
            assertTrue(pinnedAt < recentAt, "the pinned file leads the bar: $shown")
        }
    }

    @Test
    fun `a file that is both pinned and recent is offered once`() {
        val both = path("every-week")
        seed(paths = listOf(both, path("baptism")), pinned = listOf(both))

        mediaTab { _, _ ->
            waitForIdle()
            assertEquals(
                1, renderedText().count { it == "every-week.mp4" },
                "the same file twice in the bar is two chips that do the same thing"
            )
        }
    }

    @Test
    fun `a pinned file is still offered when it is the only one`() {
        seed(pinned = listOf(path("every-week")))

        mediaTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("every-week.mp4"), renderedText().toString())
            assertTrue(showsExactly(BAR_LABEL), "the bar's own label is part of it being there")
        }
    }
}
