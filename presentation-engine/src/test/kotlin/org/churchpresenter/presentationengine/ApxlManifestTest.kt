package org.churchpresenter.presentationengine

import org.churchpresenter.presentationengine.keynote.ApxlManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApxlManifestTest {

    private fun document(vararg slides: String) = """
        <key:presentation xmlns:key="http://developer.apple.com/namespaces/keynote2"
                          xmlns:sf="http://developer.apple.com/namespaces/sf">
          <key:slide-list>${slides.joinToString("")}</key:slide-list>
        </key:presentation>
    """.trimIndent()

    @Test
    fun `each slide's own thumbnail is found and slides without one are skipped`() {
        val xml = document(
            """<key:slide>
                 <key:page><sf:data sf:path="photo.jpg"/></key:page>
                 <key:thumbnails><key:binary>
                   <sf:data sf:path=" "/><sf:wrap><sf:data sf:path="thumbs/st0.jpg"/></sf:wrap>
                 </key:binary></key:thumbnails>
               </key:slide>""",
            """<key:slide><key:page/></key:slide>""",
            """<key:slide><key:thumbnails><sf:data/></key:thumbnails></key:slide>""",
            """<key:slide><key:thumbnails><key:binary>
                 <sf:data sf:path="thumbs/st2.jpg"/>
               </key:binary></key:thumbnails></key:slide>""",
        )
        assertEquals(listOf("thumbs/st0.jpg", "thumbs/st2.jpg"), ApxlManifest.parseThumbnails(xml))
    }

    @Test
    fun `notes are joined word runs from every notes child, one entry per slide`() {
        val xml = document(
            """<key:slide>
                 <key:notes><sf:p>  Welcome   </sf:p><sf:p>
                 </sf:p><sf:p>everyone</sf:p></key:notes>
                 <key:page><sf:p>not a note</sf:p></key:page>
                 <key:notes>Pray</key:notes>
               </key:slide>""",
            """<key:slide><key:page/></key:slide>""",
        )
        assertEquals(listOf("Welcome everyone Pray", ""), ApxlManifest.parseNotes(xml))
    }

    @Test
    fun `malformed xml yields nothing rather than throwing`() {
        assertTrue(ApxlManifest.parseThumbnails("<key:slide>").isEmpty())
        assertTrue(ApxlManifest.parseNotes("not xml at all").isEmpty())
    }
}
