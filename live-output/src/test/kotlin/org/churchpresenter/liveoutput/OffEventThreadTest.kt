package org.churchpresenter.liveoutput

import java.awt.EventQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffEventThreadTest {

    @Test
    fun `off the event thread the work runs right here`() {
        var ranOn: Thread? = null

        offEventThread("test") { ranOn = Thread.currentThread() }

        assertEquals(Thread.currentThread(), ranOn)
    }

    @Test
    fun `on the event thread the work runs on a thread of its own`() {
        val done = CountDownLatch(1)
        var ranOnEventThread = true

        EventQueue.invokeAndWait {
            offEventThread("test-close") {
                ranOnEventThread = EventQueue.isDispatchThread()
                done.countDown()
            }
        }

        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertFalse(ranOnEventThread)
    }
}
