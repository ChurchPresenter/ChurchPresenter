package org.churchpresenter.settings

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsWriterTest {
    private val file = File("unused-settings.json")

    @Test
    fun `a save waiting behind another is replaced by the newer one`() {
        val queued = mutableListOf<Runnable>()
        val writer = SettingsWriter { queued += it }
        val written = mutableListOf<String>()

        writer.submit(file) { written += "first" }
        writer.submit(file) { written += "second" }
        writer.submit(file) { written += "third" }
        queued.forEach { it.run() }

        assertEquals(listOf("third"), written)
        assertEquals(1, queued.size, "one drain for a burst of saves")
    }

    @Test
    fun `flush writes what is waiting on the caller's thread, so nothing is left for the writer`() {
        val queued = mutableListOf<Runnable>()
        val writer = SettingsWriter { queued += it }
        val written = mutableListOf<String>()

        writer.submit(file) { written += "saved" }
        writer.flush(file)
        queued.forEach { it.run() }

        assertEquals(listOf("saved"), written)
    }

    @Test
    fun `flush waits for a write the writer thread has already started`() {
        val pool = Executors.newSingleThreadExecutor()
        try {
            val writer = SettingsWriter(pool)
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            var done = false
            writer.submit(file) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                done = true
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            Thread { release.countDown() }.start()

            writer.flush(file)

            assertTrue(done)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `flushAll writes every file that has something waiting`() {
        val writer = SettingsWriter { }
        val written = mutableListOf<String>()

        writer.submit(File("a.json")) { written += "a" }
        writer.submit(File("b.json")) { written += "b" }
        writer.flushAll()

        assertEquals(setOf("a", "b"), written.toSet())
    }

    @Test
    fun `a write that throws does not leave the file marked as being written`() {
        val writer = SettingsWriter { }

        writer.submit(file) { error("disk full") }
        runCatching { writer.flush(file) }
        writer.flush(file)

        writer.submit(file) { }
        writer.flush(file)
    }
}
