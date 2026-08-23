package org.churchpresenter.dictionary.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `buildStrongsAnnotatedString` — the pass that turns the `H1234`/`G5678` references inside a
 * Strong's definition into the links the operator clicks to follow a word.
 *
 * The definitions come from the dictionary data as plain prose with references embedded in it, and
 * this walks them with a regex, appending the text between matches by index. **The arithmetic is the
 * risk.** A wrong `lastEnd` does not throw and does not look wrong in a screenshot — it silently
 * duplicates or drops words in the middle of a definition somebody is reading aloud. So the first
 * thing asserted throughout is that the flattened text still equals the input exactly.
 *
 * Tested directly rather than through `DictionaryTab`: it is a pure function over a string, and
 * driving it through the composable would assert on a rendered tree instead of on the ranges it
 * actually produces.
 */
class DictionaryStrongsLinksTest {

    // The two link colours are now the theme's, passed in by the caller; these stand in for them so
    // the test pins which reference gets which, not what either one is.
    private val hebrew = Color(0xFFB45309)
    private val greek = Color(0xFF1D4ED8)

    private fun build(text: String, onClick: (String) -> Unit = {}): AnnotatedString =
        buildStrongsAnnotatedString(text, hebrew, greek, onClick)

    private fun AnnotatedString.links(): List<AnnotatedString.Range<LinkAnnotation>> =
        getLinkAnnotations(0, length)

    private fun AnnotatedString.tags(): List<String> =
        links().map { (it.item as LinkAnnotation.Clickable).tag }

    private fun AnnotatedString.colorOf(index: Int): Color? =
        (links()[index].item as LinkAnnotation.Clickable).styles?.style?.color

    // ── The text must survive the walk ────────────────────────────────────────

    @Test
    fun `the whole definition survives, references and all`() {
        val source = "from H1234 in the sense of G5678 covering"

        assertEquals(source, build(source).text, "no word may be dropped or repeated by the split")
    }

    @Test
    fun `text before the first reference and after the last is kept`() {
        // The two ends are what the index arithmetic gets wrong: the head is appended from the
        // previous match's end, and the tail only after the loop has finished.
        val source = "H1234 and G5678"

        val built = build(source)

        assertEquals(source, built.text)
        assertContentEquals(listOf("H1234", "G5678"), built.tags())
    }

    @Test
    fun `a definition with no references is passed through untouched`() {
        val source = "a primitive root; to cover, to atone"

        val built = build(source)

        assertEquals(source, built.text)
        assertTrue(built.links().isEmpty(), "nothing here is clickable")
    }

    @Test
    fun `an empty definition produces nothing rather than throwing`() {
        val built = build("")

        assertEquals("", built.text)
        assertTrue(built.links().isEmpty())
    }

    @Test
    fun `adjacent references keep the separator between them`() {
        val source = "H1 H2 H3"

        val built = build(source)

        assertEquals(source, built.text, "the spaces between references are text and must be kept")
        assertContentEquals(listOf("H1", "H2", "H3"), built.tags())
    }

    // ── What each link carries ────────────────────────────────────────────────

    @Test
    fun `the link is only the reference, not the words around it`() {
        val source = "from H1234 in the sense"
        val built = build(source)

        val range = built.links().single()

        assertEquals("H1234", built.text.substring(range.start, range.end))
    }

    @Test
    fun `a lowercase reference is looked up in uppercase`() {
        // The data is not consistently cased, and the tag is what the lookup is keyed on — so a
        // lowercase `h1234` left as-is would be a link that finds nothing when clicked.
        val built = build("see h1234 and g5678")

        assertContentEquals(listOf("H1234", "G5678"), built.tags())
        assertEquals("see h1234 and g5678", built.text, "only the tag is uppercased, not the text")
    }

    @Test
    fun `clicking a link reports the reference it stands for`() {
        var clicked: String? = null
        val built = build("see h1234", onClick = { clicked = it })

        val link = built.links().single().item as LinkAnnotation.Clickable
        link.linkInteractionListener?.onClick(link)

        assertEquals("H1234", clicked, "the uppercased tag is what the lookup needs")
    }

    // ── Hebrew and Greek are told apart ───────────────────────────────────────

    @Test
    fun `hebrew and greek references are coloured differently`() {
        // The colour is the only thing distinguishing the two languages inline. If the ternary were
        // inverted every reference would still be clickable and still go to the right entry, so
        // nothing else in the app would complain.
        val built = build("H1234 and G5678")

        assertEquals(hebrew, built.colorOf(0), "Hebrew is the amber one")
        assertNotEquals(built.colorOf(0), built.colorOf(1), "Greek must not share Hebrew's colour")
    }

    @Test
    fun `a lowercase hebrew reference is still coloured as hebrew`() {
        // The branch tests the *uppercased* value, so this would regress the moment someone tested
        // `match.value` instead.
        assertEquals(hebrew, build("h1234").colorOf(0))
    }
}
