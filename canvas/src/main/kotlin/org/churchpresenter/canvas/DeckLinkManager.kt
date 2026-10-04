package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.Scene
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.sharedui.utils.addGuardedShutdownHook
import org.churchpresenter.sharedui.utils.UsageEvent
import org.churchpresenter.sharedui.utils.UsageEvents
import org.churchpresenter.sharedui.composables.mode
import org.churchpresenter.app.churchpresenter.composables.DeckLinkManager as DeckLinkJni
import org.churchpresenter.canvas.DeckLinkManager.AudioFrame
import org.churchpresenter.canvas.DeckLinkManager.DeckLinkDevice
import org.churchpresenter.canvas.DeckLinkManager.DeviceStatus
import org.churchpresenter.canvas.DeckLinkManager.InputMode
import org.churchpresenter.canvas.DeckLinkManager.OutputInfo
import org.churchpresenter.canvas.DeckLinkManager.VideoConnection

private const val OPEN_RETRY_ATTEMPTS = 3
private const val OPEN_RETRY_DELAY_MS = 100L
private const val OUTPUT_INFO_FIELDS = 4
private const val OUTPUT_INFO_FPS_NUM = 2
private const val OUTPUT_INFO_FPS_DEN = 3
private const val DEVICE_STATUS_FIELDS = 3

/**
 * BlackMagic DeckLink, through the `decklink_jni` native library: several device outputs at once,
 * and inputs. Everything is optional -- without the library [isAvailable] is false and every other
 * call is a no-op. The logic is [DeckLinkBridge]'s, over the JNI entry points.
 */
object DeckLinkManager : DeckLinkBridge(DeckLinkJni, ::loadDeckLinkLibrary) {


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
}

/**
 * What [DeckLinkManager] does, over [natives] -- the JNI entry points in the app, a fake card in a
 * test -- and a library that [loadLibrary] loads once.
 *
 * Over detekt's function count on purpose: each JNI entry point has its wrapper here.
 */
