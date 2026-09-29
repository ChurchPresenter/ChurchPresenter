package org.churchpresenter.lintrules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.TestConfig
import io.gitlab.arturbosch.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HardcodedUiStringTest {

    private fun findings(code: String, config: Config = Config.empty) = HardcodedUiString(config).lint(code)

    @Test
    fun `flags a literal as the first argument of Text`() {
        val result = findings("""fun f() { Text("Hello") }""")
        assertEquals(1, result.size)
        assertTrue("\"Hello\"" in result.single().message)
    }

    @Test
    fun `flags a literal passed as a named text argument`() {
        val code = """
            fun f() {
                Text(text = "Hello")
                Chip(label = "Pick me")
                Icon(contentDescription = "Close")
                Window(title = "Editor")
            }
        """.trimIndent()
        assertEquals(4, findings(code).size)
    }

    @Test
    fun `flags literal words around an interpolation`() {
        assertEquals(1, findings("""fun f(n: Int) { Text("+${'$'}n more") }""").size)
    }

    @Test
    fun `passes string resources and constants`() {
        val code = """
            const val SAMPLE = "Aa"
            fun f() {
                Text(stringResource(Res.string.hello))
                Text(text = SAMPLE)
                Chip(label = stringResource(Res.string.pick))
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes literals with no letters outside interpolations and escapes`() {
        val code = """
            fun f(a: String, b: Int) {
                Text("${'$'}{a}: ${'$'}b")
                Text("${'$'}b%")
                Text("▲ ${'$'}b")
                Text(text = "${'$'}{b + 1}/${'$'}b")
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes a debug label on an animation call`() {
        val code = """
            fun f() {
                animateFloatAsState(1f, label = "alpha")
                rememberInfiniteTransition(label = "pulse")
                updateTransition(true, label = "state")
                Crossfade(0, label = "fade")
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes a qualified Text such as a websocket frame`() {
        assertTrue(findings("""fun f() { send(Frame.Text("{\"type\":\"pong\"}")) }""").isEmpty())
    }

    @Test
    fun `passes positional arguments that are not the text`() {
        val code = """
            fun f(m: Modifier) {
                Text(m, "Hello")
                Chip("Pick me")
                log("Something happened")
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes named arguments that are not text`() {
        assertTrue(findings("""fun f() { load(key = "songs", path = "a/b") }""").isEmpty())
    }

    @Test
    fun `passes an annotation argument inside a text call`() {
        val code = """
            fun f() {
                Box {
                    @Deprecated(message = "Old")
                    fun g() = Unit
                }
            }
        """.trimIndent()
        assertTrue(findings(code).isEmpty())
    }

    @Test
    fun `passes literals outside call arguments`() {
        assertTrue(findings("""val title = "Crossword"""").isEmpty())
    }

    @Test
    fun `reads its lists from config`() {
        val config = TestConfig(
            "textCalls" to listOf("Label"),
            "textArguments" to listOf("caption"),
            "ignoredCalls" to "Debug.*",
        )
        val code = """
            fun f() {
                Label("Hello")
                Text("Hello")
                Card(caption = "Hi")
                Card(text = "Hi")
                DebugCard(caption = "Hi")
            }
        """.trimIndent()
        assertEquals(2, findings(code, config).size)
    }
}
