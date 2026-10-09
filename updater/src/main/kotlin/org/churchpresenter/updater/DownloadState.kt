package org.churchpresenter.updater

import java.io.File

internal sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState() // -1f = indeterminate
    data class Done(val file: File) : DownloadState()
    /** [unverified]: the installer was missing a published digest or did not match it, and was not kept. */
    data class Error(val message: String, val unverified: Boolean = false) : DownloadState()
}
