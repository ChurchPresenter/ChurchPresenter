package org.churchpresenter.settings

import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Writes settings files off the caller's thread, which is almost always the event thread: encoding
 * the document and stripping what the profiles own takes seconds on a slow machine with many
 * profiles, and the window froze for all of it on every change.
 *
 * Only the newest document for a file is written: a save that arrives while an older one is still
 * waiting replaces it. [flush] waits until what was saved for a file is on disk, and every reader
 * of a settings file calls it first, so a load straight after a save -- by this instance or a fresh
 * one -- reads what was saved. A shutdown hook flushes everything, so `exitProcess` and a normal
 * quit lose nothing.
 *
 * Process-wide rather than per [SettingsManager], because the app makes several managers over the
 * same file.
 */
internal class SettingsWriter(private val executor: Executor) {
    private val lock = Object()

    /** The document still to be written for each file, by absolute path. */
    private val pending = HashMap<String, () -> Unit>()

    /** Files whose write is running now, so [flush] also waits for one already taken from [pending]. */
    private val running = HashSet<String>()

    /** Saves [write]'s output for [file] later; replaces whatever was still waiting for it. */
    fun submit(file: File, write: () -> Unit) {
        val key = file.absolutePath
        val first = synchronized(lock) {
            val wasIdle = key !in pending
            pending[key] = write
            wasIdle
        }
        if (first) executor.execute { drain(key) }
    }

    /** Blocks until nothing saved for [file] is still waiting or being written. */
    fun flush(file: File) {
        val key = file.absolutePath
        // Run what is waiting on this thread rather than wait for the writer to reach it.
        drain(key)
        synchronized(lock) {
            while (key in running || key in pending) lock.wait()
        }
    }

    /** Writes every file that has something waiting. */
    fun flushAll() {
        val keys = synchronized(lock) { pending.keys.toList() }
        keys.forEach { flush(File(it)) }
    }

    private fun drain(key: String) {
        while (true) {
            val write = synchronized(lock) {
                if (key in running) return
                pending.remove(key)?.also { running += key } ?: return
            }
            try {
                write()
            } finally {
                synchronized(lock) {
                    running -= key
                    lock.notifyAll()
                }
            }
        }
    }

    companion object {
        /** The app's one writer: a single daemon thread, flushed by a shutdown hook. */
        val shared: SettingsWriter by lazy {
            val writer = SettingsWriter(
                Executors.newSingleThreadExecutor { task -> Thread(task, "settings-writer").apply { isDaemon = true } },
            )
            Runtime.getRuntime().addShutdownHook(Thread({ writer.flushAll() }, "settings-writer-flush"))
            writer
        }
    }
}
