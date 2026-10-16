package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** One output the app has on screen: an id the app gave its window, where it is, and its last still. */
data class GuardedOutput(val id: Int, val bounds: Rectangle, val snapshotSeq: Long)

/**
 * What the app tells the guard, in a small memory-mapped file both processes keep open: the app's
 * pid, a heartbeat from a thread of its own, a tick from the event thread, a few flags, and the
 * outputs it has on screen. Mapped rather than written, so a write is a store into memory -- never a
 * file the guard waits on -- and neither side blocks the other.
 *
 * A field is written whole and read whole. A read racing a write may see a timestamp a moment old,
 * which nothing here minds: the guard acts on seconds, not milliseconds.
 */
class GuardState private constructor(private val buffer: ByteBuffer) {

    var appPid: Long
        get() = buffer.getLong(PID)
        set(value) = put { buffer.putLong(PID, value) }

    /** When the app's heartbeat thread last wrote, in epoch milliseconds. */
    var heartbeatAt: Long
        get() = buffer.getLong(HEARTBEAT)
        set(value) = put { buffer.putLong(HEARTBEAT, value) }

    /** When the app's event thread last answered a tick, in epoch milliseconds. */
    var uiTickAt: Long
        get() = buffer.getLong(UI_TICK)
        set(value) = put { buffer.putLong(UI_TICK, value) }

    /** Whether any output shows content. */
    var live: Boolean
        get() = flag(LIVE)
        set(value) = setFlag(LIVE, value)

    /** The app is quitting on purpose: the guard leaves with it and shows nothing. */
    var closing: Boolean
        get() = flag(CLOSING)
        set(value) = setFlag(CLOSING, value)

    /** The app's outputs are on screen, so a guard that relaunched it can leave. */
    var outputsUp: Boolean
        get() = flag(OUTPUTS_UP)
        set(value) = setFlag(OUTPUTS_UP, value)

    /** Whether the guard should cover a hung UI with its still, not only a dead one. */
    var takeOverOnHang: Boolean
        get() = flag(TAKE_OVER_ON_HANG)
        set(value) = setFlag(TAKE_OVER_ON_HANG, value)

    /** The outputs on screen, at most [MAX_OUTPUTS]. */
    var outputs: List<GuardedOutput>
        get() = (0 until buffer.getInt(OUTPUT_COUNT).coerceIn(0, MAX_OUTPUTS)).map { i ->
            val at = SLOTS + i * SLOT_SIZE
            GuardedOutput(
                id = buffer.getInt(at),
                bounds = Rectangle(
                    buffer.getInt(at + X),
                    buffer.getInt(at + Y),
                    buffer.getInt(at + W),
                    buffer.getInt(at + H),
                ),
                snapshotSeq = buffer.getLong(at + SEQ),
            )
        }
        set(value) = put {
            val kept = value.take(MAX_OUTPUTS)
            kept.forEachIndexed { i, output ->
                val at = SLOTS + i * SLOT_SIZE
                buffer.putInt(at, output.id)
                buffer.putInt(at + X, output.bounds.x)
                buffer.putInt(at + Y, output.bounds.y)
                buffer.putInt(at + W, output.bounds.width)
                buffer.putInt(at + H, output.bounds.height)
                buffer.putLong(at + SEQ, output.snapshotSeq)
            }
            buffer.putInt(OUTPUT_COUNT, kept.size)
        }

    /** Starts the file over for the app with [pid]: no flags, no outputs, its own heartbeat. */
    fun reset(pid: Long, now: Long) = put {
        buffer.putInt(MAGIC_AT, MAGIC)
        buffer.putInt(VERSION_AT, VERSION)
        buffer.putLong(PID, pid)
        buffer.putLong(HEARTBEAT, now)
        buffer.putLong(UI_TICK, now)
        buffer.putInt(FLAGS, 0)
        buffer.putInt(OUTPUT_COUNT, 0)
    }

    /** Whether the file holds this version's layout -- it was written by an app of this build. */
    val isCurrent: Boolean get() = buffer.getInt(MAGIC_AT) == MAGIC && buffer.getInt(VERSION_AT) == VERSION

    private fun flag(bit: Int) = buffer.getInt(FLAGS) and bit != 0

    private fun setFlag(bit: Int, on: Boolean) = put {
        val flags = buffer.getInt(FLAGS)
        buffer.putInt(FLAGS, if (on) flags or bit else flags and bit.inv())
    }

    private inline fun put(write: () -> Unit) = synchronized(this) { write() }

    companion object {
        /** The most outputs one app reports; a projector rig with more is not one this was built for. */
        const val MAX_OUTPUTS = 16

        private const val MAGIC = 0x43504744 // "CPGD"
        private const val VERSION = 1
        private const val MAGIC_AT = 0
        private const val VERSION_AT = 4
        private const val PID = 8
        private const val HEARTBEAT = 16
        private const val UI_TICK = 24
        private const val FLAGS = 32
        private const val OUTPUT_COUNT = 36
        private const val SLOTS = 40
        private const val SLOT_SIZE = 32
        private const val X = 4
        private const val Y = 8
        private const val W = 12
        private const val H = 16
        private const val SEQ = 24
        private const val SIZE = SLOTS + MAX_OUTPUTS * SLOT_SIZE

        private const val LIVE = 1
        private const val CLOSING = 2
        private const val OUTPUTS_UP = 4
        private const val TAKE_OVER_ON_HANG = 8

        /** The state in [file], mapped; created at its full size if it is not there yet. */
        fun open(file: File): GuardState {
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                if (raf.length() < SIZE) raf.setLength(SIZE.toLong())
                val mapped = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, SIZE.toLong())
                return GuardState(mapped.order(ByteOrder.LITTLE_ENDIAN))
            }
        }

        /** A state held in memory only, for a test that drives both sides at once. */
        fun inMemory(): GuardState = GuardState(ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN))
    }
}
