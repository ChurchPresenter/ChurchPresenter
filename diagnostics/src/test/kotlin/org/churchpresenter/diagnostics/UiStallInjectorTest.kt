package org.churchpresenter.diagnostics

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What `-Dchurchpresenter.injectUiStall` is read as, and that the schedule posts the stall it names. */
class UiStallInjectorTest {

    @AfterTest
    fun stop() {
        UiStallInjector.stop()
    }

    @Test
    fun `a stall and an interval in seconds are read`() {
        assertEquals(UiStallSpec(stallMs = 500, everyMs = 2_000), UiStallSpec.parse("500,2"))
        assertEquals(UiStallSpec(stallMs = 100, everyMs = 1_000), UiStallSpec.parse(" 100 , 1 "))
    }

    @Test
    fun `anything else is no stall`() {
        listOf(null, "", "500", "500,2,3", "x,2", "500,y", "0,2", "500,0", "-5,2").forEach {
            assertNull(UiStallSpec.parse(it), "\"$it\"")
        }
    }

    @Test
    fun `the schedule posts the stall it was given, again and again`() {
        val stalls = CountDownLatch(2)
        val seen = mutableListOf<Long>()
        UiStallInjector.start(
            UiStallSpec(stallMs = 42, everyMs = 1),
            post = { it.run() },
            stall = { ms -> synchronized(seen) { seen += ms }; stalls.countDown() },
        )
        assertTrue(stalls.await(5, TimeUnit.SECONDS), "two stalls posted")
        UiStallInjector.stop()
        assertTrue(synchronized(seen) { seen.all { it == 42L } }, "$seen")
    }

    @Test
    fun `the real stall blocks the thread it runs on for as long as it says`() {
        val ran = CountDownLatch(1)
        var tookMs = 0L
        UiStallInjector.start(UiStallSpec(stallMs = 20, everyMs = 1), post = { task ->
            val start = System.nanoTime()
            task.run()
            tookMs = (System.nanoTime() - start) / 1_000_000
            ran.countDown()
        })
        assertTrue(ran.await(5, TimeUnit.SECONDS))
        UiStallInjector.stop()
        assertTrue(tookMs >= 20, "the default stall sleeps: $tookMs ms")
    }
}
