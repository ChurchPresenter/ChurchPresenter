package org.churchpresenter.presentationengine

import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.churchpresenter.presentationengine.pptx.TimeNode
import org.churchpresenter.presentationengine.pptx.TimeNodeKind
import org.churchpresenter.presentationengine.pptx.TimingBehavior
import org.churchpresenter.presentationengine.pptx.TimingParser
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Reading PowerPoint's `<p:timing>` XML into the engine's [TimeNode] tree.
 *
 * [TimelineCompilerTest] and [TimelineCompilerCurveTest] both start from a tree built by hand, so
 * nothing checked that a real deck's XML *becomes* that tree. The units are where this goes wrong
 * quietly: rotation is in 60000ths of a degree, scale is either a percent string or thousandths of
 * a percent, keyframe times are hundred-thousandths of the duration, and repeatCount is thousandths
 * of an iteration. Read any of them raw and the animation still plays — just 60000× too far.
 */
class TimingParserBehaviorTest {

    private val temp: File = Files.createTempDirectory("timing-parser-test").toFile()

    @AfterTest
    fun cleanUp() {
        temp.deleteRecursively()
    }

    /** Wraps [behaviorXml] in one click group and returns the parsed root. */
    private fun parseClick(behaviorXml: String, effectAttrs: String = ""): TimeNode {
        val body = """
            <p:par><p:cTn id="10" fill="hold">
              <p:stCondLst><p:cond delay="indefinite"/></p:stCondLst>
              <p:childTnLst><p:par><p:cTn id="11" fill="hold">
                <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                <p:childTnLst><p:par>
                  <p:cTn id="12" presetID="10" presetClass="entr" nodeType="clickEffect" $effectAttrs>
                    <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                    <p:childTnLst>$behaviorXml</p:childTnLst>
                  </p:cTn>
                </p:par></p:childTnLst>
              </p:cTn></p:par></p:childTnLst>
            </p:cTn></p:par>
        """.trimIndent()
        return parseBody(body)
    }

    private fun parseBody(mainSeqBody: String): TimeNode {
        val file = Fixtures.createPptx(temp, listOf("Slide" to ""), name = "timing-${counter++}.pptx")
        XMLSlideShow(file.inputStream()).use { show ->
            val slide = show.slides.first()
            Fixtures.addRawTiming(slide, mainSeqBody)
            val rewritten = File(temp, "rewritten-${counter++}.pptx")
            rewritten.outputStream().use { show.write(it) }
            XMLSlideShow(rewritten.inputStream()).use { reopened ->
                return assertNotNull(TimingParser.parse(reopened.slides.first()), "timing did not parse")
            }
        }
    }

    private var counter = 0

    /** The one behavior node in a single-click tree. */
    private fun behaviorOf(root: TimeNode): TimingBehavior {
        val found = generateSequence(listOf(root)) { nodes ->
            nodes.flatMap { it.children }.takeIf { it.isNotEmpty() }
        }.flatten().firstOrNull { it.behavior != null }
        return assertNotNull(found?.behavior, "no behavior in the parsed tree")
    }

    private val shapeTarget = """<p:tgtEl><p:spTgt spid="2"/></p:tgtEl>"""

    // ── Value animation ───────────────────────────────────────────────────────

