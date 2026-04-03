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

    data class DeviceStatus(
        val signalLocked: Boolean,
        val busy: Int,
        val detectedModeCode: Int
    )

    data class AudioFrame(
        val sampleFrames: Int,
        val channels: Int,
        val samples: ShortArray
    )

    private var available: Boolean? = null
    private val outputDevices = mutableSetOf<Int>()
    private val inputDevices = mutableSetOf<Int>()
    private var shutdownHookRegistered = false

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

    // Audio input
    private external fun nativeEnableAudioInput(deviceIndex: Int, channels: Int): Boolean
    private external fun nativeGetInputAudio(deviceIndex: Int): ShortArray?

    // Audio output
    private external fun nativeEnableAudioOutput(deviceIndex: Int, channels: Int): Boolean
    private external fun nativeWriteAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int
    private external fun nativeDisableAudioOutput(deviceIndex: Int)

    // Keyer
    private external fun nativeEnableKeyer(deviceIndex: Int, isExternal: Boolean): Boolean
    private external fun nativeSetKeyerLevel(deviceIndex: Int, level: Int)
    private external fun nativeKeyerRampUp(deviceIndex: Int, frames: Int)
    private external fun nativeKeyerRampDown(deviceIndex: Int, frames: Int)
    private external fun nativeDisableKeyer(deviceIndex: Int)

    // Output connection
    private external fun nativeSetOutputConnection(deviceIndex: Int, connectionType: Int): Boolean
    private external fun nativeListOutputConnections(deviceIndex: Int): Array<String>

    // Status
    private external fun nativeGetDeviceStatus(deviceIndex: Int): IntArray

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
            val result = nativeOpen(deviceIndex, width, height)
            if (result) {
                outputDevices.add(deviceIndex)
                registerShutdownHook()
            }
            result
        } catch (_: Exception) {
            false
        }
    }

    private fun registerShutdownHook() {
        if (shutdownHookRegistered) return
        shutdownHookRegistered = true
        Runtime.getRuntime().addShutdownHook(Thread {
            closeAllOutputs()
        })
    }

    /** Send black frames and close all open DeckLink outputs. */
    fun closeAllOutputs() {
        for (deviceIndex in outputDevices.toSet()) {
            try {
                val info = getOutputInfo(deviceIndex)
                val w = info?.width ?: 1920
                val h = info?.height ?: 1080
                val blackPixels = IntArray(w * h)
                repeat(3) {
                    nativeSendFrame(deviceIndex, blackPixels, w, h)
                }
                Thread.sleep(100)
                nativeClose(deviceIndex)
            } catch (_: Exception) {}
        }
        outputDevices.clear()
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
            outputDevices.remove(deviceIndex)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    /** Check if a device is currently open for output. */
    fun isOutputActive(deviceIndex: Int): Boolean = deviceIndex in outputDevices

    /** Check if a device is currently open for input. */
    fun isInputActive(deviceIndex: Int): Boolean = deviceIndex in inputDevices

    /** Check if a device is configured for input in any scene (not just currently running). */
    fun isInputConfigured(deviceIndex: Int, scenes: List<org.churchpresenter.app.churchpresenter.models.Scene> = emptyList()): Boolean {
        // Check provided scenes list
        if (scenes.any { scene ->
            scene.sources.any { source ->
                source is org.churchpresenter.app.churchpresenter.models.SceneSource.CameraSource &&
                    source.isDeckLink && source.deckLinkIndex == deviceIndex
            }
        }) return true

        // Also check saved scenes file as fallback
        return try {
            val appDataDir = System.getProperty("user.home") + "/.churchpresenter"
            val scenesFile = java.io.File(appDataDir, "scenes.json")
            if (scenesFile.exists()) {
                val json = scenesFile.readText()
                // Simple check: look for deckLinkIndex matching in the JSON
                json.contains("\"deckLinkIndex\":$deviceIndex") || json.contains("\"deckLinkIndex\": $deviceIndex")
            } else false
        } catch (_: Exception) { false }
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
            val result = nativeOpenInput(deviceIndex, mode, connection)
            if (result) inputDevices.add(deviceIndex)
            result
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
            inputDevices.remove(deviceIndex)
        } catch (_: Exception) {
            // silently ignore
        }
    }

    // ── Audio input API ────────────────────────────────────────────────

    fun enableAudioInput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableAudioInput(deviceIndex, channels)
        } catch (_: Exception) { false }
    }

    fun getInputAudio(deviceIndex: Int): AudioFrame? {
        if (!isAvailable()) return null
        return try {
            val data = nativeGetInputAudio(deviceIndex) ?: return null
            if (data.size < 2) return null
            val sampleFrames = data[0].toInt()
            val channels = data[1].toInt()
            if (sampleFrames <= 0 || channels <= 0) return null
            val samples = data.copyOfRange(2, 2 + sampleFrames * channels)
            AudioFrame(sampleFrames, channels, samples)
        } catch (_: Exception) { null }
    }

    // ── Audio output API ───────────────────────────────────────────────

    fun enableAudioOutput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableAudioOutput(deviceIndex, channels)
        } catch (_: Exception) { false }
    }

    fun writeAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int {
        if (!isAvailable()) return 0
        return try {
            nativeWriteAudioSamples(deviceIndex, samples, sampleFrameCount)
        } catch (_: Exception) { 0 }
    }

    fun disableAudioOutput(deviceIndex: Int) {
        if (!isAvailable()) return
        try { nativeDisableAudioOutput(deviceIndex) } catch (_: Exception) {}
    }

    // ── Keyer API ──────────────────────────────────────────────────────

    /** Enable hardware keyer. isExternal=false for internal keying (overlay
     *  your graphics on the input signal), isExternal=true for external keying
     *  (use a separate key+fill signal). */
    fun enableKeyer(deviceIndex: Int, isExternal: Boolean = false): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableKeyer(deviceIndex, isExternal)
        } catch (_: Exception) { false }
    }

    /** Set keyer opacity level (0 = fully transparent, 255 = fully opaque). */
    fun setKeyerLevel(deviceIndex: Int, level: Int) {
        if (!isAvailable()) return
        try { nativeSetKeyerLevel(deviceIndex, level) } catch (_: Exception) {}
    }

    /** Smoothly ramp the keyer overlay up over the given number of frames. */
    fun keyerRampUp(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { nativeKeyerRampUp(deviceIndex, frames) } catch (_: Exception) {}
    }

    /** Smoothly ramp the keyer overlay down over the given number of frames. */
    fun keyerRampDown(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { nativeKeyerRampDown(deviceIndex, frames) } catch (_: Exception) {}
    }

    fun disableKeyer(deviceIndex: Int) {
        if (!isAvailable()) return
        try { nativeDisableKeyer(deviceIndex) } catch (_: Exception) {}
    }

    // ── Output connection API ──────────────────────────────────────────

    fun setOutputConnection(deviceIndex: Int, connectionType: Int): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeSetOutputConnection(deviceIndex, connectionType)
        } catch (_: Exception) { false }
    }

    fun listOutputConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            nativeListOutputConnections(deviceIndex).map { encoded ->
                val parts = encoded.split("|", limit = 2)
                VideoConnection(
                    name = parts.getOrElse(0) { encoded },
                    value = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    // ── Status monitoring API ──────────────────────────────────────────

    fun getDeviceStatus(deviceIndex: Int): DeviceStatus? {
        if (!isAvailable()) return null
        return try {
            val data = nativeGetDeviceStatus(deviceIndex)
            if (data.size >= 3) {
                DeviceStatus(
                    signalLocked = data[0] != 0,
                    busy = data[1],
                    detectedModeCode = data[2]
                )
            } else null
        } catch (_: Exception) { null }
    }
}
