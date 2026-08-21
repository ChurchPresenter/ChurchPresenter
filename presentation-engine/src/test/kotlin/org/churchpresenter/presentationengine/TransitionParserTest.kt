package org.churchpresenter.presentationengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import org.churchpresenter.presentationengine.model.Direction
import org.churchpresenter.presentationengine.model.SlideTransitionSpec
import org.churchpresenter.presentationengine.model.TransitionType
import org.churchpresenter.presentationengine.pptx.TransitionParser
import java.io.File
import java.nio.file.Files
import javax.xml.namespace.QName
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Reading `<p:transition>` off a PowerPoint slide.
 *
 * Fixtures are built with POI rather than checked in, so every case is legible in the test. Two
 * behaviours the parser's own comments record as validated against real decks are pinned here,
 * because both are silent when wrong:
 *
 * - **`p14:dur` wins over `spd`.** The legacy `spd` attribute quantizes everything to three
 *   buckets, so a deck whose transitions really run 1200–2900ms reports a flat 1000ms if `spd` is
 *   trusted. PowerPoint writes both.
 * - **An unrecognised kind degrades to a fade**, never to nothing — timing and content survive
 *   even when the compositor has no faithful implementation of that effect.
 */
class TransitionParserTest {

    private val temp: File = Files.createTempDirectory("transition-test").toFile()
    private val PML = "http://schemas.openxmlformats.org/presentationml/2006/main"
    private val P14 = "http://schemas.microsoft.com/office/powerpoint/2010/main"

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /**
     * A slide carrying one `<p:transition>` with the given kind and attributes, written through
     * POI and read back from disk so the XML round-trips exactly as a real deck would.
     */
    private fun slideWithTransition(
        kind: String?,
        dir: String? = null,
        orient: String? = null,
        spd: String? = null,
        advTmMs: Long? = null,
        p14DurMs: Long? = null,
    ): XSLFSlide {
        val file = File(temp, "deck-${System.nanoTime()}.pptx")
        XMLSlideShow().use { ppt ->
            val slide = ppt.createSlide()
            val transition = slide.xmlObject.addNewTransition()
            val cursor = transition.newCursor()
            cursor.toFirstContentToken()
            if (spd != null) cursor.insertAttributeWithValue("spd", spd)
            if (advTmMs != null) cursor.insertAttributeWithValue("advTm", advTmMs.toString())
            if (p14DurMs != null) cursor.insertAttributeWithValue(QName(P14, "dur", "p14"), p14DurMs.toString())
            if (kind != null) {
                cursor.beginElement(QName(PML, kind, "p"))
                if (dir != null) cursor.insertAttributeWithValue("dir", dir)
                if (orient != null) cursor.insertAttributeWithValue("orient", orient)
            }
            cursor.dispose()
            file.outputStream().use { ppt.write(it) }
        }
        // Re-open from disk: the parser reaches the transition the same way it would in the wild.
        return XMLSlideShow(file.inputStream()).slides.first()
    }

    private fun parse(
        kind: String?,
        dir: String? = null,
        orient: String? = null,
        spd: String? = null,
        advTmMs: Long? = null,
        p14DurMs: Long? = null,
    ): SlideTransitionSpec? = TransitionParser.parse(slideWithTransition(kind, dir, orient, spd, advTmMs, p14DurMs))

    // ── No transition ─────────────────────────────────────────────────────────

    @Test
    fun `a slide with no transition reports none`() {
        val file = File(temp, "plain.pptx")
        XMLSlideShow().use { ppt ->
            ppt.createSlide()
            file.outputStream().use { ppt.write(it) }
        }
        assertNull(TransitionParser.parse(XMLSlideShow(file.inputStream()).slides.first()))
    }

    // ── Kinds ─────────────────────────────────────────────────────────────────

    @Test
    fun `a cut is no visible transition at all`() {
        assertEquals(TransitionType.NONE, assertNotNull(parse("cut")).type)
    }

    @Test
    fun `fade and randomBar both read as a fade`() {
        assertEquals(TransitionType.FADE, assertNotNull(parse("fade")).type)
        assertEquals(TransitionType.FADE, assertNotNull(parse("randomBar")).type)
    }

    @Test
    fun `push and wipe keep their own kinds`() {
        assertEquals(TransitionType.PUSH, assertNotNull(parse("push", dir = "l")).type)
        assertEquals(TransitionType.WIPE, assertNotNull(parse("wipe", dir = "d")).type)
    }

    @Test
    fun `strips, blinds and comb are all wipes`() {
        assertEquals(TransitionType.WIPE, assertNotNull(parse("strips", dir = "lu")).type)
        assertEquals(TransitionType.WIPE, assertNotNull(parse("blinds", dir = "horz")).type)
        assertEquals(TransitionType.WIPE, assertNotNull(parse("comb", dir = "vert")).type)
    }

