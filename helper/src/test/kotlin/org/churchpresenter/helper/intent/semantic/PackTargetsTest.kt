package org.churchpresenter.helper.intent.semantic

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.pack.PackFile
import org.churchpresenter.helper.pack.PackStepFile
import org.churchpresenter.helper.pack.PackTourFile
import org.churchpresenter.helper.pack.WickPack
import org.churchpresenter.helper.pack.parseWickPack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A downloaded pack's phrases ranked beside the catalog, and its tours as what they lead to. */
class PackTargetsTest {

    private val context = ResolveContext(language = "en")

    private fun packWith(vararg phrases: String): WickPack {
        val step = PackStepFile("preview.clear", hint = "Here.")
        val tour = PackTourFile("lamp-oil", "Fill the lamp", steps = listOf(step))
        val file = PackFile(1, "1", phrases.toList(), listOf(tour))
        return assertNotNull(parseWickPack(Json.encodeToString(PackFile.serializer(), file), "26.0.0"))
    }

    @Test
    fun `a pack's tour is acted on when sure, asked about when close, and unknown without the pack`() {
        val pack = packWith()
        val withPack = SemanticIntentResolver(RuleIntentResolver(), SemanticMatcher({ error("no model") }), { pack })
        val target = CatalogTarget.PackTour("lamp-oil")

        val sure = assertIs<Resolution.Act>(withPack.decide(listOf(Scored(target, 0.9f)), context, "x"))
        val tour = assertIs<HelperAction.Highlight>(sure.action)
        assertEquals(HelperText.Plain("Fill the lamp"), tour.label)
        assertEquals("preview.clear", tour.tour.steps.single().target.id)

        val close = assertIs<Resolution.DidYouMean>(withPack.decide(listOf(Scored(target, 0.5f)), context, "x"))
        assertEquals(HelperText.Plain("Fill the lamp"), close.label)

        val without = SemanticIntentResolver(RuleIntentResolver(), SemanticMatcher({ error("no model") }), { null })
        assertEquals(Resolution.Unknown, without.decide(listOf(Scored(target, 0.9f)), context, "x"))
    }

    @Test
    fun `the matcher ranks a pack's phrases beside the catalog`() {
        val text = "pour oil into the little brass lamp"
        val vector = WickCatalog.encodeVector(TestModel.encoder.encode(text))
        val pack = packWith("tour:lamp-oil\t$text\t$vector")
        val matcher = SemanticMatcher({ TestModel.encoder }, loadCatalog = { emptyList() }, pack = { pack })
        val best = assertNotNull(runBlocking { matcher.rank(listOf(text)) }).first()
        assertEquals(CatalogTarget.PackTour("lamp-oil"), best.target)
        assertTrue(best.score > 0.99f, "${best.score}")
    }
}
