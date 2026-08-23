package org.churchpresenter.companionserver

/**
 * The port a suite in this module should bind, given the [base] it was written against.
 *
 * Server suites bind a fixed port rather than port 0 so a failure names a stable address in the log.
 * These suites used to live in `:composeApp`, where `PerForkTestHome` shifted every port by the
 * fork's own band — four bands of 1,000 starting at the base. This module's `test` task is a
 * separate JVM that can be running at the same time as those forks, so it takes a band of its own,
 * clear of all four. Change [MODULE_PORT_BAND] and it must stay clear of them.
 */
fun testPort(base: Int): Int = base + MODULE_PORT_BAND

/** Past `PerForkTestHome`'s four 1,000-wide fork bands. */
const val MODULE_PORT_BAND = 5_000

/**
 * Pins [InstanceLinkLogger]'s log directory against the real `user.home` before a test swaps it.
 *
 * The logger resolves its directory in a `by lazy`, so it keeps whatever `user.home` pointed at the
 * *first* time anything logged, for the rest of the JVM. A class that swaps `user.home` to a temp
 * dir and then exercises code that logs latches the logger onto that dir; teardown deletes it and
 * every later write in the JVM fails silently, in a class that did nothing wrong. Call this as the
 * first line of `@BeforeTest`, before the swap. Idempotent, one appended line per JVM.
 *
 * `:composeApp`'s `TestSingletons.latchToTestHome` is the same latch for the app's own suite, which
 * pins skiko as well; this is the half that concerns this module.
 */
object LogHomeLatch {
    @Volatile private var latched = false

    fun latch() {
        if (latched) return
        synchronized(this) {
            if (latched) return
            InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, "test_home_latch")
            latched = true
        }
    }
}

/**
 * A [LottieFrameRenderer] that answers without Skia: each frame is a per-pixel pattern that depends
 * on both the pixel and the progress it was asked for.
 *
 * The real renderer composes offscreen with Compose, which this module deliberately cannot do. What
 * the cache tests are about is the file format written around the frames — the header, the RLE, the
 * footer offsets — so frames that are *distinguishable* per progress value are enough.
 *
 * **The pattern has to vary within a frame, not only between frames.** A flat fill RLE-compresses
 * to a handful of bytes, and an ATEM upload of a handful of bytes is not a transfer the switcher
 * ever performs — `CompanionServerAtemUploadTest` asserts on the payload size for exactly that
 * reason, and a uniform fake makes it fail with "the rendered frame is only 0 bytes".
 */
class FakeLottieFrameRenderer : LottieFrameRenderer {
    /** Every progress value the cache asked for, in order. */
    val renderedProgress = mutableListOf<Float>()

    override suspend fun withSession(
        width: Int,
        height: Int,
        lottieJson: String,
        initialProgress: Float,
        block: suspend (renderFrame: suspend (Float) -> IntArray) -> Unit,
    ) {
        val buffer = IntArray(width * height)
        block { progress ->
            renderedProgress += progress
            val shift = (progress.coerceIn(0f, 1f) * 0xFF).toInt()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    buffer[y * width + x] = pixelAt(x, y, shift)
                }
            }
            buffer
        }
    }

    companion object {
        /** Opaque, and different from its neighbours, so a frame never compresses away to nothing. */
        fun pixelAt(x: Int, y: Int, shift: Int): Int {
            val r = (x + shift) and 0xFF
            val g = (y + shift) and 0xFF
            val b = (x * y + shift) and 0xFF
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
