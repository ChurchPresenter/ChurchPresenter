package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.churchpresenter.app.churchpresenter.utils.WindowsWindowCapture
import org.churchpresenter.ui.WindowInfo
import org.churchpresenter.ui.readCommandOutput
import org.churchpresenter.ui.CommandRunner

private const val HEX_RADIX = 16
private const val WINDOW_LINE_FIELDS = 4

internal fun listOpenWindows(): List<WindowInfo> =
    openWindowsFor(System.getProperty("os.name", "").lowercase(), ::readCommandOutput)

internal fun openWindowsFor(osName: String, run: CommandRunner): List<WindowInfo> = when {
    osName.contains("linux") -> linuxWindowsFrom(run)
    osName.contains("win") -> listWindowsWindows()
    osName.contains("mac") -> macWindowsFrom(run)
    else -> emptyList()
}

internal fun linuxWindowsFrom(run: CommandRunner): List<WindowInfo> {
    val windowIds = parseXpropWindowIds(
        run(listOf("xprop", "-root", "_NET_CLIENT_LIST_STACKING"), 0L).output
    )
    if (windowIds.isNotEmpty()) {
        val windows = windowIds
            .mapNotNull { wid -> xpropWindow(wid, run(listOf("xprop", "-id", wid, "_NET_WM_NAME"), 0L).output) }
            .filter { it.title.isNotBlank() }
        if (windows.isNotEmpty()) return windows
    }

    val wmctrl = run(listOf("wmctrl", "-l"), 0L)
    if (wmctrl.exitCode == 0) {
        val windows = parseWmctrlWindows(wmctrl.output)
        if (windows.isNotEmpty()) return windows
    }

    return emptyList()
}

internal fun parseXpropWindowIds(output: String): List<String> =
    Regex("0x[0-9a-fA-F]+").findAll(output).map { it.value }.toList()

internal fun xpropWindow(windowId: String, nameOutput: String): WindowInfo? {
    val name = Regex("\"(.+)\"").find(nameOutput)?.groupValues?.get(1)
    if (name.isNullOrBlank()) return null
    return WindowInfo(name, windowId.removePrefix("0x").toLongOrNull(HEX_RADIX) ?: 0L)
}

internal fun parseWmctrlWindows(output: String): List<WindowInfo> =
    output.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(Regex("\\s+"), limit = 4)
            if (parts.size >= WINDOW_LINE_FIELDS) {
                val id = parts[0].removePrefix("0x").toLongOrNull(HEX_RADIX) ?: 0L
                val title = parts[3]
                if (title.isNotBlank()) WindowInfo(title, id) else null
            } else null
        }

private fun listWindowsWindows(): List<WindowInfo> {
    return try {
        WindowsWindowCapture.listWindows().map { WindowInfo(it.title, it.hwnd) }
    } catch (_: Exception) { emptyList() }
}

private val MAC_WINDOW_TITLES_SCRIPT = """
    tell application "System Events"
        set windowList to {}
        repeat with proc in (every process whose visible is true)
            repeat with win in (every window of proc)
                set end of windowList to (name of win)
            end repeat
        end repeat
        return windowList
    end tell
""".trimIndent()

internal fun macWindowsFrom(run: CommandRunner): List<WindowInfo> =
    parseMacWindowTitles(run(listOf("osascript", "-e", MAC_WINDOW_TITLES_SCRIPT), 0L).output)

internal fun parseMacWindowTitles(output: String): List<WindowInfo> =
    output.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { WindowInfo(it, 0L) }
