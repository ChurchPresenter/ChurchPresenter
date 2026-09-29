package org.churchpresenter.lintrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardcodedColorTest {

    private fun findings(code: String, config: Config = Config.empty) = HardcodedColor(config).lint(code)

    @Test
    fun `flags a hex color literal`() {
        val result = findings("""val c = Color(0xFF123456)""")
        assertEquals(1, result.size)
        assertTrue("Color(0xFF123456)" in result.single().message)
    }

    @Test
    fun `flags a color built from literal components`() {
        val code = """
            val a = Color(0.2f, 0.4f, 0.6f)
            val b = Color(red = 1f, green = 0f, blue = 0f, alpha = 1f)
        """.trimIndent()
        assertEquals(2, findings(code).size)
    }

    @Test
    fun `flags the named colors`() {
        val code = """
            fun f() {
                Text("x", color = Color.White)
                Box(Modifier.background(Color.Black))
                val r = Color.Red
            }
        """.trimIndent()
        assertEquals(3, findings(code).size)
    }

    @Test
    fun `passes theme colors`() {
        val code = """
            fun f() {
                Text("x", color = MaterialTheme.colorScheme.onSurface)
                Box(Modifier.background(LocalSemanticColors.current.success))
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes Transparent and Unspecified`() {
        assertTrue(findings("""val a = Color.Transparent; val b = Color.Unspecified""").isEmpty())
    }

    @Test
    fun `passes colors computed at run time`() {
        val code = """
            fun f(argb: Long, hex: String, c: Color) {
                val a = Color(argb)
                val b = Color(parseHex(hex))
                val d = c.copy(alpha = 0.5f)
                val e = Color(0xFF000000 or argb)
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes other calls and members`() {
        val code = """
            val a = Other(0xFF123456)
            val b = Other.White
            val c = Color.hsv(1f, 1f, 1f)
            val d = Color()
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `reads its names from config`() {
        val config = TestConfig("colorClass" to "Paint", "namedColors" to listOf("Red"))
        val code = """
            val a = Paint(0xFF123456)
            val b = Paint.Red
            val c = Paint.White
            val d = Color(0xFF123456)
        """.trimIndent()
        assertEquals(2, findings(code, config).size)
    }
}
