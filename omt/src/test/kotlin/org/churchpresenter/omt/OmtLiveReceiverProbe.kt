package org.churchpresenter.omt

import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull

/** Looks for the source over this many seconds, one discovery look a second. */
private const val DISCOVERY_SECONDS = 20

/** Frames to read once connected, each waiting up to the receiver's own timeout. */
private const val FRAMES = 30

/**
 * An **opt-in manual probe** that receives a running OMT source and says what arrived — the other
 * half of checking an OMT output: the app sends, this receives, from outside the app.
 *
 * ```
 * ./gradlew :omt:test -PomtHardware=true -PomtSource='Verify' --tests '*OmtLiveReceiverProbe*'
 * ```
 *
 * `-PomtSource` is matched by containment against discovered names, so the output's own name is
 * enough; an `omt://host:port` address is used as given. It prints the size, how many frames came,
 * and whether they carried alpha — which is the thing an Alpha-mode output exists to deliver.
 */
class OmtLiveReceiverProbe {

    private val enabled = System.getProperty("churchpresenter.omtHardware") == "true"
    private val wanted = System.getProperty("churchpresenter.omtSource").orEmpty()

    @Test
    fun `receive a named source and report what arrived`() {
        if (!enabled || wanted.isBlank()) return
        val dir = System.getProperty("churchpresenter.omtLibrary").orEmpty().ifBlank {
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val osDir = if (os.contains("mac")) "macos" else if (os.contains("win")) "windows" else "linux"
            File("../composeApp/src/jvmMain/appResources/$osDir/omt").absolutePath
        }
        val lib = assertNotNull(JnaOmtLibrary.load(assertNotNull(OmtRuntime.detect(customPath = dir))))
        lib.setLoggingFilename(null)
        val address = if (wanted.startsWith("omt://")) {
            wanted
        } else {
            val discovery = OmtDiscovery(lib)
            var found: String? = null
            repeat(DISCOVERY_SECONDS) {
                if (found == null) {
                    found = discovery.sources().find { wanted in it }
                    if (found == null) Thread.sleep(1_000)
                }
            }
            assertNotNull(found, "'$wanted' was not discovered; saw ${discovery.sources()}")
        }
        println("connecting to $address")
        // One frame straight off the library first, for the flag itself: an opaque background drawn
        // by an Alpha output is still sent flagged as alpha, which the pixels alone cannot show.
        val raw = lib.recvCreate(address, preview = false)
        val flagged = (0 until FRAMES).firstNotNullOfOrNull { lib.recvCaptureVideo(raw, 500) }?.alpha
        lib.recvDestroy(raw)
        println("frames flagged as carrying alpha: $flagged")
        val receiver = OmtReceiver(lib, address)
        check(receiver.open()) { "the library refused a receiver for $address" }
        try {
            var frames = 0
            var translucent = 0
            var size = ""
            repeat(FRAMES) {
                val frame = receiver.receive(timeoutMs = 500) ?: return@repeat
                frames++
                size = "${frame.width}x${frame.height}"
                val count = frame.width * frame.height
                if ((0 until count).any { (frame.pixels[it] ushr ALPHA_SHIFT) < BYTE_MASK }) translucent++
            }
            println("received $frames frame(s) of $size; $translucent carried transparency")
        } finally {
            receiver.close()
        }
    }
}
