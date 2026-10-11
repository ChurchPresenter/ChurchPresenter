package org.churchpresenter.core.models.songs

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals

/** How a song file's bytes become text: UTF-8 with or without its mark, else Windows-1251. */
class SongEncodingTest {

    @Test
    fun `UTF-8 is read as it is`() {
        assertEquals("Слава Богу", decodeUtf8OrCyrillic("Слава Богу".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `a file that is not UTF-8 is read as Windows-1251`() {
        val ansi = "Слава Богу".toByteArray(Charset.forName("windows-1251"))
        assertEquals("Слава Богу", decodeUtf8OrCyrillic(ansi))
    }

    @Test
    fun `a file shorter than a byte order mark is read as plain UTF-8`() {
        assertEquals("A", decodeUtf8OrCyrillic(byteArrayOf(0x41)))
    }

    @Test
    fun `a byte order mark is dropped, not read as text`() {
        val withMark = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Amen".toByteArray()
        assertEquals("Amen", decodeUtf8OrCyrillic(withMark))
    }
}
