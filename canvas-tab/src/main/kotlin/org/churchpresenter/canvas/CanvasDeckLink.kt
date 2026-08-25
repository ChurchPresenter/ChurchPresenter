package org.churchpresenter.canvas

/**
 * The Blackmagic capture card, as the Canvas tab uses it.
 *
 * A scene can take a DeckLink input as a source, so the tab has to enumerate what a card offers and
 * then pull frames off it. The app's `DeckLinkManager` does far more than that — it also drives the
 * *output* side, the media pool and the keyer, and nine other files depend on it — so this is the
 * slice the canvas needs and nothing else.
 *
 * [None] reports no card, which is what a machine without one has and what every test uses: there is
 * no way to fake a capture card, and asserting against a stub that returns pixels would prove only
 * that the stub was called.
 */
interface CanvasDeckLink {

    /** Whether the DeckLink driver loaded at all. False on a machine with no card or no SDK. */
    fun isAvailable(): Boolean

    /** Whether [deviceIndex] is already busy sending a program feed, so it cannot also capture. */
    fun isOutputActive(deviceIndex: Int): Boolean

    /** The video modes [deviceIndex] can capture in. */
    fun listInputModes(deviceIndex: Int): List<InputMode>

    /** The physical inputs on [deviceIndex] — SDI, HDMI and so on. */
    fun listVideoConnections(deviceIndex: Int): List<VideoConnection>

    /** Starts capture. Returns false when the device is missing or already in use. */
    fun openInput(deviceIndex: Int, mode: String, connection: Int): Boolean

    /** The most recent frame as ARGB pixels, or null when none has arrived yet. */
    fun getInputFrame(deviceIndex: Int): IntArray?

    /** Stops capture and releases the device. */
    fun closeInput(deviceIndex: Int)

    /** One video mode a card can capture in. [encodedValue] is what the driver is given back. */
    data class InputMode(val name: String, val encodedValue: String)

    /** One physical input on a card. [value] is the driver's own constant for it. */
    data class VideoConnection(val name: String, val value: Int)

    companion object {
        /** No card present. */
        val None: CanvasDeckLink = object : CanvasDeckLink {
            override fun isAvailable() = false
            override fun isOutputActive(deviceIndex: Int) = false
            override fun listInputModes(deviceIndex: Int) = emptyList<InputMode>()
            override fun listVideoConnections(deviceIndex: Int) = emptyList<VideoConnection>()
            override fun openInput(deviceIndex: Int, mode: String, connection: Int) = false
            override fun getInputFrame(deviceIndex: Int): IntArray? = null
            override fun closeInput(deviceIndex: Int) = Unit
        }
    }
}

/**
 * The card the canvas talks to.
 *
 * A composition local rather than a parameter because the two places that need it — the source
 * editor and the frame cache — sit at opposite ends of the tab, and threading it through every
 * layer between them would touch a dozen signatures for one device.
 */
val LocalCanvasDeckLink = androidx.compose.runtime.staticCompositionLocalOf { CanvasDeckLink.None }
