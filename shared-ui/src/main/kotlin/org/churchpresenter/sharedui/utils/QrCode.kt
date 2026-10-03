package org.churchpresenter.sharedui.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage

/**
 * Renders [content] as a square QR code [size] pixels wide, or null when ZXing cannot encode it
 * (empty content, a non-positive size), so a bad URL never takes down the screen drawing it.
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
    } catch (_: WriterException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
