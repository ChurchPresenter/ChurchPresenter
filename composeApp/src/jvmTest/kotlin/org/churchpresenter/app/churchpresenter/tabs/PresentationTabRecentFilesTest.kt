@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import org.churchpresenter.app.churchpresenter.data.RecentPresentationFiles
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsExactly

/**
 * The recent-files bar: which decks it offers and in what order.
 *
 * This document listed the bar as unreachable, "only through a `private object` that resolves JSON
 * paths from `user.home` at class-init". Half of that is wrong. [RecentPresentationFiles] is public,
 * and its `files` and `pinned` are `mutableStateListOf` — the bar renders straight off them, so
 * seeding those lists drives it with no file I/O, no `user.home` swap and no new test seam.
 *
 * What the entry got right is the persistence: the two JSON paths *are* resolved at class-init, so
 * whichever test touches the object first decides where the whole JVM writes. That is why nothing
 * here clicks a chip, a star or the clear button — `add`, `togglePin` and `clear` all save, and
 * `clear` against a real home would delete the developer's own recent-files list. Those handful of
 * lines stay uncovered on purpose; the ordering and rendering below are the part worth having.
 *
 * The ordering is the part that is easy to get wrong and annoying live: pinned decks come first, a
 * deck that is both pinned and recent must appear once rather than twice, and the deck currently
 * loaded is highlighted so an operator can see at a glance which of ten similar file names is up.
 */
class PresentationTabRecentFilesTest {

    private lateinit var savedFiles: List<String>
    private lateinit var savedPinned: List<String>

    @BeforeTest
    fun snapshotRecents() {
        // The object loads the developer's real lists at class-init. Put them back afterwards, and
        // never call anything that saves.
        savedFiles = RecentPresentationFiles.files.toList()
        savedPinned = RecentPresentationFiles.pinned.toList()
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
    }

    @AfterTest
    fun restoreRecents() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.files.addAll(savedFiles)
        RecentPresentationFiles.pinned.clear()
        RecentPresentationFiles.pinned.addAll(savedPinned)
    }

    private fun seed(files: List<String> = emptyList(), pinned: List<String> = emptyList()) {
        RecentPresentationFiles.files.addAll(files)
        RecentPresentationFiles.pinned.addAll(pinned)
    }

    /** Chip labels are file names, so the paths differ but the names are what a test looks for. */
    private fun path(name: String) = "/Volumes/Services/decks/$name"

    /**
     * The bar's own label, exactly as the resource spells it. Asserting on "Recent" instead reads as
     * absent whether the bar is there or not — which quietly turned the empty-bar test below into one
     * that measured nothing until it was checked against the string resource.
     */
    private companion object {
        const val BAR_LABEL = "Recent:"
    }

    @Test
    fun `no recent decks means no bar at all`() {
        presentationTab { _, _ ->
            waitForIdle()
            assertFalse(
                showsExactly(BAR_LABEL),
                "an empty bar would take a strip of height from the slide grid for nothing"
            )
        }
    }

    @Test
    fun `recent decks are offered by file name`() {
        seed(files = listOf(path("Sunday Morning.pdf"), path("Notices.pptx")))

        presentationTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("Sunday Morning.pdf"), renderedText().toString())
            assertTrue(showsExactly("Notices.pptx"), renderedText().toString())
        }
    }

    @Test
    fun `pinned decks come before the merely recent ones`() {
        // The order is the whole point of pinning: the deck used every week should not drift down
        // the list behind whatever was opened most recently.
        seed(
            files = listOf(path("Opened Today.pdf"), path("Opened Yesterday.pdf")),
            pinned = listOf(path("Every Week.pdf")),
        )

        presentationTab { _, _ ->
            waitForIdle()
            val shown = renderedText()
            val pinnedAt = shown.indexOf("Every Week.pdf")
            val recentAt = shown.indexOf("Opened Today.pdf")
            assertTrue(pinnedAt >= 0 && recentAt >= 0, "both should be on screen: $shown")
            assertTrue(pinnedAt < recentAt, "the pinned deck leads the bar: $shown")
        }
    }

    @Test
    fun `a deck that is both pinned and recent is offered once`() {
        // Pinning does not remove a deck from the recent list, so the two overlap constantly.
        val both = path("Every Week.pdf")
        seed(files = listOf(both, path("Notices.pptx")), pinned = listOf(both))

        presentationTab { _, _ ->
            waitForIdle()
            assertEquals(
                1, renderedText().count { it == "Every Week.pdf" },
                "the same deck twice in the bar is two chips that do the same thing"
            )
        }
    }

    @Test
    fun `a pinned deck is still offered when it is the only one`() {
        seed(pinned = listOf(path("Every Week.pdf")))

        presentationTab { _, _ ->
            waitForIdle()
            assertTrue(showsExactly("Every Week.pdf"), renderedText().toString())
            assertTrue(showsExactly(BAR_LABEL), "the bar's own label is part of it being there")
        }
    }
}
