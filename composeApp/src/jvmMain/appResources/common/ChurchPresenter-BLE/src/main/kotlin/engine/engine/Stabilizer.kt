package engine.engine

import engine.Config

class Stabilizer(private val clock: () -> Long = System::currentTimeMillis) {

    private val lastEmittedAt = HashMap<String, Long>()
    private val lastConfidence = HashMap<String, Double>()
    // When this ref last produced an actual emission (new or updated) — gates the re-emit cooldown.
    private val lastEmissionAt = HashMap<String, Long>()

    sealed class EmitDecision {
        data class NewDetection(val key: String) : EmitDecision()
        data class UpdatedDetection(val key: String, val oldConfidence: Double) : EmitDecision()
        // [reason] = "below-confidence" (under the emit threshold) or "deduped" (same ref, no change
        // within the TTL). Surfaced so the candidate log can record why a near-miss was dropped.
        data class Suppress(val reason: String) : EmitDecision()
    }

    fun evaluate(key: String, confidence: Double): EmitDecision {
        if (confidence < Config.minConfidenceEmit) return EmitDecision.Suppress("below-confidence")

        val now = clock()
        val seenAt = lastEmittedAt[key]
        // Time-based dedup: a reference re-emits only after it has gone quiet for dedupTtlMs, so the
        // speaker returning to a passage later re-fires (the old fixed 32-key window never did).
        if (seenAt == null || now - seenAt > Config.dedupTtlMs) {
            lastEmittedAt[key] = now
            lastConfidence[key] = confidence
            lastEmissionAt[key] = now
            pruneExpired(now)
            return EmitDecision.NewDetection(key)
        }
        lastEmittedAt[key] = now
        val prev = lastConfidence[key] ?: 0.0
        // Re-emit a held passage only on a meaningful confidence move AND after a cooldown — the
        // reverse-lookup confidence oscillates as the window slides, which otherwise churns the same
        // verse back on screen many times in a row.
        val sinceEmission = now - (lastEmissionAt[key] ?: 0L)
        return if (kotlin.math.abs(confidence - prev) >= Config.reEmitMinDelta &&
            sinceEmission >= Config.reEmitCooldownMs
        ) {
            lastConfidence[key] = confidence
            lastEmissionAt[key] = now
            EmitDecision.UpdatedDetection(key, prev)
        } else {
            EmitDecision.Suppress("deduped")
        }
    }

    private fun pruneExpired(now: Long) {
        if (lastEmittedAt.size < 256) return
        val cutoff = now - Config.dedupTtlMs
        val it = lastEmittedAt.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (e.value < cutoff) { lastConfidence.remove(e.key); lastEmissionAt.remove(e.key); it.remove() }
        }
    }

    fun reset() {
        lastEmittedAt.clear()
        lastConfidence.clear()
        lastEmissionAt.clear()
    }
}
