package org.churchpresenter.osrproto

import java.awt.Rectangle
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

/**
 * Per-stage counters, printed every [periodMs]: paints and how long the copy and the snapshot took,
 * how many pixels were dirty, each window's draws, its draw cost and paint→draw latency, process CPU
 * and memory.
 *
 * Process CPU covers Java and the JNI calls on this process's threads; Chromium's GPU and renderer
 * subprocesses are separate processes and are read by the OS tools in the write-up.
 */
class Stats(private val periodMs: Long = 2_000) {
    private class Bucket {
        val count = AtomicLong()
        val totalNs = AtomicLong()
        val maxNs = AtomicLong()
        val extra = AtomicLong()
        val extra2 = AtomicLong()

        fun add(ns: Long, extraValue: Long = 0, extra2Value: Long = 0) {
            count.incrementAndGet()
            totalNs.addAndGet(ns)
            maxNs.accumulateAndGet(ns, ::maxOf)
            extra.addAndGet(extraValue)
            extra2.addAndGet(extra2Value)
        }

        fun drain(): LongArray = longArrayOf(
            count.getAndSet(0), totalNs.getAndSet(0), maxNs.getAndSet(0), extra.getAndSet(0), extra2.getAndSet(0),
        )
    }

    private val copy = Bucket()
    private val snapshot = Bucket()
    private val popupPaints = AtomicLong()
    private val fullPaints = AtomicLong()
    private val draws = ConcurrentHashMap<String, Bucket>()
    private val skipped = ConcurrentHashMap<String, AtomicLong>()

    fun paint(popup: Boolean, copiedNs: Long, snapshotNs: Long, full: Boolean, rects: Array<Rectangle>) {
        if (popup) popupPaints.incrementAndGet()
        if (full) fullPaints.incrementAndGet()
        val dirtyPixels = rects.sumOf { it.width.toLong() * it.height }
        copy.add(copiedNs, extraValue = dirtyPixels)
        snapshot.add(snapshotNs)
    }

    /** A [window] drew frame [seq]: [drawNs] spent drawing, [latencyNs] since the paint arrived, [gap] frames skipped since its last draw. */
    fun draw(window: String, drawNs: Long, latencyNs: Long, gap: Long) {
        draws.getOrPut(window) { Bucket() }.add(drawNs, extraValue = latencyNs)
        if (gap > 0) skipped.getOrPut(window) { AtomicLong() }.addAndGet(gap)
    }

    fun start(label: String) {
        val os = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        val cores = Runtime.getRuntime().availableProcessors()
        thread(isDaemon = true, name = "osr-stats") {
            var lastCpu = os.processCpuTime
            var lastWall = System.nanoTime()
            while (true) {
                Thread.sleep(periodMs)
                val cpu = os.processCpuTime
                val wall = System.nanoTime()
                val cpuPct = 100.0 * (cpu - lastCpu) / (wall - lastWall)
                lastCpu = cpu
                lastWall = wall
                val secs = periodMs / 1000.0
                val c = copy.drain()
                val s = snapshot.drain()
                val line = buildString {
                    append("[$label] paints ${fmt(c[0] / secs)}/s")
                    append(" (full ${fullPaints.getAndSet(0)}, popup ${popupPaints.getAndSet(0)})")
                    append(" | copy avg ${ms(c[1], c[0])} max ${ms(c[2])} dirty ${mpx(c[3], c[0])}")
                    append(" | snapshot avg ${ms(s[1], s[0])} max ${ms(s[2])}")
                    draws.toSortedMap().forEach { (window, bucket) ->
                        val d = bucket.drain()
                        val dropped = skipped[window]?.getAndSet(0) ?: 0
                        append(" | $window ${fmt(d[0] / secs)}/s draw ${ms(d[1], d[0])} lat ${ms(d[3], d[0])} skip $dropped")
                    }
                    val rt = Runtime.getRuntime()
                    append(" | cpu ${fmt(cpuPct)}% of 1 core (${cores} cores)")
                    append(" heap ${(rt.totalMemory() - rt.freeMemory()) shr 20}MB")
                }
                println(line)
            }
        }
    }

    private fun fmt(v: Double) = "%.1f".format(v)
    private fun ms(totalNs: Long, count: Long = 1) = if (count == 0L) "-" else "%.2fms".format(totalNs / count / 1e6)
    private fun mpx(pixels: Long, count: Long) = if (count == 0L) "-" else "%.2fMpx".format(pixels.toDouble() / count / 1e6)
}
