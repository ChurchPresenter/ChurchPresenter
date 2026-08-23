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
 * The recent-folders bar in the Pictures tab: which folders it offers and in what order.
 *
 * Same shape as [PresentationTabRecentFilesTest], and the same reasoning. [RecentPictureFolders]
 * renders the bar straight off two `mutableStateListOf`s, so seeding those drives it with no file
 * I/O and no `user.home` swap.
 *
 * `add`/`togglePin`/`clear` themselves — and the JSON they read and write — are covered separately
 * in [RecentPictureFoldersLogicTest], which repoints [RecentPictureFolders.file] and
 * [RecentPictureFolders.pinnedFile] at a temp dir rather than clicking through this bar; nothing here
 * clicks a chip, a star or the clear button.
 *
 * One difference from the presentation bar is worth pinning: `clear` here **keeps pinned folders**,
 * which is why the ordering tests below treat pinned and recent as overlapping sets rather than as
 * two disjoint lists.
 */
class PicturesTabRecentFoldersTest {

    private lateinit var savedFolders: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        savedFolders = RecentPictureFolders.folders.toList()
        savedPinned = RecentPictureFolders.pinned.toList()
        RecentPictureFolders.folders.clear()
        RecentPictureFolders.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentPictureFolders.folders.clear()
        RecentPictureFolders.folders.addAll(savedFolders)
        RecentPictureFolders.pinned.clear()
        RecentPictureFolders.pinned.addAll(savedPinned)
    }

    private fun seed(folders: List<String> = emptyList(), pinned: List<String> = emptyList()) {
        RecentPictureFolders.folders.addAll(folders)
        RecentPictureFolders.pinned.addAll(pinned)
    }

    /** Chips are labelled with the folder name, so the paths differ but the leaf name is the label. */
    private fun path(name: String) = "/Volumes/Services/photos/$name"

    private companion object {
        /** Exactly as `Res.string.recent` spells it — asserting on "Recent" would never match. */
        const val BAR_LABEL = "Recent:"
    }

    @Test
    fun `no recent folders means no bar at all`() {
        picturesTab { _, _ ->
            waitForIdle()
            assertFalse(
                showsExactly(BAR_LABEL),
                "an empty bar would take a strip of height from the thumbnail grid for nothing"
            )
        }
    }

    @Test
    fun `recent folders are offered by folder name`() {
        seed(folders = listOf(path("Advent 2026"), path("Baptism")))

        picturesTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("Advent 2026"), renderedText().toString())
            assertTrue(showsExactly("Baptism"), renderedText().toString())
        }
    }

    @Test
    fun `pinned folders come before the merely recent ones`() {
        seed(
            folders = listOf(path("Opened Today"), path("Opened Yesterday")),
            pinned = listOf(path("Every Week")),
        )

        picturesTab { _, _ ->
            waitForIdle()
            val shown = renderedText()
            val pinnedAt = shown.indexOf("Every Week")
            val recentAt = shown.indexOf("Opened Today")
            assertTrue(pinnedAt >= 0 && recentAt >= 0, "both should be on screen: $shown")
            assertTrue(pinnedAt < recentAt, "the pinned folder leads the bar: $shown")
        }
    }

    @Test
    fun `a folder that is both pinned and recent is offered once`() {
        val both = path("Every Week")
        seed(folders = listOf(both, path("Baptism")), pinned = listOf(both))

        picturesTab { _, _ ->
            waitForIdle()
            assertEquals(
                1, renderedText().count { it == "Every Week" },
                "the same folder twice in the bar is two chips that do the same thing"
            )
        }
    }

    @Test
    fun `a pinned folder is still offered when it is the only one`() {
        seed(pinned = listOf(path("Every Week")))

        picturesTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("Every Week"), renderedText().toString())
            assertTrue(showsExactly(BAR_LABEL), "the bar's own label is part of it being there")
        }
    }
}
