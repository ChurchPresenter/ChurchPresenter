package org.churchpresenter.helper.intent.semantic

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

/**
 * The Kotlin encoder against the reference: `helper/tools/export_minilm.py` writes, for a dozen
 * sentences, the token ids the reference tokenizer gives and the vector its NumPy forward pass computes
 * from the very int8 weights this module bundles (that forward pass matches ONNX Runtime exactly).
 * Same weights, same arithmetic, so the vectors must agree to float noise.
 */
class MiniLmEncoderTest {

    private class Row(val sentence: String, val ids: IntArray, val vector: FloatArray)

    private val rows: List<Row> = javaClass.classLoader.getResourceAsStream("wick/reference.tsv")!!
        .bufferedReader().readLines().filter { it.isNotBlank() }.map { line ->
            val (sentence, ids, vector) = line.split('\t')
            Row(
                sentence,
                ids.split(' ').map { it.toInt() }.toIntArray(),
                vector.split(' ').map { it.toFloat() }.toFloatArray(),
            )
        }

    private val encoder = TestModel.encoder

    @Test
    fun `the tokenizer cuts every reference sentence into the reference's pieces`() {
        for (row in rows) {
            assertContentEquals(row.ids, encoder.tokenize(row.sentence), row.sentence)
        }
    }

    @Test
    fun `each sentence lands where the reference puts it`() {
        for (row in rows) {
            val similarity = cosine(encoder.encode(row.sentence), row.vector)
            assertTrue(similarity > MATCH, "${row.sentence}: cosine $similarity")
        }
    }

    @Test
    fun `sentences that mean the same are closer than sentences that do not`() {
        val blank = encoder.encode("make the screen go black")
        assertTrue(cosine(blank, encoder.encode("clear the screen")) > cosine(blank, encoder.encode("next slide")))
    }

    private companion object {
        /** Float rounding only; anything lower is a different computation. */
        const val MATCH = 0.9999f
    }
}
