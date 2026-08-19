package presentation.engine.timeline

/**
 * Evaluator for PowerPoint animation value expressions — the formulas `<p:anim>` uses for
 * position curves, e.g. `#ppt_x`, `0-#ppt_w/2`, `#ppt_x+0.25`, `1+#ppt_h/2`.
 *
 * Variables are the target shape's geometry in normalized slide space:
 * `ppt_x`/`ppt_y` = center position, `ppt_w`/`ppt_h` = size. Returns null for anything it
 * cannot parse — callers degrade the effect rather than guess.
 */
internal object MotionExpr {

    data class Geometry(val x: Double, val y: Double, val w: Double, val h: Double)

    fun evaluate(expression: String, geometry: Geometry): Double? {
        return try {
            Parser(expression.trim(), geometry).parseExpression().also { result ->
                if (result.isNaN() || result.isInfinite()) return null
            }
        } catch (_: Exception) {
            null
        }
    }

    private class Parser(private val text: String, private val geometry: Geometry) {
        private var pos = 0

        fun parseExpression(): Double {
            val value = parseAdditive()
            skipWhitespace()
            require(pos == text.length) { "trailing input at $pos" }
            return value
        }

        private fun parseAdditive(): Double {
            var value = parseMultiplicative()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '+' -> { pos++; value += parseMultiplicative() }
                    '-' -> { pos++; value -= parseMultiplicative() }
                    else -> return value
                }
            }
        }

        private fun parseMultiplicative(): Double {
            var value = parseUnary()
            while (true) {
                skipWhitespace()
                when (peek()) {
                    '*' -> { pos++; value *= parseUnary() }
                    '/' -> { pos++; value /= parseUnary() }
                    else -> return value
                }
            }
        }

        private fun parseUnary(): Double {
            skipWhitespace()
            if (peek() == '-') { pos++; return -parseUnary() }
            if (peek() == '+') { pos++; return parseUnary() }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            skipWhitespace()
            return when {
                peek() == '(' -> {
                    pos++
                    val value = parseAdditive()
                    skipWhitespace()
                    require(peek() == ')') { "expected )" }
                    pos++
                    value
                }
                peek() == '#' || peek()?.isLetter() == true -> parseVariable()
                else -> parseNumber()
            }
        }

        private fun parseVariable(): Double {
            if (peek() == '#') pos++
            val start = pos
            while (pos < text.length && (text[pos].isLetterOrDigit() || text[pos] == '_')) pos++
            return when (text.substring(start, pos).lowercase()) {
                "ppt_x" -> geometry.x
                "ppt_y" -> geometry.y
                "ppt_w" -> geometry.w
                "ppt_h" -> geometry.h
                else -> throw IllegalArgumentException("unknown variable")
            }
        }

        private fun parseNumber(): Double {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '.')) pos++
            require(pos > start) { "expected number at $start" }
            return text.substring(start, pos).toDouble()
        }

        private fun peek(): Char? = text.getOrNull(pos)

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }
    }
}
