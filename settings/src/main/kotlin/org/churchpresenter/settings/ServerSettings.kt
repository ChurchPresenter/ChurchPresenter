package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.settings.utils.Constants

@Serializable
data class ServerSettings(
    val enabled: Boolean = false,
    val port: Int = Constants.SERVER_DEFAULT_PORT,
    val apiKeyEnabled: Boolean = false,
    val apiKey: String = "",
    /** Optional fixed hostname/IP shown in the Server URL.
     *  Leave blank to auto-detect from the active network interface. */
    val serverHost: String = "",
    /** When false, POST /api/presentations/upload and POST /api/pictures/upload
     *  return 403 and no files are written to disk. */
    val fileUploadEnabled: Boolean = false,
    /** Maximum media-file upload size (in MB) accepted from companion devices. */
    val maxMediaUploadMb: Int = Constants.DEFAULT_MAX_MEDIA_UPLOAD_MB
)

/** The ports a TCP server can listen on. */
val SERVER_PORTS = 1..65_535

/**
 * [text] as a port the companion server can listen on, or null when it is not one -- blank, or
 * past 65535, which the five-digit port field lets through and `ServerSocket` throws on.
 */
fun serverPortOrNull(text: String): Int? = text.toIntOrNull()?.takeIf { it in SERVER_PORTS }
