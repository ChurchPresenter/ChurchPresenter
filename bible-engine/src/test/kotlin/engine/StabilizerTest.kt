package engine

import engine.engine.Stabilizer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers the re-emission churn control (an earlier study §3): a held passage whose reverse-lookup
 * confidence oscillates as the window slides must NOT re-present the same verse over and over
 * (Иакова 2:19 fired 11× in the studied service). Emissions are bounded by [Config.reEmitMinDelta]
 * + [Config.reEmitCooldownMs], while a genuinely re-read passage (after the dedup TTL goes quiet)
 * still re-fires.
 */
class StabilizerTest {

    private var nowMs = 0L
    private fun stab() = Stabilizer { nowMs }

    @BeforeTest fun resetConfig() {
        Config.applyLevel("balanced") // minConfidenceEmit = 0.4; reEmit defaults untouched
    }

    private fun Stabilizer.EmitDecision.isEmission(): Boolean =
        this is Stabilizer.EmitDecision.NewDetection || this is Stabilizer.EmitDecision.UpdatedDetection

    @Test fun `held passage with oscillating confidence emits a bounded number of times`() {
        val s = stab()
        val key = "59:2:19" // James 2:19 — the studied churn case
        var emissions = 0
        // 30 detections at 1-second steps with confidence swinging by 0.25 each step (always over the
        // re-emit delta). Without the cooldown this would emit on nearly every step (~30×).
        repeat(30) { step ->
            nowMs = step * 1_000L
            val conf = if (step % 2 == 0) 0.60 else 0.85
            if (s.evaluate(key, conf).isEmission()) emissions++
        }
        // New at t=0, then updates gated to once per 10s → t=10s, t=20s. ≈ 3 emissions, never 30.
        assertTrue(emissions in 2..5, "expected a bounded 2..5 emissions, got $emissions")
    }

    @Test fun `first detection is a NewDetection then immediate repeats are deduped`() {
        val s = stab()
        val key = "59:2:19"
        nowMs = 0
        assertTrue(s.evaluate(key, 0.90) is Stabilizer.EmitDecision.NewDetection)
        nowMs = 1_000
        // Within the cooldown, even a large confidence swing is suppressed.
        assertEquals(
            Stabilizer.EmitDecision.Suppress("deduped"),
            s.evaluate(key, 0.50),
        )
    }

    @Test fun `a re-read passage re-fires after the dedup TTL goes quiet`() {
        val s = stab()
        val key = "59:2:19"
        nowMs = 0
        assertTrue(s.evaluate(key, 0.90) is Stabilizer.EmitDecision.NewDetection)
        // Silence longer than dedupTtlMs, then the same reference is read again → a fresh NewDetection.
        nowMs = Config.dedupTtlMs + 1_000
        assertTrue(
            s.evaluate(key, 0.90) is Stabilizer.EmitDecision.NewDetection,
            "a passage re-read after the dedup window should re-fire as a NewDetection",
        )
    }

    @Test fun `below-confidence detections are suppressed`() {
        val s = stab()
        nowMs = 0
        assertEquals(
            Stabilizer.EmitDecision.Suppress("below-confidence"),
            s.evaluate("40:1:1", Config.minConfidenceEmit - 0.01),
        )
    }
}
