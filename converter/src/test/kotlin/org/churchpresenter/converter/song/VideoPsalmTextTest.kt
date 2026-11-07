package org.churchpresenter.converter.song

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoPsalmTextTest {

    private val yoruba = "Yoruba_ITC Officina Serif"

    @Test
    fun `every inline tag is taken out and its text kept`() {
        val text = "<cFF00F200>Ògo</c> <s1384>11</s> <kFFFFFF00>a</k> <b>b</b><i>c</i><u>d</u> <fTahoma>e</f>"
        assertEquals("Ògo 11 a bcd e", VideoPsalmText.plain(text))
    }

    @Test
    fun `an end marker made of angle brackets is not mistaken for a tag`() {
        assertEquals("<><><>", VideoPsalmText.plain("<><><>"))
        assertEquals("a < b > c", VideoPsalmText.plain("a < b > c"))
    }

    @Test
    fun `only the text inside a legacy font run is decoded`() {
        val text = "[B] <f$yoruba>Iw] ni ipin mi, SUNM} }</f> [B]"
        assertEquals("[B] Iwọ ni ipin mi, SUNMỌ Ọ [B]", VideoPsalmText.plain(text))
    }

    @Test
    fun `an ordinary font inside a legacy run keeps its brackets`() {
        val text = "<f$yoruba>J[ ki nle sunm</f><fEbrima>ọ [x]</f><f$yoruba> l]</f>"
        assertEquals("Jẹ ki nle sunmọ [x] lọ", VideoPsalmText.plain(text, bodyFont = yoruba))
    }

    @Test
    fun `text in a legacy body font is decoded, and an ordinary run inside it is not`() {
        assertEquals("Ẹ yin Ọba [Ebrima]", VideoPsalmText.plain("{ yin }ba <fEbrima>[Ebrima]</f>", yoruba))
    }

    @Test
    fun `a song typed for a legacy font reads as it under an ordinary body font`() {
        assertEquals("Ẹnikẹni t'iwọ", VideoPsalmText.plain("{nik[ni t'iw]", "Tahoma", typedFor = "Yor_Arial"))
        assertEquals("Ẹnikẹni", VideoPsalmText.plain("{nik[ni", "", typedFor = "Yor_Arial"))
    }

    @Test
    fun `a stray closing font tag does not lose the body font`() {
        assertEquals("ọ ọ", VideoPsalmText.plain("]</f> ]", yoruba))
    }

    @Test
    fun `decomposed letters are composed and a form feed is a line break`() {
        assertEquals("ọ ẹ\nṣ", VideoPsalmText.plain("ọ ẹ\u000cṣ"))
        // ọ with a tone mark has no single code point, so the mark stays after the composed letter.
        assertEquals("ọ̀", VideoPsalmText.plain("ọ̀"))
    }

    @Test
    fun `a body font is read from a style`() {
        val node = LooseJson.parse("""{Style:{Body:{FontName:" Yor_Arial ",FontSize:300}}}""")
        assertEquals("Yor_Arial", VideoPsalmText.bodyFontOf(node))
        assertEquals("", VideoPsalmText.bodyFontOf(LooseJson.parse("""{Style:{Header:{}}}""")))
        assertEquals("", VideoPsalmText.bodyFontOf(LooseJson.parse("{}")))
    }

    @Test
    fun `a book's legacy font is found in a style or inline, and ordinary fonts are passed over`() {
        assertEquals("Yor_Arial", VideoPsalmText.legacyFontOf("""FontName:"Tahoma" x <fYor_Arial>y</f>"""))
        assertEquals(yoruba, VideoPsalmText.legacyFontOf("""Body:{FontName:"$yoruba"}"""))
        assertEquals("", VideoPsalmText.legacyFontOf("""FontName:"Arial" <fEbrima>x</f>"""))
    }

    @Test
    fun `a song is typed for the legacy font only when its own words use the stand-ins`() {
        val yorubaSong = LooseJson.parse("""{Verses:[{Text:"Mo y] s[s["}],Text:"Title"}""")
        val englishSong = LooseJson.parse("""{Verses:[{Text:"What a Friend"}],Text:"[B] Hymn"}""")
        assertEquals("Yor_Arial", VideoPsalmText.typedFor(yorubaSong, "Yor_Arial"))
        assertEquals("", VideoPsalmText.typedFor(yorubaSong, ""))
        assertEquals("", VideoPsalmText.typedFor(LooseJson.parse("""{Text:"a [ b"}"""), "Yor_Arial"))
        // "[B]" touches a letter, so an English book that names a legacy font would decode it: the
        // book has to name one before any of its songs is read this way.
        assertEquals("Yor_Arial", VideoPsalmText.typedFor(englishSong, "Yor_Arial"))
    }
}
