package org.churchpresenter.app.churchpresenter

import com.sun.jna.Library
import com.sun.jna.Native

/**
 * Turns on Xlib's own locking before anything opens the X display — on Linux, the first thing
 * [main] does.
 *
 * `XInitThreads` must be the first Xlib call a process makes. The embedded browser makes it
 * (`CefApp.startup`, "on Linux this initializes Xlib multithreading"), but only once startup reaches
 * `CefManager.init`, after AWT has opened the display and built its resource database without
 * locks. From then on Xlib locks every call, and the first one that reaches that database locks a
 * mutex that was never made: a segfault in `pthread_mutex_lock` before the first window opens.
 * libX11 1.8 and later calls `XInitThreads` from `XOpenDisplay` itself, which is why only older
 * releases (Ubuntu 22.04's 1.7.5) crash. Called here first, the browser's later call is a no-op.
 *
 * @param initThreads the native call; returns zero when Xlib could not set up its locking.
 * @return whether the call was made and succeeded.
 */
internal fun initXlibThreads(
    osName: String = System.getProperty("os.name", ""),
    initThreads: () -> Int = { Native.load("X11", Xlib::class.java).XInitThreads() },
): Boolean {
    if (!osName.lowercase().contains("linux")) return false
    // No libX11 (a Wayland-only or headless machine) means nothing opens an X display to race.
    return runCatching { initThreads() != 0 }.getOrDefault(false)
}

/** PascalCase because `Native.load` binds each function to the libX11 symbol of the same name. */
@Suppress("FunctionNaming")
private interface Xlib : Library {
    fun XInitThreads(): Int
}
