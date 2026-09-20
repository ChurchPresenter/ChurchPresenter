package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.composables.FfmpegBinary
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * How long a video or audio file runs, in whole seconds — asked of ffmpeg, which the app already
 * ships for cameras.
 *
 * A planner needs this before anything is played: a run of show says a clip takes 1:23, the engine
 * books its end action from it, and the clock times of every row after it depend on it. The
 * alternative — "runs its own length" — leaves the engine with no number at all, so a row set that
 * way could never hand on to the next one.
 *
 * Reading the container's header is all this costs; ffmpeg prints the duration and exits with an
 * error because no output file was given, which is expected and ignored. Runs a process, so call
 * it off the UI thread.
 */
fun mediaDurationSeconds(path: String, ffmpeg: String = FfmpegBinary.path): Int? {
    if (!File(path).isFile) return null
    val output = runCatching {
        val process = ProcessBuilder(ffmpeg, "-hide_banner", "-i", path)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        process.destroy()
        text
    }.getOrNull() ?: return null
    return parseFfmpegDuration(output)
}

/**
 * The `Duration: 00:01:23.45` line of ffmpeg's report, as whole seconds, rounded up.
 *
 * Rounded up because a 1:23.45 clip occupies 1:24 of a service, and a run of show that says 1:23
 * would cut it off. `N/A` — a stream, a file ffmpeg cannot seek — gives null rather than zero: no
 * duration and a duration of nothing are different things to a plan.
 */
internal fun parseFfmpegDuration(output: String): Int? {
    val parts = DURATION.find(output)?.groupValues ?: return null
    val whole = parts[HOURS].toInt() * SECONDS_PER_HOUR +
        parts[MINUTES].toInt() * SECONDS_PER_MINUTE +
        parts[SECONDS].toInt()
    return if (parts[FRACTION].toInt() > 0) whole + 1 else whole
}

private const val HOURS = 1
private const val MINUTES = 2
private const val SECONDS = 3
private const val FRACTION = 4
private val DURATION = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2})\.(\d+)""")
private const val PROBE_TIMEOUT_SECONDS = 10L
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

/**
 * How long a slideshow of [count] items takes at [intervalSeconds] each -- 20 pictures at 5s is
 * 1:40.
 *
 * Null for an empty folder or a nonsense interval, which is not the same as zero: a row with no
 * length shows blank in the run of show and is left for the operator, while a zero would claim the
 * slideshow takes no time and push every clock time after it wrong.
 */
fun slideshowSeconds(count: Int, intervalSeconds: Float): Int? {
    if (count <= 0 || intervalSeconds <= 0f) return null
    return (count * intervalSeconds).toInt().coerceAtLeast(1)
}
