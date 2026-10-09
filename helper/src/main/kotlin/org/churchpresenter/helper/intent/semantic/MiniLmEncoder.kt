package org.churchpresenter.helper.intent.semantic

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * `all-MiniLM-L6-v2`, run in plain Kotlin: a sentence in, a unit vector of [HIDDEN] floats out, so that
 * two sentences that mean the same thing point the same way. Six BERT layers over the WordPiece tokens,
 * then the mean of the last layer.
 *
 * One request is a few hundred thousand multiply-adds per token on one thread — milliseconds — and the
 * weights stay int8 ([Int8Rows]), so the model holds about 23 MB while loaded. Not thread-safe: one
 * encoder per caller, which [SemanticMatcher] guarantees.
 */
internal class MiniLmEncoder(private val tokenizer: WordPieceTokenizer, private val w: MiniLmWeights) {

    fun encode(text: String): FloatArray = encodeIds(tokenizer.encode(text))

    /** The WordPiece ids [text] is read as, `[CLS]` and `[SEP]` included. */
    fun tokenize(text: String): IntArray = tokenizer.encode(text)

    fun encodeIds(ids: IntArray): FloatArray {
        val n = ids.size
        var x = embed(ids)
        repeat(LAYERS) { layer -> x = layer(x, n, "encoder.layer.$layer.") }
        val pooled = FloatArray(HIDDEN)
        for (t in 0 until n) for (c in 0 until HIDDEN) pooled[c] += x[t * HIDDEN + c]
        return unit(pooled)
    }

    private fun embed(ids: IntArray): FloatArray {
        val words = w.rows("embeddings.word_embeddings.weight")
        val positions = w.floats("embeddings.position_embeddings.weight")
        val tokenType = w.floats("embeddings.token_type_embeddings.weight")
        val x = FloatArray(ids.size * HIDDEN)
        for ((t, id) in ids.withIndex()) {
            words.copyRow(id, x, t * HIDDEN)
            for (c in 0 until HIDDEN) x[t * HIDDEN + c] += positions[t * HIDDEN + c] + tokenType[c]
        }
        layerNorm(x, ids.size, "embeddings.LayerNorm")
        return x
    }

    private fun layer(x: FloatArray, n: Int, p: String): FloatArray {
        val q = dense(x, n, p + "attention.self.query")
        val k = dense(x, n, p + "attention.self.key")
        val v = dense(x, n, p + "attention.self.value")
        val attended = dense(attention(q, k, v, n), n, p + "attention.output.dense")
        for (i in attended.indices) attended[i] += x[i]
        layerNorm(attended, n, p + "attention.output.LayerNorm")

        val hidden = dense(attended, n, p + "intermediate.dense")
        for (i in hidden.indices) hidden[i] = gelu(hidden[i])
        val out = dense(hidden, n, p + "output.dense")
        for (i in out.indices) out[i] += attended[i]
        layerNorm(out, n, p + "output.LayerNorm")
        return out
    }

    /** Twelve heads of scaled dot-product attention over all [n] tokens (there is no padding to mask). */
    private fun attention(q: FloatArray, k: FloatArray, v: FloatArray, n: Int): FloatArray =
        Attention(q, k, v, n).run()

    /** One layer's attention: the queries, keys and values of its [n] tokens, read a head at a time. */
    private class Attention(val q: FloatArray, val k: FloatArray, val v: FloatArray, val n: Int) {
        private val out = FloatArray(n * HIDDEN)
        private val weights = FloatArray(n)

        fun run(): FloatArray {
            for (h in 0 until HEADS) {
                for (i in 0 until n) {
                    weigh(i, h * HEAD_SIZE)
                    mix(i, h * HEAD_SIZE)
                }
            }
            return out
        }

        /** How much token [i] attends to each token, in the head starting at [base]: a softmax. */
        private fun weigh(i: Int, base: Int) {
            var max = Float.NEGATIVE_INFINITY
            for (j in 0 until n) {
                var s = 0f
                for (d in 0 until HEAD_SIZE) s += q[i * HIDDEN + base + d] * k[j * HIDDEN + base + d]
                weights[j] = s * SCALE
                if (weights[j] > max) max = weights[j]
            }
            var total = 0f
            for (j in 0 until n) {
                weights[j] = exp(weights[j] - max)
                total += weights[j]
            }
            for (j in 0 until n) weights[j] /= total
        }

        /** Token [i]'s output in that head: the values, mixed by its weights. */
        private fun mix(i: Int, base: Int) {
            for (j in 0 until n) {
                for (d in 0 until HEAD_SIZE) out[i * HIDDEN + base + d] += weights[j] * v[j * HIDDEN + base + d]
            }
        }

