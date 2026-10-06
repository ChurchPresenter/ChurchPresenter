package org.churchpresenter.detektrules

import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class HardcodedStringTest {

    private fun findings(code: String, rule: HardcodedString = HardcodedString()) = rule.lint(code)

    @Test
    fun `a literal in a function call is flagged`() {
        val found = findings("""fun f() { println("Hello") }""")
        assertEquals(1, found.size)
        assertEquals("HardcodedString", found.single().id)
    }

    @Test
    fun `a literal in a plain val is flagged`() {
        assertEquals(1, findings("""val greeting = "Hello"""").size)
    }

    @Test
    fun `a raw string is flagged`() {
        assertEquals(1, findings("val sql = \"\"\"SELECT 1\"\"\"").size)
    }

    @Test
    fun `a template with text of its own is flagged`() {
        assertEquals(1, findings("""fun f(n: Int) = "${'$'}n items"""").size)
    }

    @Test
    fun `an escape is text of its own`() {
        assertEquals(1, findings("""fun f(a: String) = "${'$'}a\n"""").size)
    }

    @Test
    fun `a const val initializer is allowed`() {
        assertEquals(0, findings("""const val KEY = "primary_bible"""").size)
    }

    @Test
    fun `a const val in an object is allowed`() {
        assertEquals(0, findings("""object Keys { const val KEY = "k" + "ey" }""").size)
    }

    @Test
    fun `an annotation argument is allowed`() {
        assertEquals(0, findings("""@Suppress("MagicNumber") fun f() = Unit""").size)
    }

    @Test
    fun `a file annotation is allowed`() {
        assertEquals(0, findings("@file:JvmName(\"Names\")\npackage p").size)
    }

    @Test
    fun `a Log call is allowed`() {
        assertEquals(0, findings("""fun f(e: Exception) { Log.warn("Tag", "failed: ${'$'}{e.message}") }""").size)
    }

    @Test
    fun `a Log call allows a literal nested inside its arguments`() {
        assertEquals(0, findings("""fun f(a: Int) { Log.info("Tag", listOf("x", a).joinToString()) }""").size)
    }

    @Test
    fun `a same-named call on another receiver is flagged`() {
        assertEquals(1, findings("""fun f(o: Other) { o.info("Hello") }""").size)
    }

    @Test
    fun `a bare call named like a Log call is flagged`() {
        assertEquals(1, findings("""fun f() { info("Hello") }""").size)
    }

    @Test
    fun `a call is matched only as the selector, not as the receiver`() {
        assertEquals(1, findings("""fun f() { warn("Hello").length }""").size)
    }

    @Test
    fun `allowedCalls is configurable`() {
        val rule = HardcodedString(TestConfig(ALLOWED_CALLS to listOf("require")))
        assertEquals(0, findings("""fun f(a: Int) { require(a > 0) { "a must be positive" } }""", rule).size)
        assertEquals(2, findings("""fun f() { Log.info("Tag", "message") }""", rule).size)
    }

    @Test
    fun `an empty string is not hard-coded`() {
        assertEquals(0, findings("""val empty = """"").size)
    }

    @Test
    fun `whitespace and interpolations alone are not hard-coded`() {
        assertEquals(0, findings("""fun f(a: String, b: String) = "${'$'}a ${'$'}{b}"""").size)
    }

    @Test
    fun `each literal is reported once`() {
        assertEquals(2, findings("""fun f() = listOf("a", "b")""").size)
    }

    @Test
    fun `the provider supplies the rule under the project's rule set id`() {
        val provider = ChurchPresenterRuleSetProvider()
        val ruleSet = provider.instance(TestConfig())
        assertEquals("churchpresenter", provider.ruleSetId)
        assertEquals(listOf("HardcodedString"), ruleSet.rules.map { it.ruleId })
    }
}
