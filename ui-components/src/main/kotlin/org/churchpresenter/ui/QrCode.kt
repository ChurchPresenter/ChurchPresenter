package org.churchpresenter.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * A square QR code carrying [content], [size] pixels on a side, or `null` when it cannot be drawn.
 *
 * Null rather than an exception because every caller is drawing a screen: the Q&A join code, the
 * presentation remote's code and the canvas QR source all have something else to show when the
 * content is empty or too long for the chosen size, and none of them can usefully handle a throw
 * from inside a composition.
 *
 * [foregroundArgb]/[backgroundArgb] are packed ARGB ints rather than `Color` so the pixels go
 * straight into the [BufferedImage] without a conversion per pixel.
 */
fun generateQRCodeBitmap(
    content: String,
    size: Int,
    foregroundArgb: Int = 0xFF000000.toInt(),
    backgroundArgb: Int = 0xFFFFFFFF.toInt(),
): ImageBitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until size) {
            for (y in 0 until size) {
                image.setRGB(x, y, if (bitMatrix.get(x, y)) foregroundArgb else backgroundArgb)
            }
        }
        image.toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
