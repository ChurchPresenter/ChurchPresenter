package engine

import engine.engine.Stabilizer
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StabilizerPruneTest {

    private val savedMinConfidence = Config.minConfidenceEmit
    private val savedTtl = Config.dedupTtlMs
    private val savedDelta = Config.reEmitMinDelta
    private val savedCooldown = Config.reEmitCooldownMs

    private var now = 1_000L
    private val stabilizer = Stabilizer { now }

    @AfterTest
    fun restore() {
        Config.minConfidenceEmit = savedMinConfidence
        Config.dedupTtlMs = savedTtl
        Config.reEmitMinDelta = savedDelta
        Config.reEmitCooldownMs = savedCooldown
    }

    @Test
    fun `a confidence move past the delta after the cooldown re-emits as an update`() {
        Config.reEmitMinDelta = 0.15
        Config.reEmitCooldownMs = 10_000L
        stabilizer.evaluate("John 3:16", 0.5)

        now += Config.reEmitCooldownMs + 1
        val decision = stabilizer.evaluate("John 3:16", 0.9)

        val updated = assertIs<Stabilizer.EmitDecision.UpdatedDetection>(decision)
        assertEquals(0.5, updated.oldConfidence)
    }

    @Test
    fun `a big confidence move inside the cooldown is suppressed`() {
        Config.reEmitMinDelta = 0.15
        Config.reEmitCooldownMs = 10_000L
        stabilizer.evaluate("John 3:16", 0.5)

        now += 1_000
        val decision = stabilizer.evaluate("John 3:16", 0.95)

        assertEquals("deduped", assertIs<Stabilizer.EmitDecision.Suppress>(decision).reason)
    }

    @Test
    fun `a small confidence move after the cooldown is suppressed`() {
        Config.reEmitMinDelta = 0.15
        Config.reEmitCooldownMs = 10_000L
        stabilizer.evaluate("John 3:16", 0.5)

        now += Config.reEmitCooldownMs + 1
        val decision = stabilizer.evaluate("John 3:16", 0.51)

        assertEquals("deduped", assertIs<Stabilizer.EmitDecision.Suppress>(decision).reason)
    }

    @Test
    fun `a downward confidence move counts as a move`() {
        Config.reEmitMinDelta = 0.15
        Config.reEmitCooldownMs = 10_000L
        stabilizer.evaluate("John 3:16", 0.9)

        now += Config.reEmitCooldownMs + 1
        val decision = stabilizer.evaluate("John 3:16", 0.5)

        assertIs<Stabilizer.EmitDecision.UpdatedDetection>(decision)
    }

    @Test
    fun `a detection under the emit floor never reaches the dedup bookkeeping`() {
        Config.minConfidenceEmit = 0.4

        val decision = stabilizer.evaluate("John 3:16", 0.1)

        assertEquals("below-confidence", assertIs<Stabilizer.EmitDecision.Suppress>(decision).reason)
        assertIs<Stabilizer.EmitDecision.NewDetection>(stabilizer.evaluate("John 3:16", 0.9))
    }

    @Test
    fun `the dedup table prunes once it grows past its threshold`() {
        Config.dedupTtlMs = 45_000L
        repeat(300) { stabilizer.evaluate("ref-$it", 0.9) }

        now += Config.dedupTtlMs * 2
        assertIs<Stabilizer.EmitDecision.NewDetection>(stabilizer.evaluate("trigger-prune", 0.9))

        assertIs<Stabilizer.EmitDecision.NewDetection>(stabilizer.evaluate("ref-0", 0.9))
    }

    @Test
    fun `entries still inside the TTL survive a prune`() {
        Config.dedupTtlMs = 45_000L
        repeat(300) { stabilizer.evaluate("ref-$it", 0.9) }

        now += 1_000
        stabilizer.evaluate("trigger-prune", 0.9)

        assertEquals("deduped", assertIs<Stabilizer.EmitDecision.Suppress>(stabilizer.evaluate("ref-0", 0.9)).reason)
    }

    @Test
    fun `reset forgets every held passage`() {
        stabilizer.evaluate("John 3:16", 0.9)
        assertTrue(stabilizer.evaluate("John 3:16", 0.9) is Stabilizer.EmitDecision.Suppress)

        stabilizer.reset()

        assertIs<Stabilizer.EmitDecision.NewDetection>(stabilizer.evaluate("John 3:16", 0.9))
    }
}
