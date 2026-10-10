package org.churchpresenter.helper.intent.semantic

import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.sharedui.models.Tabs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wick's catalog (`wick/catalog.tsv`) is generated from the codebase, and this keeps it that way: it fails
 * when the committed file no longer says what the code says — a control tagged, a label reworded, a
 * phrase added to a topic table — and names the task that regenerates it.
 *
 * `./gradlew :helper:updateWickCatalog` runs this class with `wick.catalog.update` set: it rewrites the
 * file, embedding only the lines that are new (about 30 ms each) and keeping every vector already there.
 */
class WickCatalogTest {

    private val root = File(System.getProperty("user.dir")).parentFile
    private val source = WickCatalogSource(root)
    private val catalogFile = File(root, "helper/src/main/resources/${WickCatalog.RESOURCE}")

    @Test
    fun `the committed catalog says what the codebase says`() {
        val items = source.items()
        if (System.getProperty(UPDATE) == "true") {
            write(items)
            return
        }
        val committed = committedLines().map { it.target to it.text }
        assertEquals(
            items.map { it.target.format() to it.text },
            committed,
            "wick/catalog.tsv is out of date: run ./gradlew :helper:updateWickCatalog",
        )
    }

    @Test
    fun `every entry leads to something the helper can do`() {
        val resolver = SemanticIntentResolver()
        val everything = ResolveContext(visibleTabs = Tabs.entries.toSet(), language = "en")
        for (entry in WickCatalog.load()) {
            assertNotNull(resolver.resolutionFor(entry.target, everything), "${entry.target.format()}: ${entry.text}")
        }
    }

    @Test
    fun `every file named as part of a Settings page is still there`() {
        for ((page, files) in WickCatalogSource.SETTINGS_FILES) {
            for (path in files) assertTrue(File(root, path).isFile, "$page: $path is gone")
        }
    }

    @Test
    fun `a sample of the stored vectors is what the model computes`() {
        val entries = WickCatalog.load()
        val encoder = TestModel.encoder
        val every = (entries.size / SAMPLE).coerceAtLeast(1)
        for (entry in entries.filterIndexed { i, _ -> i % every == 0 }.take(SAMPLE)) {
            val similarity = cosine(encoder.encode(entry.text), entry.vector)
            assertTrue(similarity > STORED_MATCH, "${entry.text}: cosine $similarity")
        }
    }

    @Test
    fun `every reachable tagged control is described`() {
        val missing = source.unlabelledControls()
        println("Tagged controls with no label near the tag: ${missing.ifEmpty { "none" }}")
        val unmapped = source.unmappedProfileRowFiles()
        println("Profile row files with no page: ${unmapped.ifEmpty { "none" }}")
    }

    private class Line(val target: String, val text: String, val vector: String)

    private fun committedLines(): List<Line> =
        if (!catalogFile.isFile) emptyList() else catalogFile.readLines().filter { it.isNotBlank() }.map {
            val (target, text, vector) = it.split('\t')
            Line(target, text, vector)
        }

    private fun write(items: List<WickCatalogSource.Item>) {
        val known = committedLines().associate { (it.target to it.text) to it.vector }
        val encoder = MiniLmEncoder.load()
        val lines = items.parallelStream().map { item ->
            val vector = known[item.target.format() to item.text]
                ?: WickCatalog.encodeVector(encoder.encode(item.text))
            "${item.target.format()}\t${item.text}\t$vector"
        }.toList()
        catalogFile.parentFile.mkdirs()
        catalogFile.writeText(lines.joinToString("\n", postfix = "\n"))
        val fresh = items.count { (it.target.format() to it.text) !in known }
        println("Wrote ${lines.size} catalog entries, $fresh of them new or changed")
    }

    private companion object {
        const val UPDATE = "wick.catalog.update"
        const val SAMPLE = 8

        /** fp16 storage only; anything lower is a stale vector. */
        const val STORED_MATCH = 0.999f
    }
}
