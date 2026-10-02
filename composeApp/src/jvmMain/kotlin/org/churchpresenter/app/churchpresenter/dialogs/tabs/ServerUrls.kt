package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage
import org.churchpresenter.settings.ServerSettings
import java.net.URLEncoder

/** The API key to append to companion URLs, or blank when protection is off or no key is set. */
internal fun effectiveApiKey(server: ServerSettings): String =
    if (server.apiKeyEnabled && server.apiKey.isNotBlank()) server.apiKey else ""

/**
 * The `?a=b&c=d` tail shared by the ATEM URLs: [extra] first if present, then the API key if there
 * is one. Returns an empty string when there is nothing to add, so callers can append it blindly.
 */
internal fun apiQueryString(extra: String = "", apiKey: String = ""): String {
    val params = buildList {
        if (extra.isNotEmpty()) add(extra)
        if (apiKey.isNotEmpty()) add("apiKey=" + URLEncoder.encode(apiKey, "UTF-8"))
    }
    return if (params.isEmpty()) "" else "?" + params.joinToString("&")
}

/** A lower-third name as it appears in a URL path — spaces as `%20` rather than `+`. */
internal fun encodeUrlPathSegment(name: String): String =
    URLEncoder.encode(name, "UTF-8").replace("+", "%20")

/**
 * Encodes [content] as a QR bitmap, or null when it cannot be encoded.
 *
 * Null rather than an exception is the point: this is called from inside the connection dialog's
 * composition, so a throw would take the dialog down instead of merely leaving it without a code —
 * and ZXing does throw on content it cannot represent, an empty string included.
 *
 * Error correction is left at M and the quiet-zone margin at 1: a phone camera a metre from a laptop
 * screen has a clean, well-lit target, and a wider margin would shrink the modules inside a fixed
 * 512px square for no gain.
 */
internal fun connectionQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? = try {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 1
    )
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val img = BufferedImage(matrix.width, matrix.height, BufferedImage.TYPE_INT_ARGB)
    val black = 0xFF000000.toInt()
    val white = 0xFFFFFFFF.toInt()
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            img.setRGB(x, y, if (matrix.get(x, y)) black else white)
        }
    }
    SkiaImage.makeFromEncoded(
        ByteArrayOutputStream().also { ImageIO.write(img, "PNG", it) }.toByteArray()
    ).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

/** The `churchpresenter://connect` deep link the connection QR encodes. */
internal fun connectionQrContent(host: String, port: String, apiKey: String?): String = buildString {
    append("churchpresenter://connect?host=$host")
    if (port.isNotBlank()) append("&port=$port")
    if (!apiKey.isNullOrBlank()) append("&apikey=$apiKey")
}

/**
 * Splits a server URL into the host and port the connection QR encodes.
 *
 * Anything unparseable falls back to using the whole string as the host and no port, so a hand-typed
 * host override still produces a scannable code rather than throwing inside the dialog.
 */
internal fun parseServerUrlHostPort(serverUrl: String): Pair<String, String> = try {
    val parsed = java.net.URI.create(serverUrl).toURL()
    val host = parsed.host ?: serverUrl
    val port = if (parsed.port != -1) parsed.port.toString() else ""
    host to port
} catch (_: Exception) {
    serverUrl to ""
}
