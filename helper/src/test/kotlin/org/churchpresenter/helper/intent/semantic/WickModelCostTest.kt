package org.churchpresenter.helper.intent.semantic

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What one request costs, so a change that makes the model heavier fails here rather than on a Sunday:
 * a warm request stays well inside the time an operator waits for a reply, on one low-priority thread.
 */
class WickModelCostTest {

    @Test
    fun `a request takes tens of milliseconds once the model is warm`() {
        val encoder = TestModel.encoder
        val sentence = "the words are too small to read from the back"
        repeat(WARM_UP) { encoder.encode(sentence) }

        val started = System.nanoTime()
        repeat(MEASURED) { encoder.encode(sentence) }
        val perRequest = (System.nanoTime() - started) / MEASURED / NANOS_PER_MS

        assertTrue(perRequest < BUDGET_MS, "one request took $perRequest ms")
    }

    @Test
    fun `the matcher ranks the whole catalog against a request`() {
        val ranked = runBlocking { SemanticMatcher({ TestModel.encoder }).rank(listOf("blank the screen")) }

        assertTrue(assertNotNull(ranked).isNotEmpty())
        assertTrue(ranked.zipWithNext().all { (a, b) -> a.score >= b.score }, "best first")
    }

    @Test
    fun `a matcher whose model will not load reports that rather than failing`() {
        val ranked = runBlocking { SemanticMatcher({ error("missing") }).rank(listOf("anything")) }

        assertTrue(ranked == null)
    }

    private companion object {
        const val WARM_UP = 5
        const val MEASURED = 5
        const val NANOS_PER_MS = 1_000_000

        /** Twice what a laptop takes, for slower CI machines; a model several times heavier still fails. */
        const val BUDGET_MS = 100
    }
}
