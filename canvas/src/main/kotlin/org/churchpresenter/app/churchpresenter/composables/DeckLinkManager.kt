package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.canvas.DeckLinkNatives

/**
 * The DeckLink JNI entry points, and nothing else -- the logic is `org.churchpresenter.canvas.DeckLinkManager`.
 *
 * The native library (`native/decklink`) binds these by this class's fully qualified name, so the
 * class keeps the package and name the library was built against. Moving or renaming it, or making
 * these functions `internal` (whose JVM names Kotlin mangles), unbinds every call.
 */
@Suppress("TooManyFunctions") // JNI binds every native to this one class; they cannot be split.
internal object DeckLinkManager : DeckLinkNatives {
    external override fun nativeListDevices(): Array<String>
    external override fun nativeOpen(deviceIndex: Int, width: Int, height: Int): Boolean
    external override fun nativeSendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    external override fun nativeStartScheduledPlayback(deviceIndex: Int, fps: Double): Boolean
    external override fun nativeScheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int)
    external override fun nativeStopPlayback(deviceIndex: Int)
    external override fun nativeClose(deviceIndex: Int)
    external override fun nativeGetOutputInfo(deviceIndex: Int): IntArray
    external override fun nativeListInputModes(deviceIndex: Int): Array<String>
    external override fun nativeListVideoConnections(deviceIndex: Int): Array<String>
    external override fun nativeOpenInput(deviceIndex: Int, mode: String, connection: Int): Boolean
    external override fun nativeGetInputFrame(deviceIndex: Int): IntArray?
    external override fun nativeCloseInput(deviceIndex: Int)
    external override fun nativeEnableAudioInput(deviceIndex: Int, channels: Int): Boolean
    external override fun nativeGetInputAudio(deviceIndex: Int): ShortArray?
    external override fun nativeEnableAudioOutput(deviceIndex: Int, channels: Int): Boolean
    external override fun nativeWriteAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int): Int
    external override fun nativeDisableAudioOutput(deviceIndex: Int)
    external override fun nativeEnableKeyer(deviceIndex: Int, isExternal: Boolean): Boolean
    external override fun nativeSetKeyerLevel(deviceIndex: Int, level: Int)
    external override fun nativeKeyerRampUp(deviceIndex: Int, frames: Int)
    external override fun nativeKeyerRampDown(deviceIndex: Int, frames: Int)
    external override fun nativeDisableKeyer(deviceIndex: Int)
    external override fun nativeSetOutputConnection(deviceIndex: Int, connectionType: Int): Boolean
    external override fun nativeListOutputConnections(deviceIndex: Int): Array<String>
    external override fun nativeGetDeviceStatus(deviceIndex: Int): IntArray
}
