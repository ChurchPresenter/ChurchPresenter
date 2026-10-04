package org.churchpresenter.canvas


import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.awt.Rectangle

import org.churchpresenter.sharedui.utils.CommandRunner
import org.churchpresenter.sharedui.utils.readCommandOutput

internal fun findWindowBounds(windowTitle: String): Rectangle? =
    windowBoundsFor(System.getProperty("os.name", "").lowercase(), windowTitle, ::readCommandOutput)

/**
 * Where the window called [windowTitle] currently sits, or `null` when no window by that name is
 * open — which is the ordinary case after the operator picks a window and then closes it.
 *
 * [osName] is a parameter rather than read from `os.name` here so a test can ask for each platform's
 * lookup without swapping the system property, which skiko latches JVM-wide.
 */
fun windowBoundsFor(osName: String, windowTitle: String, run: CommandRunner): Rectangle? {
    return try {
        when {
            osName.contains("linux") -> linuxWindowBoundsFrom(windowTitle, run)
            osName.contains("win") -> findWindowsWindowBounds(windowTitle)
            osName.contains("mac") -> macWindowBoundsFrom(windowTitle, run)
            else -> null
        }
    } catch (_: Exception) { null }
}

/**
 * Where [title]'s window sits on X11, found by walking the root stacking list with `xprop` and asking
 * `xwininfo` about the one whose `_NET_WM_NAME` matches exactly.
 *
 * The match is exact rather than a prefix or contains: the operator picked this title out of a list
 * built the same way, and two windows of an application routinely differ only by a suffix. A window
 * whose geometry comes back with a zero width or height is skipped rather than returned, so the walk
 * continues to the next candidate — a mapped-but-unrealised window reports exactly that.
 */
fun linuxWindowBoundsFrom(title: String, run: CommandRunner): Rectangle? {
    val listOutput = run(listOf("xprop", "-root", "_NET_CLIENT_LIST_STACKING"), 0L).output
    val windowIds = Regex("0x[0-9a-fA-F]+").findAll(listOutput).map { it.value }.toList()

    for (wid in windowIds) {
        val nameOutput = run(listOf("xprop", "-id", wid, "_NET_WM_NAME"), 0L).output
        val name = Regex("\"(.+)\"").find(nameOutput)?.groupValues?.get(1)
        if (name != title) continue

        val bounds = parseXwininfoBounds(run(listOf("xwininfo", "-id", wid), 0L).output)
        if (bounds != null) return bounds
    }
    return null
}

/**
 * The geometry in one `xwininfo -id` report, whose interesting lines are four labelled integers among
 * a page of other properties. "Absolute" is the position on the screen rather than within the parent,
 * which is what a capture needs.
 *
 * `null` when the report carries no usable size, so the caller keeps looking rather than capturing an
 * empty rectangle.
 */
fun parseXwininfoBounds(output: String): Rectangle? {
    var x = 0; var y = 0; var w = 0; var h = 0
    for (line in output.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("Absolute upper-left X:") -> x = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Absolute upper-left Y:") -> y = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Width:") -> w = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
            trimmed.startsWith("Height:") -> h = trimmed.substringAfter(":").trim().toIntOrNull() ?: 0
        }
    }
    return if (w > 0 && h > 0) Rectangle(x, y, w, h) else null
}

private fun findWindowsWindowBounds(title: String): Rectangle? {
    return try {
        // Find the window by title using JNA EnumWindows
        val windows = WindowsWindowCapture.listWindows()
        val win = windows.find { it.title == title }
        if (win != null) {
            WindowsWindowCapture.getWindowBounds(win.hwnd)
        } else null
    } catch (_: Exception) { null }
}

/** The AppleScript that returns `x,y,w,h` for the first visible window named [title]. */
internal fun macWindowBoundsScript(title: String): String = """
    tell application "System Events"
        repeat with proc in (every process whose visible is true)
            repeat with win in (every window of proc)
                if name of win is "$title" then
                    set {x, y} to position of win
                    set {w, h} to size of win
                    return "" & x & "," & y & "," & w & "," & h
                end if
            end repeat
        end repeat
    end tell
""".trimIndent()

/**
 * Where [title]'s window sits on macOS, which only System Events can answer.
 *
 * The script returns the four numbers on one comma-separated line, and anything else means no window
 * matched — an empty answer, or an error message osascript wrote to the stream instead.
 */
fun macWindowBoundsFrom(title: String, run: CommandRunner): Rectangle? {
    val output = run(listOf("osascript", "-e", macWindowBoundsScript(title)), 0L).output.trim()
    val parts = output.split(",").mapNotNull { it.trim().toIntOrNull() }
    return if (parts.size == WINDOW_BOUNDS_FIELDS &&
        parts[BOUNDS_WIDTH_INDEX] > 0 && parts[BOUNDS_HEIGHT_INDEX] > 0
    ) {
        Rectangle(parts[0], parts[1], parts[BOUNDS_WIDTH_INDEX], parts[BOUNDS_HEIGHT_INDEX])
    } else null
}

private const val WINDOW_BOUNDS_FIELDS = 4
private const val BOUNDS_WIDTH_INDEX = 2
private const val BOUNDS_HEIGHT_INDEX = 3