@Suppress("TooManyFunctions")
open class DeckLinkBridge internal constructor(
    private val natives: DeckLinkNatives,
    private val loadLibrary: () -> Boolean,
) {
    private var available: Boolean? = null
    private val outputDevices: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val inputDevices: MutableSet<Int> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private var shutdownHookRegistered = false

    // ── Public API ──────────────────────────────────────────────────────

    fun isAvailable(): Boolean {
        if (available == null) available = loadLibrary()
        return available ?: false
    }

    fun listDevices(): List<DeckLinkDevice> {
        if (!isAvailable()) return emptyList()
        return try {
            natives.nativeListDevices().mapIndexed { index, name ->
                DeckLinkDevice(index, name)
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    fun open(deviceIndex: Int, width: Int = 1920, height: Int = 1080): Boolean {
        if (!isAvailable()) return false
        return try {
            val result = natives.nativeOpen(deviceIndex, width, height)
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
        addGuardedShutdownHook("decklink") {
            closeAllOutputs()
        }
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
                    natives.nativeSendFrame(deviceIndex, blackPixels, w, h)
                }
                Thread.sleep(OPEN_RETRY_DELAY_MS)
                natives.nativeClose(deviceIndex)
            } catch (_: Throwable) {}
        }
        outputDevices.clear()
    }

    fun getOutputInfo(deviceIndex: Int): OutputInfo? {
        if (!isAvailable()) return null
        return try {
            parseOutputInfo(natives.nativeGetOutputInfo(deviceIndex))
        } catch (_: Throwable) {
            null
        }
    }

    fun sendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            natives.nativeSendFrame(deviceIndex, pixels, width, height)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun startScheduledPlayback(deviceIndex: Int, fps: Double = 30.0): Boolean {
        if (!isAvailable()) return false
        return try {
            natives.nativeStartScheduledPlayback(deviceIndex, fps)
        } catch (_: Throwable) {
            false
        }
    }

    fun scheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        if (!isAvailable()) return
        try {
            natives.nativeScheduleFrame(deviceIndex, pixels, width, height)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun stopPlayback(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            natives.nativeStopPlayback(deviceIndex)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    fun close(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            natives.nativeClose(deviceIndex)
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
            parseInputModes(natives.nativeListInputModes(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    /** Whether [deviceIndex] can capture at all: an output-only card lists no input modes. */
    fun hasInput(deviceIndex: Int): Boolean = listInputModes(deviceIndex).isNotEmpty()

    fun listVideoConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            parseVideoConnections(natives.nativeListVideoConnections(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    fun openInput(deviceIndex: Int, mode: String = "", connection: Int = 0): Boolean {
        if (!isAvailable()) return false
        return try {
            val result = natives.nativeOpenInput(deviceIndex, mode, connection)
            if (result) inputDevices.add(deviceIndex)
            result
        } catch (_: Throwable) { false }
    }

    fun getInputFrame(deviceIndex: Int): IntArray? {
        if (!isAvailable()) return null
        return try {
            natives.nativeGetInputFrame(deviceIndex)
        } catch (_: Throwable) { null }
    }

    fun closeInput(deviceIndex: Int) {
        if (!isAvailable()) return
        try {
            natives.nativeCloseInput(deviceIndex)
            inputDevices.remove(deviceIndex)
        } catch (_: Throwable) {
            // silently ignore
        }
    }

    // ── Audio input API ────────────────────────────────────────────────

    fun enableAudioInput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            natives.nativeEnableAudioInput(deviceIndex, channels)
        } catch (_: Throwable) { false }
    }

    fun getInputAudio(deviceIndex: Int): AudioFrame? {
        if (!isAvailable()) return null
        return try {
            parseInputAudio(natives.nativeGetInputAudio(deviceIndex) ?: return null)
        } catch (_: Throwable) { null }
    }

    // ── Audio output API ───────────────────────────────────────────────

    fun enableAudioOutput(deviceIndex: Int, channels: Int = 2): Boolean {
        if (!isAvailable()) return false
        return try {
            natives.nativeEnableAudioOutput(deviceIndex, channels)
        } catch (_: Throwable) { false }
    }

    fun writeAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int {
        if (!isAvailable()) return 0
        return try {
            natives.nativeWriteAudioSamples(deviceIndex, samples, sampleFrameCount)
        } catch (_: Throwable) { 0 }
    }

    fun disableAudioOutput(deviceIndex: Int) {
        if (!isAvailable()) return
        try { natives.nativeDisableAudioOutput(deviceIndex) } catch (_: Throwable) {}
    }

    // ── Keyer API ──────────────────────────────────────────────────────

    /** Enable hardware keyer. isExternal=false for internal keying (overlay
     *  your graphics on the input signal), isExternal=true for external keying
     *  (use a separate key+fill signal). */
    fun enableKeyer(deviceIndex: Int, isExternal: Boolean = false): Boolean {
        if (!isAvailable()) return false
        return try {
            natives.nativeEnableKeyer(deviceIndex, isExternal)
        } catch (_: Throwable) { false }
    }

    /** Set keyer opacity level (0 = fully transparent, 255 = fully opaque). */
    fun setKeyerLevel(deviceIndex: Int, level: Int) {
        if (!isAvailable()) return
        try { natives.nativeSetKeyerLevel(deviceIndex, level) } catch (_: Throwable) {}
    }

    /** Smoothly ramp the keyer overlay up over the given number of frames. */
    fun keyerRampUp(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { natives.nativeKeyerRampUp(deviceIndex, frames) } catch (_: Throwable) {}
    }

    /** Smoothly ramp the keyer overlay down over the given number of frames. */
    fun keyerRampDown(deviceIndex: Int, frames: Int = 30) {
        if (!isAvailable()) return
        try { natives.nativeKeyerRampDown(deviceIndex, frames) } catch (_: Throwable) {}
    }

    fun disableKeyer(deviceIndex: Int) {
        if (!isAvailable()) return
        try { natives.nativeDisableKeyer(deviceIndex) } catch (_: Throwable) {}
    }

    // ── Output connection API ──────────────────────────────────────────

    fun setOutputConnection(deviceIndex: Int, connectionType: Int): Boolean {
        if (!isAvailable()) return false
        return try {
            natives.nativeSetOutputConnection(deviceIndex, connectionType)
        } catch (_: Throwable) { false }
    }

    fun listOutputConnections(deviceIndex: Int): List<VideoConnection> {
        if (!isAvailable()) return emptyList()
        return try {
            parseVideoConnections(natives.nativeListOutputConnections(deviceIndex))
        } catch (_: Throwable) { emptyList() }
    }

    // ── Status monitoring API ──────────────────────────────────────────

    fun getDeviceStatus(deviceIndex: Int): DeviceStatus? {
        if (!isAvailable()) return null
        return try {
            parseDeviceStatus(natives.nativeGetDeviceStatus(deviceIndex))
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

/** The `decklink_jni` entry points [DeckLinkBridge] calls, by concern. */
interface DeckLinkNatives : DeckLinkOutputNatives, DeckLinkInputNatives, DeckLinkAudioNatives, DeckLinkKeyerNatives

/** The output side: devices, frames, playback. */
interface DeckLinkOutputNatives {
    fun nativeListDevices(): Array<String>
    fun nativeOpen(deviceIndex: Int, width: Int, height: Int): Boolean
    fun nativeSendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    fun nativeStartScheduledPlayback(deviceIndex: Int, fps: Double): Boolean
    fun nativeScheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    fun nativeStopPlayback(deviceIndex: Int)
    fun nativeClose(deviceIndex: Int)
    fun nativeGetOutputInfo(deviceIndex: Int): IntArray
}

/** The input side: modes, connectors, frames, status. */
interface DeckLinkInputNatives {
    fun nativeListInputModes(deviceIndex: Int): Array<String>
    fun nativeListVideoConnections(deviceIndex: Int): Array<String>
    fun nativeOpenInput(deviceIndex: Int, mode: String, connection: Int): Boolean
    fun nativeGetInputFrame(deviceIndex: Int): IntArray?
    fun nativeCloseInput(deviceIndex: Int)
    fun nativeGetDeviceStatus(deviceIndex: Int): IntArray
}

/** Embedded audio, in and out. */
interface DeckLinkAudioNatives {
    fun nativeEnableAudioInput(deviceIndex: Int, channels: Int): Boolean
    fun nativeGetInputAudio(deviceIndex: Int): ShortArray?
    fun nativeEnableAudioOutput(deviceIndex: Int, channels: Int): Boolean
    fun nativeWriteAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int
    fun nativeDisableAudioOutput(deviceIndex: Int)
}

/** The hardware keyer and the output connector. */
interface DeckLinkKeyerNatives {
    fun nativeEnableKeyer(deviceIndex: Int, isExternal: Boolean): Boolean
    fun nativeSetKeyerLevel(deviceIndex: Int, level: Int)
    fun nativeKeyerRampUp(deviceIndex: Int, frames: Int)
    fun nativeKeyerRampDown(deviceIndex: Int, frames: Int)
    fun nativeDisableKeyer(deviceIndex: Int)
    fun nativeSetOutputConnection(deviceIndex: Int, connectionType: Int): Boolean
    fun nativeListOutputConnections(deviceIndex: Int): Array<String>
}

/** Loads `decklink_jni` from the app's resources, or from the library path; false when it is not there. */
internal fun loadDeckLinkLibrary(): Boolean = try {
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
