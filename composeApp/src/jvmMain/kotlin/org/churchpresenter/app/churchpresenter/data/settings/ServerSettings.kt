package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

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
    val fileUploadEnabled: Boolean = false
)
