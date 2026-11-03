package org.churchpresenter.diagnostics

import java.io.File

/**
 * A run that ended without closing and without a Java crash on record: the JVM died under the app
 * (a native crash in a driver or library), Windows or the user ended the process, or the power went.
 * None of those reach Sentry while they happen, so the next start reports them once, with the JVM's
 * own crash file (`hs_err_pid*.log`) when it left one.
 */
internal object UncleanExit {
    /** The event's title: constant, so every such exit groups on what its tags and extras say. */
    const val TITLE = "Previous run ended without closing"

    /** How the run ended, as the `exit.kind` tag: a JVM crash file was found, or nothing was left. */
    const val NATIVE_CRASH = "native_crash"
    const val NO_CRASH_FILE = "no_crash_file"

    private const val PREFIX = "hs_err_pid"
    private const val MAX_FRAMES = 12
    private const val MAX_FILE_BYTES = 2L * 1024 * 1024

    /**
     * The newest JVM crash file in [folders] written at or after [sinceMs], the previous run's start;
     * null when there is none. The JVM writes it to the working folder, or to the temp folder when it
     * cannot.
     */
    fun newestCrashFile(folders: List<File>, sinceMs: Long): File? = folders.asSequence()
        .flatMap { folder -> folder.listFiles().orEmpty().asSequence() }
        .filter { it.isFile && it.name.startsWith(PREFIX) && it.name.endsWith(".log") && it.lastModified() >= sinceMs }
        .maxByOrNull { it.lastModified() }

    /** [file]'s text, or null when it cannot be read or is too large to be a crash file. */
    fun read(file: File): String? = try {
        file.takeIf { it.length() <= MAX_FILE_BYTES }?.readText()
    } catch (_: Exception) {
        null
    }

    /**
     * What a JVM crash file says, for the event's extras: the error line, the problematic frame,
     * the thread it happened on, and the first native and Java frames. Missing parts are left out.
     */
    fun summary(text: String): Map<String, String> {
        val lines = text.lines()
        val header = lines.takeWhile { it.startsWith("#") }.map { it.removePrefix("#").trim() }
            .filter { it.isNotEmpty() }
        val error = header.firstOrNull { line ->
            line != "A fatal error has been detected by the Java Runtime Environment:" &&
                !line.startsWith("JRE version") && !line.startsWith("Java VM")
        }
        val frameAt = header.indexOf("Problematic frame:")
        val problematic = header.getOrNull(frameAt + 1)?.takeIf { frameAt >= 0 }
        val thread = lines.firstOrNull { it.startsWith("Current thread") }?.trim()
        return buildMap {
            error?.let { put("crash.error", it) }
            problematic?.let { put("crash.problematic_frame", it) }
            thread?.let { put("crash.thread", it) }
            frames(lines, "Native frames:")?.let { put("crash.native_frames", it) }
            frames(lines, "Java frames:")?.let { put("crash.java_frames", it) }
        }
    }

    /**
     * The library the problematic frame is in, such as `amdxx64.dll` or `libjvm.so`, for a tag that
     * says at a glance whether a driver, the JVM or a library of ours crashed; null when it names none.
     */
    fun module(problematicFrame: String?): String? =
        problematicFrame?.let { Regex("""\[([^+\]]+)""").find(it)?.groupValues?.get(1)?.trim() }

    /** The first [MAX_FRAMES] lines under [heading], up to the blank line that ends the list. */
    private fun frames(lines: List<String>, heading: String): String? {
        val start = lines.indexOfFirst { it.startsWith(heading) }
        if (start < 0) return null
        return lines.drop(start + 1).takeWhile { it.isNotBlank() }.take(MAX_FRAMES)
            .joinToString("\n").takeIf { it.isNotEmpty() }
    }
}
