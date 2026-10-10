package org.churchpresenter.helper.pack

import kotlinx.serialization.json.Json
import org.churchpresenter.helper.intent.semantic.CatalogTarget
import org.churchpresenter.helper.intent.semantic.MiniLmEncoder
import org.churchpresenter.helper.intent.semantic.WickCatalog
import org.junit.jupiter.api.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `./gradlew :helper:buildWickPack`: turns the hand-written `wick-pack/source.json` into `wick-pack/pack.json`,
 * embedding each phrase and tour trigger with the model `updateWickCatalog` uses (re-using the vectors of
 * text that has not changed), then reads the result back as the app would and fails if it dropped anything.
 *
 * Tagged `wickPack`, so `test` leaves it out.
 */
@Tag("wickPack")
class WickPackBuild {

    private val packFile = WickPackSource.packFile
    private val json = Json { prettyPrint = true }

    @Test
    fun `build the pack`() {
        val source = WickPackSource.read()
        val wanted = source.wanted().map { (target, text) ->
            assertNotNull(CatalogTarget.parse(target), "Unknown target $target") to text
        }
        val known = previousVectors()
        val encoder by lazy { MiniLmEncoder.load() }
        val lines = wanted.map { (target, text) ->
            val vector = known[target.format() to text] ?: WickCatalog.encodeVector(encoder.encode(text))
            "${target.format()}\t$text\t$vector"
        }
        val file = PackFile(source.version, source.minApp, lines, source.tours, source.tips)
        val written = json.encodeToString(PackFile.serializer(), file) + "\n"

        val pack = assertNotNull(parseWickPack(written, source.minApp), "The built pack does not load")
        assertEquals(source.tours.map { it.id }.toSet(), pack.tours.keys, "Tours this build cannot run")
        assertEquals(lines.size, pack.phrases.size, "Phrases whose target this build does not have")
        assertEquals(source.tips.size, pack.tips.size, "Tips that were dropped")
        assertEquals(
            source.tips.count { it.tour != null },
            pack.tips.count { it.action != null },
            "Tips naming a tour that is not in the pack",
        )
        packFile.writeText(written)
        val counts = "${lines.size} phrases, ${pack.tours.size} tours, ${pack.tips.size} tips"
        println("Wrote Wick pack ${source.version}: $counts")
    }

    private fun previousVectors(): Map<Pair<String, String>, String> {
        if (!packFile.isFile) return emptyMap()
        val previous = runCatching { Json.decodeFromString(PackFile.serializer(), packFile.readText()) }.getOrNull()
        return previous?.phrases.orEmpty().mapNotNull { line ->
            line.split('\t').takeIf { it.size == 3 }?.let { (target, text, vector) -> (target to text) to vector }
        }.toMap()
    }
}
