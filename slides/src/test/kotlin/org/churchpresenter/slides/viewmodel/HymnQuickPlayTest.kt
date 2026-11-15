package org.churchpresenter.slides.viewmodel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class HymnQuickPlayTest {
    private val root = File("hymns").absoluteFile
    private val files = listOf(
        "BookA/001 Amazing Grace.pptx", "BookA/001 Amazing Grace.pdf",
        "BookA/012 Other Hymn.pptx", "BookB/001 Different Hymn.pdf",
    ).map(root::resolve)

    @Test
    fun `number lookup respects book and preferred format without matching longer numbers`() {
        assertEquals(listOf(files[0]), HymnQuickPlay.find(files, root, "1", "BookA", "pptx"))
        assertEquals(listOf(files[1]), HymnQuickPlay.find(files, root, "001", "BookA", "pdf"))
        assertEquals(listOf(files[2]), HymnQuickPlay.find(files, root, "12", "BookA", "pptx"))
    }

    @Test
    fun `empty search lists only the default book using the preferred file format`() {
        assertEquals(listOf(files[1], files[2]), HymnQuickPlay.find(files, root, "", "BookA", "pdf"))
    }

    @Test
    fun `title lookup is case insensitive and missing preferred formats fall back`() {
        assertEquals(listOf(files[0]), HymnQuickPlay.find(files, root, "amazing", "BookA", "pptx"))
        assertEquals(listOf(files[3]), HymnQuickPlay.find(files, root, "1", "BookB", "pptx"))
        assertEquals(3, HymnQuickPlay.find(files, root, " ", "", "pdf").size)
        assertEquals(2, HymnQuickPlay.find(files, root, "1", "", "pdf").size)
    }
}
