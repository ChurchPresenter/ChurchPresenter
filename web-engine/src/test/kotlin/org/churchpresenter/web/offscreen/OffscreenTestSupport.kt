package org.churchpresenter.web.offscreen

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.random.Random

/** A direct buffer of [width]×[height] pixels as Chromium hands one over, filled from [seed]. */
internal fun paintBuffer(width: Int, height: Int, seed: Int = 1): ByteBuffer {
    val bytes = ByteArray(width * height * BYTES_PER_PIXEL).also { Random(seed).nextBytes(it) }
    return ByteBuffer.allocateDirect(bytes.size).put(bytes).rewind()
}

/** Every byte of [buffer], from its start. */
internal fun ByteBuffer.allBytes(): ByteArray = ByteArray(capacity()).also { get(0, it) }

/** The image's pixels as stored, row after row, in its own colour type. */
internal fun Image.bytes(): ByteArray {
    val bitmap = Bitmap()
    bitmap.allocPixels(imageInfo)
    check(readPixels(bitmap)) { "the image could not be read back" }
    return checkNotNull(bitmap.readPixels(bitmap.imageInfo, width * BYTES_PER_PIXEL, 0, 0)).also { bitmap.close() }
}

/** A pool of [COPY_THREADS] - 1 threads, as the frame store's copies use; shut down by [use]. */
internal fun <T> withPool(block: (ExecutorService) -> T): T {
    val pool = Executors.newFixedThreadPool(COPY_THREADS - 1)
    return try {
        block(pool)
    } finally {
        pool.shutdown()
    }
}

/** A page on a stub client that is never started natively, reporting into [sent]; see [stubOffscreenPage]. */
internal fun testPage(sent: MutableList<OffscreenReport> = mutableListOf()): OffscreenPage =
    stubOffscreenPage { sent += it as OffscreenReport }

/** Hands [page] a full paint of [width]×[height], as Chromium would. */
internal fun paint(page: OffscreenPage, width: Int, height: Int, seed: Int = 1) = paintStub(page, width, height, seed)
