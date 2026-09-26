package org.churchpresenter.app.churchpresenter.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Hidden pictures and slides (#676): what is remembered per folder and per file, and where Next and
 * Previous land once some are passed over.
 */
class HiddenItemsStoreTest {

    private val dir: File = Files.createTempDirectory("cp-hidden-items").toFile()
    private val file = File(dir, "nested/hidden_items.json")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    // ── The store ────────────────────────────────────────────────────────────────

    @Test
    fun `nothing is hidden anywhere to begin with`() {
        val store = HiddenItemsStore(file)
        assertEquals(emptySet(), store.hiddenPictures("/pictures/Sunday"))
        assertEquals(emptySet(), store.hiddenSlides("/decks/Sunday.pptx"))
        assertFalse(file.exists(), "reading writes nothing")
    }

    @Test
    fun `hidden pictures and slides are still hidden the next time`() {
        HiddenItemsStore(file).apply {
            setHiddenPictures("/pictures/Sunday", setOf("b.jpg", "d.jpg"))
            setHiddenSlides("/decks/Sunday.pptx", setOf(0, 3))
        }

        val reopened = HiddenItemsStore(file)

        assertEquals(setOf("b.jpg", "d.jpg"), reopened.hiddenPictures("/pictures/Sunday"))
        assertEquals(setOf(0, 3), reopened.hiddenSlides("/decks/Sunday.pptx"))
    }

    @Test
    fun `each folder and each file keeps its own`() {
        val store = HiddenItemsStore(file)
        store.setHiddenPictures("/a", setOf("x.png"))
        store.setHiddenPictures("/b", setOf("y.png"))
        store.setHiddenSlides("/a.pptx", setOf(1))

        assertEquals(setOf("x.png"), store.hiddenPictures("/a"))
        assertEquals(setOf("y.png"), store.hiddenPictures("/b"))
        assertEquals(setOf(1), store.hiddenSlides("/a.pptx"))
        assertEquals(emptySet(), store.hiddenSlides("/b.pptx"))
    }

    @Test
    fun `showing everything again leaves no entry behind`() {
        val store = HiddenItemsStore(file)
        store.setHiddenPictures("/a", setOf("x.png"))
        store.setHiddenSlides("/a.pptx", setOf(1))

        store.setHiddenPictures("/a", emptySet())
        store.setHiddenSlides("/a.pptx", emptySet())

        assertFalse(file.readText().contains("/a"), file.readText())
    }

    @Test
    fun `a damaged file costs the hidden marks and nothing else`() {
        file.parentFile.mkdirs()
        file.writeText("{ not json")

        val store = HiddenItemsStore(file)
        assertEquals(emptySet(), store.hiddenPictures("/a"))

        store.setHiddenPictures("/a", setOf("x.png"))
        assertEquals(setOf("x.png"), HiddenItemsStore(file).hiddenPictures("/a"), "and it can be written again")
    }

    @Test
    fun `a file that cannot be written is not an error`() {
        // Its parent is a file, so no directory can be made there.
        val blocker = File(dir, "blocker").apply { writeText("x") }
        val store = HiddenItemsStore(File(blocker, "hidden_items.json"))

        store.setHiddenPictures("/a", setOf("x.png"))

        assertEquals(setOf("x.png"), store.hiddenPictures("/a"), "still hidden for this session")
    }

    // ── Where a move lands ───────────────────────────────────────────────────────

    @Test
    fun `a move passes over hidden items`() {
        assertEquals(3, nextVisibleIndex(from = 0, step = 1, count = 5, hidden = setOf(1, 2), wrap = false))
        assertEquals(0, nextVisibleIndex(from = 3, step = -1, count = 5, hidden = setOf(1, 2), wrap = false))
    }

    @Test
    fun `without wrapping, the end is where it stops`() {
        assertNull(nextVisibleIndex(from = 4, step = 1, count = 5, hidden = emptySet(), wrap = false))
        assertNull(nextVisibleIndex(from = 2, step = 1, count = 5, hidden = setOf(3, 4), wrap = false))
        assertNull(nextVisibleIndex(from = 0, step = -1, count = 5, hidden = emptySet(), wrap = false))
    }

    @Test
    fun `with wrapping, it carries on from the other end`() {
        assertEquals(1, nextVisibleIndex(from = 3, step = 1, count = 5, hidden = setOf(4, 0), wrap = true))
        assertEquals(3, nextVisibleIndex(from = 1, step = -1, count = 5, hidden = setOf(0, 4), wrap = true))
    }

    @Test
    fun `when everything else is hidden there is nowhere to go`() {
        assertNull(nextVisibleIndex(from = 2, step = 1, count = 4, hidden = setOf(0, 1, 3), wrap = true))
        assertNull(nextVisibleIndex(from = 0, step = 1, count = 1, hidden = emptySet(), wrap = true))
        assertNull(nextVisibleIndex(from = 0, step = 1, count = 0, hidden = emptySet(), wrap = true))
    }

    @Test
    fun `moving off a hidden item works like moving off any other`() {
        assertEquals(3, nextVisibleIndex(from = 2, step = 1, count = 5, hidden = setOf(2), wrap = false))
    }

    @Test
    fun `the first shown item, or the first of all when every one is hidden`() {
        assertEquals(0, firstVisibleIndex(count = 3, hidden = emptySet()))
        assertEquals(2, firstVisibleIndex(count = 3, hidden = setOf(0, 1)))
        assertEquals(0, firstVisibleIndex(count = 3, hidden = setOf(0, 1, 2)))
        assertEquals(0, firstVisibleIndex(count = 0, hidden = emptySet()))
    }
}