    @Test
    fun `an animate behavior carries its attribute and its from and to`() {
        val root = parseClick(
            """
            <p:anim from="0.2" to="0.75" calcmode="lin" valueType="num">
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget
                <p:attrNameLst><p:attrName>ppt_x</p:attrName></p:attrNameLst>
              </p:cBhvr>
            </p:anim>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateValue>(behaviorOf(root))
        assertEquals("ppt_x", behavior.attribute)
        assertEquals("0.2", behavior.from)
        assertEquals("0.75", behavior.to)
        assertEquals(500L, behavior.durMs)
        assertEquals(2L, behavior.target?.shapeId)
    }

    @Test
    fun `keyframe times are read as hundred-thousandths of the duration`() {
        val root = parseClick(
            """
            <p:anim calcmode="lin" valueType="num">
              <p:cBhvr><p:cTn id="20" dur="1000"/>$shapeTarget
                <p:attrNameLst><p:attrName>ppt_y</p:attrName></p:attrNameLst>
              </p:cBhvr>
              <p:tavLst>
                <p:tav tm="0"><p:val><p:strVal val="#ppt_y"/></p:val></p:tav>
                <p:tav tm="50000"><p:val><p:strVal val="0.5"/></p:val></p:tav>
                <p:tav tm="100000"><p:val><p:strVal val="0.9"/></p:val></p:tav>
              </p:tavLst>
            </p:anim>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateValue>(behaviorOf(root))
        assertEquals(3, behavior.keyframes.size)
        assertEquals(0.0, behavior.keyframes[0].first, 1e-9)
        assertEquals(0.5, behavior.keyframes[1].first, 1e-9, "tm=50000 is halfway, not 50000ms")
        assertEquals(1.0, behavior.keyframes[2].first, 1e-9)
        assertEquals("0.9", behavior.keyframes[2].second)
    }

    @Test
    fun `numeric keyframe values are read as well as string ones`() {
        val root = parseClick(
            """
            <p:anim calcmode="lin" valueType="num">
              <p:cBhvr><p:cTn id="20" dur="1000"/>$shapeTarget
                <p:attrNameLst><p:attrName>ppt_x</p:attrName></p:attrNameLst>
              </p:cBhvr>
              <p:tavLst>
                <p:tav tm="0"><p:val><p:fltVal val="0.25"/></p:val></p:tav>
                <p:tav tm="100000"><p:val><p:intVal val="1"/></p:val></p:tav>
              </p:tavLst>
            </p:anim>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateValue>(behaviorOf(root))
        assertEquals(listOf("0.25", "1"), behavior.keyframes.map { it.second })
    }

    @Test
    fun `a keyframe list containing a formula is discarded rather than half-read`() {
        // PowerPoint writes a dummy <p:val> alongside a formula for viewers that cannot evaluate
        // it. Reading that dummy as a position throws the shape off-screen, so the whole list goes.
        val root = parseClick(
            """
            <p:anim calcmode="lin" valueType="num">
              <p:cBhvr><p:cTn id="20" dur="1000"/>$shapeTarget
                <p:attrNameLst><p:attrName>ppt_x</p:attrName></p:attrNameLst>
              </p:cBhvr>
              <p:tavLst>
                <p:tav tm="0" fmla="#ppt_x+sin(2*pi*\$)*0.1"><p:val><p:strVal val="0"/></p:val></p:tav>
                <p:tav tm="100000"><p:val><p:strVal val="1"/></p:val></p:tav>
              </p:tavLst>
            </p:anim>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateValue>(behaviorOf(root))
        assertTrue(behavior.keyframes.isEmpty(), "a formula poisons the list, got ${behavior.keyframes}")
    }

    // ── Scale, rotation, motion ───────────────────────────────────────────────

    @Test
    fun `scale percentages are read from both of PowerPoint's spellings`() {
        val root = parseClick(
            """
            <p:animScale>
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
              <p:from x="25000" y="25000"/>
              <p:to x="150%" y="200%"/>
            </p:animScale>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateScale>(behaviorOf(root))
        assertEquals(0.25, behavior.fromX!!, 1e-9, "25000 thousandths of a percent is a quarter")
        assertEquals(0.25, behavior.fromY!!, 1e-9)
        assertEquals(1.5, behavior.toX!!, 1e-9, "\"150%\" is one and a half")
        assertEquals(2.0, behavior.toY!!, 1e-9)
    }

    @Test
    fun `a by-scale is read without a from`() {
        val root = parseClick(
            """
            <p:animScale>
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
              <p:by x="200000" y="50000"/>
            </p:animScale>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateScale>(behaviorOf(root))
        assertNull(behavior.fromX)
        assertEquals(2.0, behavior.byX!!, 1e-9)
        assertEquals(0.5, behavior.byY!!, 1e-9)
    }

    @Test
    fun `rotation is converted out of sixty-thousandths of a degree`() {
        val root = parseClick(
            """
            <p:animRot from="0" to="5400000" by="900000">
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
            </p:animRot>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateRotation>(behaviorOf(root))
        assertEquals(0.0, behavior.fromDeg!!, 1e-9)
        assertEquals(90.0, behavior.toDeg!!, 1e-9, "5400000/60000 is a quarter turn")
        assertEquals(15.0, behavior.byDeg!!, 1e-9)
    }

    @Test
    fun `a motion path is carried through verbatim`() {
        val root = parseClick(
            """
            <p:animMotion origin="layout" path="M 0 0 L 0.25 0.1 E" pathEditMode="relative">
              <p:cBhvr><p:cTn id="20" dur="750"/>$shapeTarget</p:cBhvr>
            </p:animMotion>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.AnimateMotion>(behaviorOf(root))
        assertEquals("M 0 0 L 0.25 0.1 E", behavior.path)
        assertEquals(750L, behavior.durMs)
    }

    @Test
    fun `a media command keeps its verb`() {
        val root = parseClick(
            """
            <p:cmd type="call" cmd="playFrom(0.0)">
              <p:cBhvr><p:cTn id="20" dur="1"/>$shapeTarget</p:cBhvr>
            </p:cmd>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.Command>(behaviorOf(root))
        assertEquals("playFrom(0.0)", behavior.verb)
    }

    @Test
    fun `a set behavior keeps the value it sets`() {
        val root = parseClick(
            """
            <p:set>
              <p:cBhvr><p:cTn id="20" dur="1" fill="hold"/>$shapeTarget
                <p:attrNameLst><p:attrName>style.visibility</p:attrName></p:attrNameLst>
              </p:cBhvr>
              <p:to><p:strVal val="visible"/></p:to>
            </p:set>
            """.trimIndent()
        )
        val behavior = assertIs<TimingBehavior.SetValue>(behaviorOf(root))
        assertEquals("style.visibility", behavior.attribute)
        assertEquals("visible", behavior.toValue)
    }

    // ── Targets ───────────────────────────────────────────────────────────────

    @Test
    fun `a paragraph range target names its paragraph`() {
        val root = parseClick(
            """
            <p:animEffect transition="in" filter="fade">
              <p:cBhvr><p:cTn id="20" dur="500"/>
                <p:tgtEl><p:spTgt spid="7"><p:txEl><p:pRg st="2" end="2"/></p:txEl></p:spTgt></p:tgtEl>
              </p:cBhvr>
            </p:animEffect>
            """.trimIndent()
        )
        val behavior = behaviorOf(root)
        assertEquals(7L, behavior.target?.shapeId)
        assertEquals(2, behavior.target?.paragraphIndex)
        assertEquals(false, behavior.target?.widensToShape)
    }

    @Test
    fun `a range covering several paragraphs widens to the whole shape`() {
        val root = parseClick(
            """
            <p:animEffect transition="in" filter="fade">
              <p:cBhvr><p:cTn id="20" dur="500"/>
                <p:tgtEl><p:spTgt spid="7"><p:txEl><p:pRg st="0" end="3"/></p:txEl></p:spTgt></p:tgtEl>
              </p:cBhvr>
            </p:animEffect>
            """.trimIndent()
        )
        assertEquals(true, behaviorOf(root).target?.widensToShape)
    }

    // ── Node attributes ───────────────────────────────────────────────────────

    @Test
    fun `repeat counts are read as thousandths of an iteration`() {
        val root = parseClick(
            """
            <p:animEffect transition="in" filter="fade">
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
            </p:animEffect>
            """.trimIndent(),
            effectAttrs = """repeatCount="3000" autoRev="1" fill="hold" restart="whenNotActive"""",
        )
        val effect = assertNotNull(
            generateSequence(listOf(root)) { nodes -> nodes.flatMap { it.children }.takeIf { it.isNotEmpty() } }
                .flatten().firstOrNull { it.presetClass == "entr" },
            "the effect node did not survive parsing",
        )
        assertEquals(3.0, effect.repeatCount!!, 1e-9, "3000 thousandths is three times through")
        assertTrue(effect.autoReverse)
        assertEquals("hold", effect.fill)
        assertEquals("whenNotActive", effect.restart)
        assertEquals(10, effect.presetId)
    }

    @Test
    fun `an indefinite repeat is marked as such rather than parsed as a number`() {
        val root = parseClick(
            """
            <p:animEffect transition="in" filter="fade">
              <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
            </p:animEffect>
            """.trimIndent(),
            effectAttrs = """repeatCount="indefinite"""",
        )
        val effect = assertNotNull(
            generateSequence(listOf(root)) { nodes -> nodes.flatMap { it.children }.takeIf { it.isNotEmpty() } }
                .flatten().firstOrNull { it.presetClass == "entr" },
        )
        assertEquals(-1.0, effect.repeatCount!!, 1e-9)
    }

    @Test
    fun `an exclusive container is parsed like the other containers`() {
        val root = parseBody(
            """
            <p:excl><p:cTn id="10" nodeType="interactiveSeq">
              <p:stCondLst><p:cond delay="indefinite" evt="onClick"><p:tgtEl><p:spTgt spid="5"/></p:tgtEl></p:cond></p:stCondLst>
              <p:childTnLst><p:par><p:cTn id="11" fill="hold">
                <p:stCondLst><p:cond delay="0"/></p:stCondLst>
                <p:childTnLst>
                  <p:animEffect transition="in" filter="fade">
                    <p:cBhvr><p:cTn id="20" dur="500"/>$shapeTarget</p:cBhvr>
                  </p:animEffect>
                </p:childTnLst>
              </p:cTn></p:par></p:childTnLst>
            </p:cTn></p:excl>
            """.trimIndent()
        )
        val excl = assertNotNull(
            generateSequence(listOf(root)) { nodes -> nodes.flatMap { it.children }.takeIf { it.isNotEmpty() } }
                .flatten().firstOrNull { it.kind == TimeNodeKind.EXCL },
            "the exclusive container was dropped",
        )
        val condition = excl.beginConditions.single()
        assertEquals(TimeNode.INDEFINITE_MS, condition.delayMs, "an interactive sequence waits for its trigger")
        assertEquals("onClick", condition.event)
        assertEquals(5L, condition.triggerShapeId, "the shape that has to be clicked")
    }

    @Test
    fun `a slide with no timing at all parses to nothing`() {
        val file = Fixtures.createPptx(temp, listOf("Untimed" to ""), name = "untimed.pptx")
        XMLSlideShow(file.inputStream()).use { show ->
            assertNull(TimingParser.parse(show.slides.first()))
        }
    }
}
