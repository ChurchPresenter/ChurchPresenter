package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallDetectorTest {

    private val lines = mutableListOf<String>()
    private val frame = StackTraceElement("Compose", "measure", "Layout.kt", 42)
    private val detector = StallDetector(budgetMs = { 100 }, stack = { listOf(frame) }, report = { lines += it })

    @Test
    fun `every answer is told how late it was, stalled or not`() {
        val lates = mutableListOf<Long>()
        val counting = StallDetector(
            budgetMs = { 100 },
            stack = { listOf(frame) },
            report = { },
            onAnswered = { lates += it },
        )
        counting.posted(0)
        counting.answered(40)
        counting.posted(100)
        counting.poll(300)
        counting.answered(350)
        counting.answered(400)

        assertEquals(listOf(40L, 250L), lates, "an answer to no ping is not counted")
    }

    @Test
    fun `a ping answered inside the budget says nothing`() {
        detector.posted(0)
        detector.poll(50)
        detector.answered(60)

        assertTrue(lines.isEmpty())
        assertFalse(detector.isWaiting)
    }

    @Test
    fun `a ping unanswered past the budget reports where the thread is, once, while it is still stuck`() {
        detector.posted(0)
        detector.poll(100)
        assertTrue(lines.isEmpty(), "exactly the budget is still inside it")

        detector.poll(101)
        detector.poll(150)
        detector.poll(900)

        assertEquals(1, lines.size)
        assertTrue("has not answered for 101ms" in lines.single())
        assertTrue("at Compose.measure(Layout.kt:42)" in lines.single())
        assertTrue(detector.isWaiting)
    }

    @Test
    fun `when the stuck thread answers, how long it was out is reported`() {
        detector.posted(0)
        detector.poll(200)
        detector.answered(750)

        assertEquals(2, lines.size)
        assertEquals("UI thread answered after 750ms (budget 100ms)", lines.last())
        assertFalse(detector.isWaiting)
    }

    @Test
    fun `the next ping starts a fresh stall`() {
        detector.posted(0)
        detector.poll(200)
        detector.answered(300)
        lines.clear()

        detector.posted(1000)
        detector.poll(1050)
        detector.poll(1200)

        assertEquals(1, lines.size)
        assertTrue("has not answered for 200ms" in lines.single())
    }

    @Test
    fun `a ping keeps the budget it was posted with`() {
        var budget = 100L
        val changing = StallDetector(budgetMs = { budget }, stack = { listOf(frame) }, report = { lines += it })
        changing.posted(0)
        budget = 500
        changing.poll(150)

        assertEquals(1, lines.size, "a budget that changes mid-ping does not move that ping's")
        assertEquals(100, changing.currentBudgetMs)

        changing.answered(200)
        changing.posted(1_000)
        assertEquals(500, changing.currentBudgetMs, "the next ping takes the new one")
    }

    @Test
    fun `a stall is handed over once it is over, with where it was stuck`() {
        val stalls = mutableListOf<Stall>()
        val watched = StallDetector(
            budgetMs = { 100 },
            stack = { listOf(frame) },
            report = { },
            onStall = { stalls += it },
        )
        watched.posted(0)
        watched.answered(80)
        assertTrue(stalls.isEmpty(), "an answer inside the budget is not a stall")

        watched.posted(100)
        watched.poll(250)
        assertTrue(stalls.isEmpty(), "not until the thread answers")

        watched.answered(420)
        assertEquals(listOf(Stall(lateMs = 320, budgetMs = 100, frames = listOf(frame))), stalls)
    }

    @Test
    fun `an answer to no ping and a poll with none out are ignored`() {
        detector.answered(10)
        detector.poll(10_000)

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `a stall is sampled again every interval while it lasts, and no more than the cap`() {
        var taken = 0
        val stalls = mutableListOf<Stall>()
        val sampling = StallDetector(
            budgetMs = { 100 },
            stack = { taken++; listOf(StackTraceElement("Compose", "step$taken", null, -1)) },
            report = { },
            onStall = { stalls += it },
        )
        sampling.posted(0)
        assertEquals(20, sampling.pollDelayMs, "a fifth of the budget while nothing is stuck")
        sampling.poll(101)
        sampling.poll(120)
        assertEquals(1, taken, "not again before the interval")
        sampling.poll(101 + StallDetector.SAMPLE_INTERVAL_MS)
        assertEquals(2, taken)

        sampling.answered(200)
        val stall = stalls.single()
        assertEquals(listOf("step1"), stall.frames.map { it.methodName }, "the stack from when the budget ran out")
        assertEquals(listOf("step1", "step2"), stall.samples.map { it.single().methodName })

        sampling.posted(1_000)
        var now = 1_101L
        repeat(StallDetector.MAX_SAMPLES + 10) {
            sampling.poll(now)
            now += StallDetector.SAMPLE_INTERVAL_MS
        }
        sampling.answered(now)
        assertEquals(StallDetector.MAX_SAMPLES, stalls.last().samples.size, "a long stall keeps a bounded count")
    }

    @Test
    fun `a stall being sampled is polled at least every sampling interval`() {
        val slow = StallDetector(budgetMs = { 1_000 }, stack = { listOf(frame) }, report = { })
        slow.posted(0)
        assertEquals(200, slow.pollDelayMs)

        slow.poll(1_001)

        assertEquals(StallDetector.SAMPLE_INTERVAL_MS, slow.pollDelayMs)
    }
}
