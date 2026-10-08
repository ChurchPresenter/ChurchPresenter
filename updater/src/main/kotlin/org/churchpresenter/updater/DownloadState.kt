package org.churchpresenter.updater

import java.io.File

internal sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState() // -1f = indeterminate
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
