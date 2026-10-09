package org.churchpresenter.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StallDetectorTest {

    private val lines = mutableListOf<String>()
    private val frame = StackTraceElement("Compose", "measure", "Layout.kt", 42)
    private val detector = StallDetector(budgetMs = 100, stack = { listOf(frame) }, report = { lines += it })

    @Test
    fun `every answer is told how late it was, stalled or not`() {
        val lates = mutableListOf<Long>()
        val counting = StallDetector(
            budgetMs = 100,
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
    fun `an answer to no ping and a poll with none out are ignored`() {
        detector.answered(10)
        detector.poll(10_000)

        assertTrue(lines.isEmpty())
    }
}
