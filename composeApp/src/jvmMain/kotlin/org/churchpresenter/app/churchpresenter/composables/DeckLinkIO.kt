package org.churchpresenter.app.churchpresenter.composables

/**
 * Kotlin wrapper for BlackMagic DeckLink JNI native library.
 * Supports multiple simultaneous device outputs.
 * All operations are optional — if the native library is not installed,
 * isAvailable() returns false and all other methods are no-ops.
 */
object DeckLinkManager {

    data class DeckLinkDevice(val index: Int, val name: String)

    data class OutputInfo(val width: Int, val height: Int, val fpsNumerator: Int, val fpsDenominator: Int) {
        val fps: Double get() = if (fpsDenominator > 0) fpsNumerator.toDouble() / fpsDenominator else 30.0
    }

    data class InputMode(val name: String, val encodedValue: String)
    data class VideoConnection(val name: String, val value: Int)

    private var available: Boolean? = null

    // ── JNI native methods ──────────────────────────────────────────────

    private external fun nativeListDevices(): Array<String>
    private external fun nativeOpen(deviceIndex: Int, width: Int, height: Int): Boolean
    private external fun nativeSendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    private external fun nativeStartScheduledPlayback(deviceIndex: Int, fps: Double): Boolean
    private external fun nativeScheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    private external fun nativeStopPlayback(deviceIndex: Int)
    private external fun nativeClose(deviceIndex: Int)
    private external fun nativeGetOutputInfo(deviceIndex: Int): IntArray

    // Input capture
    private external fun nativeListInputModes(deviceIndex: Int): Array<String>
    private external fun nativeListVideoConnections(deviceIndex: Int): Array<String>
    private external fun nativeOpenInput(deviceIndex: Int, mode: String, connection: Int): Boolean
    private external fun nativeGetInputFrame(deviceIndex: Int): IntArray?
    private external fun nativeCloseInput(deviceIndex: Int)

    // ── Public API ──────────────────────────────────────────────────────

    fun isAvailable(): Boolean {
        if (available == null) {
            available = try {
                val resDir = System.getProperty("compose.application.resources.dir")
                val libName = when {
                    System.getProperty("os.name").lowercase().contains("win") -> "decklink_jni.dll"
                    System.getProperty("os.name").lowercase().contains("mac") -> "libdecklink_jni.dylib"
                    else -> "libdecklink_jni.so"
                }
                val libFile = resDir?.let { java.io.File(it, libName) }
                if (libFile != null && libFile.exists()) {
                    System.load(libFile.absolutePath)
                } else {
                    System.loadLibrary("decklink_jni")
                }
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
        return available ?: false
    }

    fun listDevices(): List<DeckLinkDevice> {
        if (!isAvailable()) return emptyList()
        return try {
            nativeListDevices().mapIndexed { index, name ->
                DeckLinkDevice(index, name)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun open(deviceIndex: Int, width: Int = 1920, height: Int = 1080): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeOpen(deviceIndex, width, height)
        } catch (_: Exception) {
            false
        }
    }

    fun getOutputInfo(deviceIndex: Int): OutputInfo? {
        if (!isAvailable()) return null
        return try {
            val info = nativeGetOutputInfo(deviceIndex)
            if (info.size >= 4 && info[0] > 0 && info[1] > 0) {
                OutputInfo(info[0], info[1], info[2], info[3])
            } else null
        } catch (_: Throwable) {
            null
        }
    }

    fun sendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeSendFrame(deviceIndex, pixels, width, height)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    fun startScheduledPlayback(deviceIndex: Int, fps: Double = 30.0): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeStartScheduledPlayback(deviceIndex, fps)
        } catch (_: Exception) {
            false
        }
    }

    fun scheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeScheduleFrame(deviceIndex, pixels, width, height)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    fun stopPlayback(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeStopPlayback(deviceIndex)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    fun close(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeClose(deviceIndex)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    // ── Input capture API ───────────────────────────────────────────────

    fun listInputModes(deviceIndex: Int): List<InputMode> {
        if (!isAvailable()) return emptyList()
        return try {
            nativeListInputModes(deviceIndex).map { encoded ->
                val parts = encoded.split("|", limit = 2)
                InputMode(
                    name = parts.getOrElse(0) { encoded },
                    encodedValue = parts.getOrElse(1) { "" }
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun listVideoConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            nativeListVideoConnections(deviceIndex).map { encoded ->
                val parts = encoded.split("|", limit = 2)
                VideoConnection(
                    name = parts.getOrElse(0) { encoded },
                    value = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun openInput(deviceIndex: Int, mode: String = "", connection: Int = 0): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeOpenInput(deviceIndex, mode, connection)
        } catch (_: Exception) { false }
    }

    fun getInputFrame(deviceIndex: Int): IntArray? {
        if (!isAvailable()) return null
        return try {
            nativeGetInputFrame(deviceIndex)
        } catch (_: Exception) { null }
    }

    fun closeInput(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeCloseInput(deviceIndex)
        } catch (_: Exception) {
            // silently ignore
        }
    }
}
