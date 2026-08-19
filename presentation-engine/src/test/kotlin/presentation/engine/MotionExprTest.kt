package presentation.engine

import presentation.engine.timeline.MotionExpr
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The expression evaluator behind PowerPoint motion animations — the little formula language
 * `<p:anim>` uses for position curves (`#ppt_x`, `0-#ppt_w/2`, `1+#ppt_h/2`).
 *
 * Precedence and the variable set are what matter: a mis-evaluated formula puts a shape in the
 * wrong place for the whole animation, which looks like a rendering bug rather than a parsing one.
 * Anything unparseable must return null so the caller can degrade the effect instead of guessing
 * a coordinate.
 */
class MotionExprTest {

    private val geometry = MotionExpr.Geometry(x = 0.5, y = 0.25, w = 0.2, h = 0.1)

    private fun eval(expression: String) = MotionExpr.evaluate(expression, geometry)

    @Test
    fun `a plain number evaluates to itself`() {
        assertEquals(0.75, eval("0.75")!!, 1e-9)
        assertEquals(3.0, eval("3")!!, 1e-9)
    }

    @Test
    fun `each geometry variable resolves to its value`() {
        assertEquals(0.5, eval("#ppt_x")!!, 1e-9)
        assertEquals(0.25, eval("#ppt_y")!!, 1e-9)
        assertEquals(0.2, eval("#ppt_w")!!, 1e-9)
        assertEquals(0.1, eval("#ppt_h")!!, 1e-9)
    }

    @Test
    fun `the leading hash is optional and the name is case-insensitive`() {
        assertEquals(0.5, eval("ppt_x")!!, 1e-9)
        assertEquals(0.5, eval("#PPT_X")!!, 1e-9)
    }

    @Test
    fun `addition and subtraction are applied left to right`() {
        assertEquals(0.75, eval("#ppt_x+0.25")!!, 1e-9)
        assertEquals(0.25, eval("#ppt_x-0.25")!!, 1e-9)
        assertEquals(1.0, eval("0.5+0.25+0.25")!!, 1e-9)
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        // 0.5 + (0.2 / 2) rather than (0.5 + 0.2) / 2 — the difference is a visibly wrong offset.
        assertEquals(0.6, eval("#ppt_x+#ppt_w/2")!!, 1e-9)
        assertEquals(0.4, eval("0-#ppt_w/2+0.5")!!, 1e-9)
    }

    @Test
    fun `parentheses override precedence`() {
        assertEquals(0.35, eval("(#ppt_x+0.2)/2")!!, 1e-9)
    }

    @Test
    fun `a leading minus negates the whole term`() {
        assertEquals(-0.5, eval("-#ppt_x")!!, 1e-9)
        assertEquals(0.5, eval("--#ppt_x")!!, 1e-9)
        assertEquals(0.5, eval("+#ppt_x")!!, 1e-9)
    }

    @Test
    fun `the common off-slide idiom evaluates to a negative offset`() {
        // `0-#ppt_w/2` is how PowerPoint parks a shape just off the left edge.
        assertEquals(-0.1, eval("0-#ppt_w/2")!!, 1e-9)
        assertEquals(1.05, eval("1+#ppt_h/2")!!, 1e-9)
    }

    @Test
    fun `whitespace around operators is ignored`() {
        assertEquals(0.75, eval("  #ppt_x  +  0.25  ")!!, 1e-9)
    }

    @Test
    fun `an unknown variable yields null rather than zero`() {
        // Zero would silently place the shape at the slide corner; null lets the caller degrade.
        assertNull(eval("#ppt_z"))
        assertNull(eval("#unknown+1"))
    }

    @Test
    fun `malformed input yields null`() {
        assertNull(eval(""))
        assertNull(eval("+"))
        assertNull(eval("0.5+"))
        assertNull(eval("(0.5"))
        assertNull(eval("0.5)"))
        assertNull(eval("0.5 0.5"), "trailing input is rejected rather than half-parsed")
    }

    @Test
    fun `a division by zero yields null rather than an infinity`() {
        // An infinite coordinate would propagate into the renderer as a NaN transform.
        assertNull(eval("#ppt_x/0"))
    }
}
