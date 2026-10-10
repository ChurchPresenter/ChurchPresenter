package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals

class XmlSupportTest {

    private fun bytes(vararg mark: Int, text: String, charset: String): ByteArray =
        mark.map { it.toByte() }.toByteArray() + text.toByteArray(charset(charset))

    @Test
    fun `a UTF-16 little-endian file is decoded by its byte-order mark`() {
        val decoded = decodeXmlText(bytes(0xFF, 0xFE, text = "<song>Слава</song>", charset = "UTF-16LE"))
        assertEquals("<song>Слава</song>", decoded)
    }

    @Test
    fun `a UTF-16 big-endian file is decoded by its byte-order mark`() {
        val decoded = decodeXmlText(bytes(0xFE, 0xFF, text = "<song>Слава</song>", charset = "UTF-16BE"))
        assertEquals("<song>Слава</song>", decoded)
    }

    @Test
    fun `a UTF-8 mark is dropped and a file shorter than any mark is plain UTF-8`() {
        assertEquals("<a/>", decodeXmlText(bytes(0xEF, 0xBB, 0xBF, text = "<a/>", charset = "UTF-8")))
        assertEquals("a", decodeXmlText("a".toByteArray()))
        assertEquals("", decodeXmlText(ByteArray(0)))
    }

    @Test
    fun `breaks become new lines and nested markup and CDATA keep their text`() {
        val root = parseXmlRoot("<lyrics>one<br/>two<BR/><i>three</i><x:br/><![CDATA[four]]><!--note--></lyrics>")
        assertEquals("one\ntwo\nthree\nfour", root.textWithBreaks())
    }
}
