package org.churchpresenter.app.churchpresenter.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The recent-and-pinned list under the presentation picker.
 *
 * Two rules do the work: the list is capped, so an operator who opens a deck every week still sees
 * this month's, and pinning survives the "clear recents" button — pinning a deck is how someone
 * keeps the file they use every single service from scrolling off the end.
 *
 * [RecentPresentationFiles] is a JVM-wide object that resolves its two JSON files when the class is
 * first loaded and reads them in its initialiser, so it cannot be rebuilt per test. Its own paths
 * are therefore read back by reflection, and each test starts by emptying both the in-memory lists
 * and those files — swapping `user.home` here would have no effect, since the paths were already
 * fixed by then.
 */
class RecentPresentationFilesTest {

    private fun pathField(name: String): File =
        RecentPresentationFiles::class.java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(RecentPresentationFiles) as File

    private val recentsFile: File get() = pathField("file")
    private val pinnedFile: File get() = pathField("pinnedFile")

    @BeforeTest
    fun emptyEverything() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
        recentsFile.delete()
        pinnedFile.delete()
    }

    @AfterTest
    fun leaveNothingBehind() {
        RecentPresentationFiles.files.clear()
        RecentPresentationFiles.pinned.clear()
        recentsFile.delete()
        pinnedFile.delete()
    }

    private fun savedRecents(): List<String> =
        if (!recentsFile.exists()) emptyList()
        else Json.decodeFromString(ListSerializer(String.serializer()), recentsFile.readText())

    private fun savedPinned(): List<String> =
        if (!pinnedFile.exists()) emptyList()
        else Json.decodeFromString(ListSerializer(String.serializer()), pinnedFile.readText())

    private val recents get() = RecentPresentationFiles.files.toList()
    private val pinned get() = RecentPresentationFiles.pinned.toList()

    // ── Recents ─────────────────────────────────────────────────────────────────

    @Test
    fun `nothing has been opened yet`() {
        assertTrue(recents.isEmpty())
        assertTrue(pinned.isEmpty())
    }

    @Test
    fun `the most recently opened deck is first`() {
        RecentPresentationFiles.add("/decks/first.pptx")
        RecentPresentationFiles.add("/decks/second.pptx")

        assertEquals(listOf("/decks/second.pptx", "/decks/first.pptx"), recents)
    }

    @Test
    fun `opening a deck again moves it to the top rather than listing it twice`() {
        RecentPresentationFiles.add("/decks/a.pptx")
        RecentPresentationFiles.add("/decks/b.pptx")

        RecentPresentationFiles.add("/decks/a.pptx")

        assertEquals(listOf("/decks/a.pptx", "/decks/b.pptx"), recents, "a duplicate row wastes the short list")
    }

    @Test
    fun `the list is capped and the oldest drops off`() {
        repeat(12) { RecentPresentationFiles.add("/decks/deck$it.pptx") }

        assertEquals(10, recents.size)
        assertEquals("/decks/deck11.pptx", recents.first(), "newest first")
        assertEquals("/decks/deck2.pptx", recents.last(), "the two oldest fell off the end")
    }

    @Test
    fun `recents are written to disk as they are opened`() {
        RecentPresentationFiles.add("/decks/first.pptx")
        RecentPresentationFiles.add("/decks/second.pptx")

        assertEquals(
            listOf("/decks/second.pptx", "/decks/first.pptx"),
            savedRecents(),
            "the list has to be there when the app comes back",
        )
    }

    // ── Pinning ─────────────────────────────────────────────────────────────────

    @Test
    fun `pinning a deck keeps it to hand`() {
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        assertEquals(listOf("/decks/liturgy.pptx"), pinned)
    }

    @Test
    fun `pinning again unpins`() {
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        assertTrue(pinned.isEmpty(), "the same control has to let go of it again")
    }

    @Test
    fun `the most recently pinned deck is first`() {
        RecentPresentationFiles.togglePin("/decks/one.pptx")
        RecentPresentationFiles.togglePin("/decks/two.pptx")

        assertEquals(listOf("/decks/two.pptx", "/decks/one.pptx"), pinned)
    }

    @Test
    fun `pins are written to disk as they are made`() {
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        assertEquals(listOf("/decks/liturgy.pptx"), savedPinned())

        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        assertTrue(savedPinned().isEmpty(), "unpinning must stick too")
    }

    @Test
    fun `pinning a deck does not put it in recents`() {
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        assertTrue(recents.isEmpty(), "the two lists are kept apart; pinning is not opening")
    }

    // ── Clearing ────────────────────────────────────────────────────────────────

    @Test
    fun `clearing empties the recent list`() {
        RecentPresentationFiles.add("/decks/a.pptx")
        RecentPresentationFiles.add("/decks/b.pptx")

        RecentPresentationFiles.clear()

        assertTrue(recents.isEmpty())
        assertTrue(savedRecents().isEmpty(), "and stays cleared after a restart")
    }

    @Test
    fun `a pinned deck survives clearing`() {
        RecentPresentationFiles.add("/decks/ordinary.pptx")
        RecentPresentationFiles.add("/decks/liturgy.pptx")
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        RecentPresentationFiles.clear()

        assertEquals(
            listOf("/decks/liturgy.pptx"),
            recents,
            "pinning is how the weekly deck is kept out of the way of the clear button",
        )
        assertEquals(listOf("/decks/liturgy.pptx"), savedRecents())
    }

    @Test
    fun `clearing keeps the pins themselves`() {
        RecentPresentationFiles.add("/decks/liturgy.pptx")
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")

        RecentPresentationFiles.clear()

        assertEquals(listOf("/decks/liturgy.pptx"), pinned)
        assertEquals(listOf("/decks/liturgy.pptx"), savedPinned())
    }

    @Test
    fun `a pin for a deck that was never opened adds nothing to recents`() {
        RecentPresentationFiles.add("/decks/ordinary.pptx")
        RecentPresentationFiles.togglePin("/decks/never-opened.pptx")

        RecentPresentationFiles.clear()

        assertTrue(recents.isEmpty(), "clearing keeps pinned recents, not every pin")
    }

    @Test
    fun `clearing an already empty list is harmless`() {
        RecentPresentationFiles.clear()

        assertTrue(recents.isEmpty())
    }

    // ── Coming back after a restart ─────────────────────────────────────────────

    /**
     * Re-runs the initialiser's own load. The object reads both files once, when the class is first
     * loaded by the JVM, so a restart cannot be staged any other way — and the load path is the half
     * of this feature that the saving tests above never touch.
     */
    private fun reload() {
        RecentPresentationFiles::class.java
            .getDeclaredMethod("load")
            .apply { isAccessible = true }
            .invoke(RecentPresentationFiles)
    }

    @Test
    fun `the recent list comes back in order`() {
        RecentPresentationFiles.add("/decks/first.pptx")
        RecentPresentationFiles.add("/decks/second.pptx")
        RecentPresentationFiles.files.clear()

        reload()

        assertEquals(
            listOf("/decks/second.pptx", "/decks/first.pptx"),
            recents,
            "newest first, or the operator's most likely deck is no longer at the top",
        )
    }

    @Test
    fun `pins come back too`() {
        RecentPresentationFiles.togglePin("/decks/liturgy.pptx")
        RecentPresentationFiles.pinned.clear()

        reload()

        assertEquals(listOf("/decks/liturgy.pptx"), pinned, "a pin that lasts one session is not a pin")
    }

    @Test
    fun `a list saved by a build with a bigger cap is trimmed on the way in`() {
        recentsFile.parentFile?.mkdirs()
        recentsFile.writeText(Json.encodeToString(ListSerializer(String.serializer()), (1..25).map { "/decks/deck$it.pptx" }))

        reload()

        assertEquals(10, recents.size, "the picker shows a short list; twenty-five rows would fill the dialog")
        assertEquals("/decks/deck1.pptx", recents.first(), "the newest end is the end that is kept")
    }

    @Test
    fun `an unreadable recents file leaves the list empty rather than failing the launch`() {
        recentsFile.parentFile?.mkdirs()
        recentsFile.writeText("{ not a list at all")
        pinnedFile.writeText(Json.encodeToString(ListSerializer(String.serializer()), listOf("/decks/liturgy.pptx")))

        reload()

        assertTrue(recents.isEmpty())
        assertEquals(listOf("/decks/liturgy.pptx"), pinned, "one bad file must not take the other down with it")
    }

    @Test
    fun `a first run with no files at all starts empty`() {
        recentsFile.delete()
        pinnedFile.delete()

        reload()

        assertTrue(recents.isEmpty())
        assertTrue(pinned.isEmpty())
    }

    // ── Bad files ───────────────────────────────────────────────────────────────

    @Test
    fun `a deck opened after a corrupt file repairs it`() {
        recentsFile.parentFile?.mkdirs()
        recentsFile.writeText("not json")

        RecentPresentationFiles.add("/decks/a.pptx")

        assertEquals(listOf("/decks/a.pptx"), savedRecents())
    }

    @Test
    fun `a path with spaces and non-ascii characters round-trips`() {
        val path = "/decks/Пасха 2026 — служение.pptx"

        RecentPresentationFiles.add(path)

        assertEquals(listOf(path), savedRecents())
        assertFalse(recentsFile.readText().contains("?"), "a mangled path would open nothing")
    }
}