        private companion object {
            val SCALE = 1f / sqrt(HEAD_SIZE.toFloat())
        }
    }

    /**
     * `x · Wᵀ + b` for each of the [n] tokens; [name] is the layer, holding `.weight` and `.bias`.
     *
     * Each weight row is expanded to floats once and reused for every token, and the dot product keeps
     * four running sums so the CPU need not wait on each add — the same arithmetic, several times faster.
     */
    private fun dense(x: FloatArray, n: Int, name: String): FloatArray {
        val weight = w.rows("$name.weight")
        val bias = w.floats("$name.bias")
        val cols = weight.cols
        val row = FloatArray(cols)
        val out = FloatArray(n * weight.rows)
        for (r in 0 until weight.rows) {
            weight.copyRow(r, row, 0)
            for (t in 0 until n) out[t * weight.rows + r] = dot4(row, x, t * cols, cols) + bias[r]
        }
        return out
    }

    private fun layerNorm(x: FloatArray, n: Int, name: String) {
        val gamma = w.floats("$name.weight")
        val beta = w.floats("$name.bias")
        for (t in 0 until n) {
            val at = t * HIDDEN
            var mean = 0f
            for (c in 0 until HIDDEN) mean += x[at + c]
            mean /= HIDDEN
            var variance = 0f
            for (c in 0 until HIDDEN) {
                val d = x[at + c] - mean
                variance += d * d
            }
            val inv = 1f / sqrt(variance / HIDDEN + LAYER_NORM_EPS)
            for (c in 0 until HIDDEN) x[at + c] = (x[at + c] - mean) * inv * gamma[c] + beta[c]
        }
    }

    companion object {
        /** Reads the bundled model and vocabulary (`wick/` in this module's resources). */
        fun load(): MiniLmEncoder {
            val loader = MiniLmEncoder::class.java.classLoader
            val vocab = loader.getResourceAsStream("wick/vocab.txt")?.bufferedReader()?.use { it.readLines() }
                ?: error("wick/vocab.txt is missing from the helper's resources")
            val weights = loader.getResourceAsStream("wick/minilm-l6.bin")?.let(MiniLmWeights::read)
                ?: error("wick/minilm-l6.bin is missing from the helper's resources")
            return MiniLmEncoder(WordPieceTokenizer(vocab), weights)
        }

        const val HIDDEN = 384
        private const val LAYERS = 6
        private const val HEADS = 12
        private const val HEAD_SIZE = HIDDEN / HEADS
        private const val LAYER_NORM_EPS = 1e-12f
    }
}

/** [v] scaled to length 1 (left as is when it is all zeros). */
internal fun unit(v: FloatArray): FloatArray {
    var sum = 0.0
    for (x in v) sum += x * x
    if (sum == 0.0) return v
    val inv = (1.0 / sqrt(sum)).toFloat()
    for (i in v.indices) v[i] *= inv
    return v
}

/** [a] (from 0) times [b] (from [bOffset]) over [length] floats, summed four ways; [length] is a multiple of 4. */
// The offsets 1 to 3 and the step of 4 are the four-way unrolling itself.
@Suppress("MagicNumber")
internal fun dot4(a: FloatArray, b: FloatArray, bOffset: Int, length: Int): Float {
    var s0 = 0f
    var s1 = 0f
    var s2 = 0f
    var s3 = 0f
    var i = 0
    while (i < length) {
        s0 += a[i] * b[bOffset + i]
        s1 += a[i + 1] * b[bOffset + i + 1]
        s2 += a[i + 2] * b[bOffset + i + 2]
        s3 += a[i + 3] * b[bOffset + i + 3]
        i += 4
    }
    return (s0 + s1) + (s2 + s3)
}

/** The cosine of two unit vectors. */
internal fun cosine(a: FloatArray, b: FloatArray): Float {
    var s = 0f
    for (i in a.indices) s += a[i] * b[i]
    return s
}

/** BERT's GELU, the exact form: `x · Φ(x)`. */
internal fun gelu(x: Float): Float = HALF * x * (1f + erf(x / SQRT_2))

private const val HALF = 0.5f
private const val SQRT_2 = 1.4142135f

/** The error function, to within about 1e-7 (Abramowitz & Stegun 7.1.26) — far below float noise here. */
@Suppress("MagicNumber")
internal fun erf(x: Float): Float {
    val t = 1.0 / (1.0 + 0.3275911 * abs(x))
    val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
        t * exp(-(x.toDouble() * x))
    return (if (x >= 0) y else -y).toFloat()
}