    @Test
    fun `cover and pull are both covers`() {
        assertEquals(TransitionType.COVER, assertNotNull(parse("cover", dir = "r")).type)
        assertEquals(TransitionType.COVER, assertNotNull(parse("pull", dir = "l")).type)
    }

    @Test
    fun `a split reads its orientation`() {
        val vertical = assertNotNull(parse("split", orient = "vert"))
        assertEquals(TransitionType.SPLIT, vertical.type)
        assertEquals(Direction.LEFT, vertical.direction)

        val horizontal = assertNotNull(parse("split", orient = "horz"))
        assertEquals(Direction.UP, horizontal.direction)
    }

    @Test
    fun `an effect with no faithful implementation degrades to a fade rather than to nothing`() {
        // Content and timing must survive even when the compositor cannot reproduce the look.
        for (kind in listOf("dissolve", "checker", "wheel", "zoom", "newsflash", "prism")) {
            val spec = assertNotNull(parse(kind, spd = "med"), "$kind still parses")
            assertEquals(TransitionType.FADE, spec.type, "$kind degrades to a fade")
            assertEquals(750L, spec.durationMs, "$kind keeps its timing")
        }
    }

    // ── Directions ────────────────────────────────────────────────────────────

    @Test
    fun `side directions decode to the way the incoming slide moves`() {
        assertEquals(Direction.LEFT, assertNotNull(parse("push", dir = "l")).direction)
        assertEquals(Direction.RIGHT, assertNotNull(parse("push", dir = "r")).direction)
        assertEquals(Direction.UP, assertNotNull(parse("push", dir = "u")).direction)
        assertEquals(Direction.DOWN, assertNotNull(parse("push", dir = "d")).direction)
    }

    @Test
    fun `a missing direction still yields one`() {
        assertEquals(Direction.LEFT, assertNotNull(parse("push")).direction)
    }

    @Test
    fun `corner directions collapse onto their dominant side`() {
        assertEquals(Direction.LEFT, assertNotNull(parse("cover", dir = "lu")).direction)
        assertEquals(Direction.LEFT, assertNotNull(parse("cover", dir = "ld")).direction)
        assertEquals(Direction.RIGHT, assertNotNull(parse("cover", dir = "ru")).direction)
        assertEquals(Direction.RIGHT, assertNotNull(parse("cover", dir = "rd")).direction)
        assertEquals(Direction.UP, assertNotNull(parse("cover", dir = "u")).direction)
        assertEquals(Direction.DOWN, assertNotNull(parse("cover", dir = "d")).direction)
    }

    @Test
    fun `blinds and comb read their orientation as an axis`() {
        assertEquals(Direction.RIGHT, assertNotNull(parse("blinds", dir = "vert")).direction)
        assertEquals(Direction.DOWN, assertNotNull(parse("blinds", dir = "horz")).direction)
        assertEquals(Direction.RIGHT, assertNotNull(parse("comb", dir = "vert")).direction)
    }

    // ── Duration ──────────────────────────────────────────────────────────────

    @Test
    fun `the legacy speed attribute maps to its three buckets`() {
        assertEquals(1000L, assertNotNull(parse("fade", spd = "slow")).durationMs)
        assertEquals(750L, assertNotNull(parse("fade", spd = "med")).durationMs)
        assertEquals(500L, assertNotNull(parse("fade", spd = "fast")).durationMs)
    }

    @Test
    fun `a transition with no speed at all gets the fast default`() {
        assertEquals(500L, assertNotNull(parse("fade")).durationMs)
    }

    @Test
    fun `the precise extension duration wins over the legacy bucket`() {
        // A real deck carried spd="slow" on every transitioned slide while its actual durations
        // ranged 1200-2900ms; trusting spd flattens all of them to 1000ms.
        val spec = assertNotNull(parse("fade", spd = "slow", p14DurMs = 2500))
        assertEquals(2500L, spec.durationMs)
    }

    @Test
    fun `the extension duration is used even when no legacy speed is present`() {
        assertEquals(1200L, assertNotNull(parse("push", dir = "l", p14DurMs = 1200)).durationMs)
    }

    // ── Auto-advance ──────────────────────────────────────────────────────────

    @Test
    fun `an auto-advance delay is carried through`() {
        assertEquals(5000L, assertNotNull(parse("fade", advTmMs = 5000)).advanceAfterMs)
    }

    @Test
    fun `a click-advanced slide reports no delay`() {
        assertNull(assertNotNull(parse("fade")).advanceAfterMs, "null means wait for the operator")
    }

    @Test
    fun `the advance delay is independent of the transition duration`() {
        val spec = assertNotNull(parse("push", dir = "r", spd = "med", advTmMs = 8000))
        assertEquals(750L, spec.durationMs, "how long the effect takes")
        assertEquals(8000L, spec.advanceAfterMs, "how long the slide stays up")
    }
}
