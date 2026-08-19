package engine

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigTuningTest {

    private val saved = listOf(
        Config.level, Config.continuationSpeed
    )
    private val savedNums = listOf(
        Config.minConfidenceEmit, Config.reverseMinScoreRatio, Config.continuationMinCoverage
    )
    private val savedFlags = listOf(
        Config.reverseEnabled, Config.normalizeStt, Config.inferBookAtEnd
    )
    private val savedTtl = Config.stickyTtlMs

    @AfterTest
    fun restore() {
        Config.level = saved[0]
        Config.continuationSpeed = saved[1]
        Config.minConfidenceEmit = savedNums[0]
        Config.reverseMinScoreRatio = savedNums[1]
        Config.continuationMinCoverage = savedNums[2]
        Config.reverseEnabled = savedFlags[0]
        Config.normalizeStt = savedFlags[1]
        Config.inferBookAtEnd = savedFlags[2]
        Config.stickyTtlMs = savedTtl
    }

    @Test
    fun `off disables the reverse lookup and every gated inference`() {
        Config.applyLevel("off")

        assertEquals("off", Config.level)
        assertFalse(Config.reverseEnabled)
        assertFalse(Config.normalizeStt)
        assertFalse(Config.inferBookAtEnd)
    }

    @Test
    fun `conservative raises the confidence bar and keeps the sticky longest`() {
        Config.applyLevel("conservative")

        assertTrue(Config.reverseEnabled)
        assertEquals(0.6, Config.minConfidenceEmit)
        assertEquals(2.5, Config.reverseMinScoreRatio)
        assertEquals(240_000L, Config.stickyTtlMs)
        assertFalse(Config.normalizeStt)
        assertFalse(Config.inferBookAtEnd)
    }

    @Test
    fun `balanced normalizes STT but will not infer a trailing book`() {
        Config.applyLevel("balanced")

        assertEquals(0.4, Config.minConfidenceEmit)
        assertEquals(2.0, Config.reverseMinScoreRatio)
        assertEquals(180_000L, Config.stickyTtlMs)
        assertTrue(Config.normalizeStt)
        assertFalse(Config.inferBookAtEnd)
    }

    @Test
    fun `aggressive lowers every gate and enables the trailing-book inference`() {
        Config.applyLevel("aggressive")

        assertEquals(0.3, Config.minConfidenceEmit)
        assertEquals(1.5, Config.reverseMinScoreRatio)
        assertEquals(90_000L, Config.stickyTtlMs)
        assertTrue(Config.inferBookAtEnd)
    }

    @Test
    fun `the level name is matched case-insensitively`() {
        Config.applyLevel("AGGRESSIVE")

        assertEquals("aggressive", Config.level)
        assertTrue(Config.inferBookAtEnd)
    }

    @Test
    fun `an unrecognized level records the name but changes no tuning`() {
        Config.applyLevel("balanced")
        val before = listOf(Config.minConfidenceEmit, Config.reverseMinScoreRatio, Config.stickyTtlMs.toDouble())

        Config.applyLevel("turbo")

        assertEquals("turbo", Config.level)
        assertEquals(before, listOf(Config.minConfidenceEmit, Config.reverseMinScoreRatio, Config.stickyTtlMs.toDouble()))
    }

    @Test
    fun `verse speed balanced and fast set their documented coverage floors`() {
        Config.applyContinuationSpeed("balanced")
        assertEquals(0.5, Config.continuationMinCoverage)

        Config.applyContinuationSpeed("fast")
        assertEquals(0.45, Config.continuationMinCoverage)
        assertEquals("fast", Config.continuationSpeed)
    }

    @Test
    fun `an unrecognized verse speed is a no-op on the floor`() {
        Config.applyContinuationSpeed("fast")

        Config.applyContinuationSpeed("glacial")

        assertEquals("glacial", Config.continuationSpeed)
        assertEquals(0.45, Config.continuationMinCoverage)
    }

    @Test
    fun `verse speed is independent of the aggressiveness level`() {
        Config.applyContinuationSpeed("fast")
        Config.applyLevel("conservative")

        assertEquals(0.45, Config.continuationMinCoverage)
    }
}
