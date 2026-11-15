package org.churchpresenter.slides.viewmodel

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HymnInitialsTest {
    @Test
    fun `Chinese hymn initials support case spaces punctuation and traditional characters`() {
        assertTrue(HymnInitials.matches("001 奇异恩典", "QYED"))
        assertTrue(HymnInitials.matches("奇異恩典（歌谱）", "q y e d"))
        assertTrue(HymnInitials.matches("635 谁是在主这边", "sszzz b"))
        assertTrue(HymnInitials.matches("赞美，救主", "zmjz"))
        assertTrue(HymnInitials.matches("重新得力", "cxdl"))
        assertTrue(HymnInitials.matches("重新得力", "zxdl"))
        assertFalse(HymnInitials.matches("奇异恩典", "zzzz"))
        assertFalse(HymnInitials.matches("奇异恩典", "123"))
    }

    @Test
    fun `initials search retains hymn book and preferred format filters`() {
        val root = File("hymns").absoluteFile
        val ppt = root.resolve("BookA/001 奇异恩典.pptx")
        val pdf = root.resolve("BookA/001 奇异恩典.pdf")
        val other = root.resolve("BookB/002 奇异恩典.pptx")
        assertEquals(listOf(pdf), HymnQuickPlay.find(listOf(ppt, pdf, other), root, "qyed", "BookA", "pdf"))
    }
}
