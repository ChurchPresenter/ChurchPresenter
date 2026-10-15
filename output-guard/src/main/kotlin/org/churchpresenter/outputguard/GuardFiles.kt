package org.churchpresenter.outputguard

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where the app and its guard meet: one folder holding the mapped [GuardState], a still of each
 * output, the command that relaunches the app, and the live state the relaunched app puts back.
 * Every file but the state is written whole to a temporary name and moved into place, so the other
 * side never reads half of one.
 */
class GuardFiles(val dir: File) {

    val state: File get() = File(dir, "state")

    /** What the relaunched app puts back on air, as the app wrote it. */
    val liveState: File get() = File(dir, "live-state.json")

    private val relaunch: File get() = File(dir, "relaunch.txt")

    private fun snapshot(id: Int) = File(dir, "output-$id.still")

    /** Stores the still of output [id]: [width] by [height] pixels, four bytes each, BGRA. */
    fun writeSnapshot(id: Int, width: Int, height: Int, bgra: ByteArray) = writeAtomically(snapshot(id)) { out ->
        out.writeInt(width)
        out.writeInt(height)
        out.write(bgra, 0, width * height * BYTES_PER_PIXEL)
    }

    /** The still of output [id], or null when there is none or it is unreadable. */
    fun readSnapshot(id: Int): BufferedImage? = runCatching {
        DataInputStream(snapshot(id).inputStream().buffered()).use { input ->
            val width = input.readInt()
            val height = input.readInt()
            require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS)
            val bytes = ByteArray(width * height * BYTES_PER_PIXEL).also(input::readFully)
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            // BGRA bytes read little-endian are ARGB ints: what the image holds.
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                .get((image.raster.dataBuffer as DataBufferInt).data)
            image
        }
    }.getOrNull()

    /** Forgets the still of output [id]. */
    fun deleteSnapshot(id: Int) {
        snapshot(id).delete()
    }

    /** Stores how to start the app again: the program and its arguments, one per line. */
    fun writeRelaunchCommand(command: List<String>) = writeText(relaunch, command.joinToString("\n"))

    /** How to start the app again, or empty when the app never said. */
    fun readRelaunchCommand(): List<String> =
        runCatching { relaunch.readLines().filter { it.isNotEmpty() } }.getOrDefault(emptyList())

    /** Stores what is on air, for a relaunched app to put back. */
    fun writeLiveState(text: String) = writeText(liveState, text)

    private fun writeText(file: File, text: String) = writeAtomically(file) { it.write(text.toByteArray()) }

    private fun writeAtomically(file: File, write: (DataOutputStream) -> Unit) {
        dir.mkdirs()
        val temp = File(dir, "${file.name}.tmp")
        DataOutputStream(temp.outputStream().buffered()).use(write)
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private const val BYTES_PER_PIXEL = 4

        /** An 8K still; anything larger is not a still this app wrote. */
        private const val MAX_PIXELS = 7680L * 4320L

        /** The folder for the app running as this user: beside its settings and crash reports. */
        fun forUser(): GuardFiles = GuardFiles(File(System.getProperty("user.home"), ".churchpresenter/output-guard"))
    }
}
