package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The near-JSON VideoPsalm writes. Every case here is something a real parser rejects outright.
 */
class LooseJsonTest {

    @Test
    fun `keys are unquoted`() {
        assertEquals("Мой Сборник", LooseJson.parse("""{Text:"Мой Сборник"}""").text("Text"))
    }

    @Test
    fun `a byte-order mark opens the file`() {
        assertEquals("Book", LooseJson.parse("﻿{Text:\"Book\"}").text("Text"))
    }

    @Test
    fun `a real line break inside a string is part of the string`() {
        val node = LooseJson.parse("{\nText:\"first\nsecond\"}")
        assertEquals("first\nsecond", node.text("Text"))
    }

    @Test
    fun `an escaped quote does not end the string`() {
        assertEquals("""say "yes"""", LooseJson.parse("""{Text:"say \"yes\""}""").text("Text"))
    }

    @Test
    fun `the escapes a string can carry are decoded`() {
        val node = LooseJson.parse("{A:\"a\\nb\",B:\"c\\\\d\",C:\"e\\tf\",D:\"\\u0041\",E:\"\\q\"}")
        assertEquals("a\nb", node.text("A"))
        assertEquals("""c\d""", node.text("B"))
        assertEquals("e\tf", node.text("C"))
        assertEquals("A", node.text("D"))
        assertEquals("q", node.text("E"))
    }

    @Test
    fun `a broken unicode escape is kept rather than swallowing the rest of the string`() {
        assertEquals("uzzzz!", LooseJson.parse("""{A:"\uzzzz!"}""").text("A"))
    }

    @Test
    fun `numbers and keywords are read as the text they were written as`() {
        val node = LooseJson.parse("{ID:12,Flag:true,Missing:null,Ratio:1.6}")
        assertEquals("12", node.text("ID"))
        assertEquals("true", node.text("Flag"))
        assertEquals("null", node.text("Missing"))
        assertEquals("1.6", node.text("Ratio"))
    }

    @Test
    fun `children reads the objects of an array and nothing else`() {
        val node = LooseJson.parse("""{Songs:[{ID:1},{ID:2}],Messages:["a","b"]}""")
        assertEquals(listOf("1", "2"), node.children("Songs").map { it.text("ID") })
        assertEquals(emptyList(), node.children("Messages"))
        assertEquals(emptyList(), node.children("Absent"))
    }

    @Test
    fun `a nested object is not readable as text, and a missing field is empty`() {
        val node = LooseJson.parse("""{Style:{Background:{Image:"a.jpg"}}}""")
        assertEquals("", node.text("Style"))
        assertEquals("", node.text("Absent"))
    }

    @Test
    fun `trailing commas and stray whitespace are skipped`() {
        val node = LooseJson.parse("{ ID:1 , Songs:[ {ID:2}, ] , }")
        assertEquals("1", node.text("ID"))
        assertEquals(listOf("2"), node.children("Songs").map { it.text("ID") })
    }

    @Test
    fun `a file that stops mid-song gives up the songs it did parse`() {
        val node = LooseJson.parse("""{Songs:[{ID:1,Text:"Done"},{ID:2,Text:"Cut off""")
        assertEquals(listOf("Done", "Cut off"), node.children("Songs").map { it.text("Text") })
    }

    @Test
    fun `text that is not an object at all parses to nothing rather than throwing`() {
        assertEquals("", LooseJson.parse("").text("Text"))
        assertEquals("", LooseJson.parse("not json").text("Text"))
    }
}
