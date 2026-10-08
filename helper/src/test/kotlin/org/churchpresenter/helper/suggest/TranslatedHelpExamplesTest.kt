package org.churchpresenter.helper.suggest

import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslatedHelpExamplesTest {

    private val resolver = RuleIntentResolver()

    private fun strings(dir: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(dir, "strings.xml")).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun outcome(text: String, language: String): String {
        val resolution = resolver.resolveNow(text, ResolveContext(language = language))
        val action = (resolution as? Resolution.Act)?.action ?: return resolution.toString()
        return if (action is HelperAction.Highlight) {
            "tour " + action.tour.steps.joinToString(" > ") { it.target.id }
        } else {
            action.toString()
        }
    }

    @Test
    fun `every translated help example does what its english request does`() {
        val locales = RESOURCES.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }.orEmpty()
        assertTrue(locales.size >= 30, "found ${locales.size} locales at ${RESOURCES.absolutePath}")
        val commands = HELPER_COMMANDS.flatMap { it.commands }
        val wrong = locales.sortedBy { it.name }.flatMap { dir ->
            val language = dir.name.removePrefix("values-")
            val translated = strings(dir)
            commands.mapNotNull { command ->
                val example = translated[command.example.key] ?: return@mapNotNull null
                val expected = outcome(command.request, "en")
                val actual = outcome(example, language)
                if (actual == expected) null else "$language ${command.example.key}: \"$example\" -> $actual"
            }
        }
        assertEquals(emptyList(), wrong)
    }

    private companion object {
        val RESOURCES = File("../strings/src/main/composeResources")
    }
}
