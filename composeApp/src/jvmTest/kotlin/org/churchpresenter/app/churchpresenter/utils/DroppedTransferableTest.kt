package org.churchpresenter.app.churchpresenter.utils

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a drop is allowed to conclude from a [Transferable].
 *
 * Driven by a stand-in rather than a real drag: only `DragAndDropEvent.awtTransferable` needs a
 * desktop, and that one call is all that is left in the tab.
 *
 * The distinction these pin is the one that matters. Asking whether a drag *carries* files must not
 * read the data — macOS refuses the read until the drop — so a stand-in that throws on
 * `getTransferData` still has to answer the first question, which is what
 * [`a flavor check does not read the data`] holds in place.
 */
class DroppedTransferableTest {

    private val fileFlavor = DataFlavor.javaFileListFlavor

    /** A drag carrying [data] under [flavors]; `null` data throws the way a refused read does. */
    private class FakeDrag(
        private val flavors: Array<DataFlavor>,
        private val data: Any?,
    ) : Transferable {
        override fun getTransferDataFlavors() = flavors
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in flavors
        override fun getTransferData(flavor: DataFlavor): Any =
            data ?: throw UnsupportedFlavorException(flavor)
    }

    private fun dragCarrying(vararg files: File) =
        FakeDrag(arrayOf(fileFlavor), files.toList())

    @Test
    fun `a drag carrying files yields them`() {
        val song = File("/songs/Amazing Grace.mp4")
        val slide = File("/decks/sermon.pptx")

        assertEquals(listOf(song, slide), dragCarrying(song, slide).fileListOrNull())
    }

    @Test
    fun `a drag carrying no file flavor yields nothing`() {
        val text = FakeDrag(arrayOf(DataFlavor.stringFlavor), "just some text")

        assertFalse(text.carriesFileList())
        assertNull(text.fileListOrNull(), "text is not something the schedule can add")
    }

    @Test
    fun `a drag carrying an empty file list yields nothing`() {
        // Null rather than an empty list: the caller's question is "is there anything to add?".
        assertNull(dragCarrying().fileListOrNull())
    }

    @Test
    fun `a file flavor holding something that is not a list yields nothing`() {
        val odd = FakeDrag(arrayOf(fileFlavor), "not a list at all")

        assertTrue(odd.carriesFileList(), "the flavor is advertised")
        assertNull(odd.fileListOrNull(), "but what came back is not files")
    }

    @Test
    fun `a flavor check does not read the data`() {
        // The check runs mid-drag, where macOS refuses the read. Reading here is what made the
        // target's predicate answer no, so nothing highlighted and nothing could be dropped.
        val refusesReads = FakeDrag(arrayOf(fileFlavor), data = null)

        assertTrue(refusesReads.carriesFileList(), "the flavor alone must settle this")
        assertFailsWith<UnsupportedFlavorException> { refusesReads.fileListOrNull() }
    }

    @Test
    fun `the file flavor is found among the many a desktop drag carries`() {
        // Measured on macOS: a drop from Finder advertises two dozen text-uri-list flavors before
        // the file-list one, so the check cannot look at only the first.
        val uriList = DataFlavor("text/uri-list;class=java.lang.String", "uri list")
        val realistic = FakeDrag(
            flavors = Array(24) { uriList } + fileFlavor,
            data = listOf(File("/media/loop.mp4")),
        )

        assertTrue(realistic.carriesFileList())
        assertEquals(listOf(File("/media/loop.mp4")), realistic.fileListOrNull())
    }
}
