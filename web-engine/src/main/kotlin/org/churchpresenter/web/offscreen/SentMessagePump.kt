package org.churchpresenter.web.offscreen

import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * On Windows, has the AWT event thread deliver messages other threads have *sent* (not posted) to
 * the windows it owns, every [PUMP_MS].
 *
 * With windowless rendering, JCEF runs Chromium's message loop from the AWT event thread, so
 * Chromium's own windows (its IME window among them) belong to it. That thread parks in Java between
 * events and never looks at its Win32 queue, so a `SendMessage` to one of those windows from another
 * thread (a language switch reaching the IME window, sent from AWT's toolkit thread) waits forever,
 * and every window hangs. `PeekMessage` with `PM_QS_SENDMESSAGE` delivers exactly those pending sent
 * messages and nothing else, leaving the queue to whoever reads it.
 */
internal object SentMessagePump {
    private var started = false

    /** Starts the pump once, on Windows only; anywhere else this does nothing. */
    fun start(os: String = System.getProperty("os.name")) {
        if (!os.startsWith("Windows")) return
        SwingUtilities.invokeLater {
            if (started) return@invokeLater
            started = true
            val msg = WinUser.MSG()
            Timer(PUMP_MS) { User32.INSTANCE.PeekMessage(msg, null, 0, 0, PM_NOREMOVE or PM_QS_SENDMESSAGE) }.start()
        }
    }

    private const val PUMP_MS = 5

    /** `PeekMessage` flags: leave posted messages in the queue, and handle only sent ones. */
    private const val PM_NOREMOVE = 0x0000
    private const val PM_QS_SENDMESSAGE = 0x0040 shl 16
}
