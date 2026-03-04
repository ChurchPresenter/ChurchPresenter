package org.churchpresenter.app.churchpresenter.composables

/**
 * Kotlin wrapper for BlackMagic DeckLink JNI native library.
 * All operations are optional — if the native library is not installed,
 * isAvailable() returns false and all other methods are no-ops.
 */
object DeckLinkManager {

    data class DeckLinkDevice(val index: Int, val name: String)

    private var available: Boolean? = null

    // ── JNI native methods ──────────────────────────────────────────────

    private external fun nativeListDevices(): Array<String>
    private external fun nativeOpen(deviceIndex: Int, width: Int, height: Int): Boolean
    private external fun nativeSendFrame(pixels: IntArray, width: Int, height: Int)
    private external fun nativeStartScheduledPlayback(deviceIndex: Int, fps: Double): Boolean
    private external fun nativeScheduleFrame(pixels: IntArray, width: Int, height: Int)
    private external fun nativeStopPlayback()
    private external fun nativeClose()

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Returns true if the native decklink_jni library is loaded and functional.
     */
    fun isAvailable(): Boolean {
        if (available == null) {
            available = try {
                System.loadLibrary("decklink_jni")
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
        return available!!
    }

    /**
     * Enumerates connected DeckLink devices.
     * Returns empty list if native lib is not available or no devices found.
     */
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

    /**
     * Opens a DeckLink device for output at the specified resolution.
     */
    fun open(deviceIndex: Int, width: Int = 1920, height: Int = 1080): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeOpen(deviceIndex, width, height)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends a single frame immediately to the opened DeckLink output.
     * Pixel format: BGRA IntArray (one int per pixel).
     */
    fun sendFrame(pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeSendFrame(pixels, width, height)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    /**
     * Starts scheduled playback on the given device at the specified FPS.
     * After calling this, use [scheduleFrame] to queue frames.
     */
    fun startScheduledPlayback(deviceIndex: Int, fps: Double = 30.0): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeStartScheduledPlayback(deviceIndex, fps)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Queues a frame for scheduled playback output.
     * Pixel format: BGRA IntArray (one int per pixel).
     */
    fun scheduleFrame(pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeScheduleFrame(pixels, width, height)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    /**
     * Stops scheduled playback.
     */
    fun stopPlayback() {
        if (!isAvailable()) return
        try {
            nativeStopPlayback()
        } catch (_: Exception) {
            // silently ignore
        }
    }

    /**
     * Closes the currently opened DeckLink device and releases resources.
     */
    fun close() {
        if (!isAvailable()) return
        try {
            nativeClose()
        } catch (_: Exception) {
            // silently ignore
        }
    }
}
