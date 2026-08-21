package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.models.scene.Scene
import org.churchpresenter.app.churchpresenter.models.scene.SceneSource
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents

private const val OPEN_RETRY_ATTEMPTS = 3
private const val OPEN_RETRY_DELAY_MS = 100L
private const val OUTPUT_INFO_FIELDS = 4
private const val OUTPUT_INFO_FPS_NUM = 2
private const val OUTPUT_INFO_FPS_DEN = 3
private const val DEVICE_STATUS_FIELDS = 3

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
    private val outputDevices: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val inputDevices: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
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
        } catch (_: Throwable) {
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
                UsageEvents.recordOncePerRun(UsageEvent.DECKLINK_OUTPUT)
            }
            result
        } catch (_: Throwable) {
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
                repeat(OPEN_RETRY_ATTEMPTS) {
                    nativeSendFrame(deviceIndex, blackPixels, w, h)
                }
                Thread.sleep(OPEN_RETRY_DELAY_MS)
                nativeClose(deviceIndex)
            } catch (_: Throwable) {}
        }
        outputDevices.clear()
    }

    fun getOutputInfo(deviceIndex: Int): OutputInfo? {
        if (!isAvailable()) return null
        return try {
            parseOutputInfo(nativeGetOutputInfo(deviceIndex))
        } catch (_: Throwable) {
            null
        }
    }

    fun sendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeSendFrame(deviceIndex, pixels, width, height)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun startScheduledPlayback(deviceIndex: Int, fps: Double = 30.0): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeStartScheduledPlayback(deviceIndex, fps)
        } catch (_: Throwable) {
            false
        }
    }

    fun scheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            nativeScheduleFrame(deviceIndex, pixels, width, height)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun stopPlayback(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeStopPlayback(deviceIndex)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun close(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeClose(deviceIndex)
            outputDevices.remove(deviceIndex)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    /** Check if a device is currently open for output. */
    fun isOutputActive(deviceIndex: Int): Boolean = deviceIndex in outputDevices

    /** Check if a device is currently open for input. */
    fun isInputActive(deviceIndex: Int): Boolean = deviceIndex in inputDevices

    /** Check if a device is configured for input in any scene (not just currently running). */
    fun isInputConfigured(deviceIndex: Int, scenes: List<Scene> = emptyList()): Boolean {
        // Check provided scenes list
        if (scenes.any { scene ->
            scene.sources.any { source ->
                source is SceneSource.CameraSource &&
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
            parseInputModes(nativeListInputModes(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    fun listVideoConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            parseVideoConnections(nativeListVideoConnections(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    fun openInput(deviceIndex: Int, mode: String = "", connection: Int = 0): Boolean {
        if (!isAvailable()) return false
        return try {
            val result = nativeOpenInput(deviceIndex, mode, connection)
            if (result) inputDevices.add(deviceIndex)
            result
        } catch (_: Throwable) { false }
    }

    fun getInputFrame(deviceIndex: Int): IntArray? {
        if (!isAvailable()) return null
        return try {
            nativeGetInputFrame(deviceIndex)
        } catch (_: Throwable) { null }
    }

    fun closeInput(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            nativeCloseInput(deviceIndex)
            inputDevices.remove(deviceIndex)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    // ── Audio input API ────────────────────────────────────────────────

    fun enableAudioInput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableAudioInput(deviceIndex, channels)
        } catch (_: Throwable) { false }
    }

    fun getInputAudio(deviceIndex: Int): AudioFrame? {
        if (!isAvailable()) return null
        return try {
            parseInputAudio(nativeGetInputAudio(deviceIndex) ?: return null)
        } catch (_: Throwable) { null }
    }

    // ── Audio output API ───────────────────────────────────────────────

    fun enableAudioOutput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableAudioOutput(deviceIndex, channels)
        } catch (_: Throwable) { false }
    }

    fun writeAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int {
        if (!isAvailable()) return 0
        return try {
            nativeWriteAudioSamples(deviceIndex, samples, sampleFrameCount)
        } catch (_: Throwable) { 0 }
    }

    fun disableAudioOutput(deviceIndex: Int) {
        if (!isAvailable()) return
        try { nativeDisableAudioOutput(deviceIndex) } catch (_: Throwable) {}
    }

    // ── Keyer API ──────────────────────────────────────────────────────

    /** Enable hardware keyer. isExternal=false for internal keying (overlay
     *  your graphics on the input signal), isExternal=true for external keying
     *  (use a separate key+fill signal). */
    fun enableKeyer(deviceIndex: Int, isExternal: Boolean = false): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeEnableKeyer(deviceIndex, isExternal)
        } catch (_: Throwable) { false }
    }

    /** Set keyer opacity level (0 = fully transparent, 255 = fully opaque). */
    fun setKeyerLevel(deviceIndex: Int, level: Int) {
        if (!isAvailable()) return
        try { nativeSetKeyerLevel(deviceIndex, level) } catch (_: Throwable) {}
    }

    /** Smoothly ramp the keyer overlay up over the given number of frames. */
    fun keyerRampUp(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { nativeKeyerRampUp(deviceIndex, frames) } catch (_: Throwable) {}
    }

    /** Smoothly ramp the keyer overlay down over the given number of frames. */
    fun keyerRampDown(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { nativeKeyerRampDown(deviceIndex, frames) } catch (_: Throwable) {}
    }

    fun disableKeyer(deviceIndex: Int) {
        if (!isAvailable()) return
        try { nativeDisableKeyer(deviceIndex) } catch (_: Throwable) {}
    }

    // ── Output connection API ──────────────────────────────────────────

    fun setOutputConnection(deviceIndex: Int, connectionType: Int): Boolean {
        if (!isAvailable()) return false
        return try {
            nativeSetOutputConnection(deviceIndex, connectionType)
        } catch (_: Throwable) { false }
    }

    fun listOutputConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            parseVideoConnections(nativeListOutputConnections(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    // ── Status monitoring API ──────────────────────────────────────────

    fun getDeviceStatus(deviceIndex: Int): DeviceStatus? {
        if (!isAvailable()) return null
        return try {
            parseDeviceStatus(nativeGetDeviceStatus(deviceIndex))
        } catch (_: Throwable) { null }
    }

    // ── Internal parsing helpers (separated from native calls for testability) ──

    internal fun parseInputModes(rawArray: Array<String>): List<InputMode> =
        rawArray.map { encoded ->
            val parts = encoded.split("|", limit = 2)
            InputMode(
                name = parts.getOrElse(0) { encoded },
                encodedValue = parts.getOrElse(1) { "" }
            )
        }

    internal fun parseVideoConnections(rawArray: Array<String>): List<VideoConnection> =
        rawArray.map { encoded ->
            val parts = encoded.split("|", limit = 2)
            VideoConnection(
                name = parts.getOrElse(0) { encoded },
                value = parts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            )
        }

    internal fun parseOutputInfo(rawArray: IntArray): OutputInfo? =
        if (rawArray.size >= OUTPUT_INFO_FIELDS && rawArray[0] > 0 && rawArray[1] > 0) {
            OutputInfo(rawArray[0], rawArray[1], rawArray[OUTPUT_INFO_FPS_NUM], rawArray[OUTPUT_INFO_FPS_DEN])
        } else null

    internal fun parseDeviceStatus(rawArray: IntArray): DeviceStatus? =
        if (rawArray.size >= DEVICE_STATUS_FIELDS) {
            DeviceStatus(
                signalLocked = rawArray[0] != 0,
                busy = rawArray[1],
                detectedModeCode = rawArray[2]
            )
        } else null

    internal fun parseInputAudio(data: ShortArray): AudioFrame? {
        if (data.size < 2) return null
        val sampleFrames = data[0].toInt()
        val channels = data[1].toInt()
        if (sampleFrames <= 0 || channels <= 0) return null
        val samples = data.copyOfRange(2, 2 + sampleFrames * channels)
        return AudioFrame(sampleFrames, channels, samples)
    }
}
