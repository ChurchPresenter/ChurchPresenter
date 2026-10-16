package org.churchpresenter.canvas

import java.util.Collections
import java.util.concurrent.Executor

/** A 1x1 input frame, and a status that reads as locked, not busy, mode 42. */
private val INPUT_FRAME = intArrayOf(1, 1, 7)
private val DEVICE_STATUS = intArrayOf(1, 0, 42)

/**
 * A card that records what it was asked and answers from what the test set. Safe to call from the
 * output thread, a capture loop and the test at once.
 *
 * [calls] is every call in order; [events] is the output side only -- `open`, `content`, `black`,
 * `close`, each with its device -- so a test can read what the card would have shown. [onOpen] and
 * [onSettle] run inside an open and inside a close's wait for its black frames, for a test to hold
 * either there.
 */
class FakeDeckLinkCard : DeckLinkNatives {
    val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

    @Volatile var opens = true

    @Volatile var outputInfo = intArrayOf(1280, 720, 60000, 1001)

    @Volatile var inputAudio: ShortArray? = shortArrayOf(2, 2, 1, 2, 3, 4)

    @Volatile var fails = false

    @Volatile var onOpen: (Int) -> Unit = {}

    @Volatile var onSettle: () -> Unit = {}

    /** [events] at this moment, safe to iterate while the card is still being called. */
    fun eventsNow(): List<String> = synchronized(events) { events.toList() }

    private fun <T> answer(call: String, value: T): T {
        calls += call
        if (fails) error("the card went away")
        return value
    }

    override fun nativeListDevices() = answer("list", arrayOf("Mini Recorder", "Duo 2"))
    override fun nativeOpen(deviceIndex: Int, width: Int, height: Int): Boolean {
        onOpen(deviceIndex)
        return answer("open $deviceIndex ${width}x$height", opens).also { if (it) events += "open $deviceIndex" }
    }
    override fun nativeSendFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) {
        answer("send $deviceIndex ${width}x$height", Unit)
        events += if (pixels.all { it == 0 }) "black $deviceIndex" else "content $deviceIndex"
    }
    override fun nativeStartScheduledPlayback(deviceIndex: Int, fps: Double) =
        answer("start $deviceIndex $fps", true)
    override fun nativeScheduleFrame(deviceIndex: Int, pixels: IntArray, width: Int, height: Int) =
        answer("schedule $deviceIndex", Unit)
    override fun nativeStopPlayback(deviceIndex: Int) = answer("stop $deviceIndex", Unit)
    override fun nativeClose(deviceIndex: Int) {
        answer("close $deviceIndex", Unit)
        events += "close $deviceIndex"
    }
    override fun nativeGetOutputInfo(deviceIndex: Int) = answer("info $deviceIndex", outputInfo)
    override fun nativeListInputModes(deviceIndex: Int) = answer("modes $deviceIndex", arrayOf("1080p30|Hp30"))
    override fun nativeListVideoConnections(deviceIndex: Int) =
        answer("conns $deviceIndex", arrayOf("SDI|1", "HDMI|2"))
    override fun nativeOpenInput(deviceIndex: Int, mode: String, connection: Int) =
        answer("openInput $deviceIndex $mode $connection", opens)
    override fun nativeGetInputFrame(deviceIndex: Int) = answer("frame $deviceIndex", INPUT_FRAME)
    override fun nativeCloseInput(deviceIndex: Int) = answer("closeInput $deviceIndex", Unit)
    override fun nativeGetDeviceStatus(deviceIndex: Int) = answer("status $deviceIndex", DEVICE_STATUS)
    override fun nativeEnableAudioInput(deviceIndex: Int, channels: Int) =
        answer("audioIn $deviceIndex $channels", true)
    override fun nativeGetInputAudio(deviceIndex: Int) = answer("audio $deviceIndex", inputAudio)
    override fun nativeEnableAudioOutput(deviceIndex: Int, channels: Int) =
        answer("audioOut $deviceIndex $channels", true)
    override fun nativeWriteAudioSamples(deviceIndex: Int, samples: ShortArray, sampleFrameCount: Int) =
        answer("write $deviceIndex $sampleFrameCount", sampleFrameCount)
    override fun nativeDisableAudioOutput(deviceIndex: Int) = answer("audioOff $deviceIndex", Unit)
    override fun nativeEnableKeyer(deviceIndex: Int, isExternal: Boolean) =
        answer("keyer $deviceIndex $isExternal", true)
    override fun nativeSetKeyerLevel(deviceIndex: Int, level: Int) = answer("level $deviceIndex $level", Unit)
    override fun nativeKeyerRampUp(deviceIndex: Int, frames: Int) = answer("up $deviceIndex $frames", Unit)
    override fun nativeKeyerRampDown(deviceIndex: Int, frames: Int) = answer("down $deviceIndex $frames", Unit)
    override fun nativeDisableKeyer(deviceIndex: Int) = answer("keyerOff $deviceIndex", Unit)
    override fun nativeSetOutputConnection(deviceIndex: Int, connectionType: Int) =
        answer("outConn $deviceIndex $connectionType", true)
    override fun nativeListOutputConnections(deviceIndex: Int) = answer("outConns $deviceIndex", arrayOf("SDI|1"))
}

/** A bridge over [card] whose library is always there, and whose wait for black frames is [card]'s `onSettle`. */
fun bridgeOver(card: FakeDeckLinkCard): DeckLinkBridge = DeckLinkBridge(card, { true }, { card.onSettle() })

/** Outputs over [bridge], opened and closed on [worker]. */
fun outputsOver(bridge: DeckLinkBridge, worker: Executor): DeckLinkOutputSessions =
    DeckLinkOutputSessions(bridge, worker)
