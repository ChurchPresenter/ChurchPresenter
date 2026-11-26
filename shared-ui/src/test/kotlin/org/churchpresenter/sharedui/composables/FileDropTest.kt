package org.churchpresenter.sharedui.composables

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDropTest {

    private class Files(private val files: List<File>?) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = files ?: error("refused")
    }

    @Test
    fun `takes files and nothing else`() {
        val drop = FileDrop {}
        assertTrue(drop.accepts(Files(listOf(File("a.jpg")))))
        assertFalse(drop.accepts(StringSelection("text")))
        assertFalse(drop.accepts(null))
    }

    @Test
    fun `hands the dropped files on and ends the drag`() {
        var got: List<File>? = null
        val drop = FileDrop { got = it }.apply { dragOver = true }
        assertTrue(drop.drop(Files(listOf(File("a.jpg"), File("b.png")))))
        assertEquals(listOf(File("a.jpg"), File("b.png")), got)
        assertFalse(drop.dragOver)
    }

    @Test
    fun `a drop with no files, or one that cannot be read, is refused`() {
        var got: List<File>? = null
        val drop = FileDrop { got = it }
        assertFalse(drop.drop(Files(emptyList())))
        assertFalse(drop.drop(Files(null)))
        assertFalse(drop.drop(StringSelection("text")))
        assertFalse(drop.drop(null))
        assertNull(got)
    }
}
