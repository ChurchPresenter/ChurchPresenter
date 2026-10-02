package org.churchpresenter.media.viewmodel

import java.io.File

/** A clip's title from its address: the last path segment of a stream, a file's own name. */
fun mediaTitleFromUrl(url: String): String {
    return when {
        url.startsWith("http://") || url.startsWith("https://") || url.startsWith("rtsp://") ||
            url.startsWith("rtp://") || url.startsWith("mms://") || url.startsWith("udp://") ->
            url.substringAfterLast("/").ifBlank { url }
        else -> {
            val file = File(url)
            // `File.name` splits on the platform's own separator, so a Windows path that no
            // longer exists still yields "clip.mp4". `substringAfterLast("/")` found no slash
            // in C:\Media\clip.mp4 and handed the whole path back as the title.
            if (file.exists()) file.nameWithoutExtension
            else file.name.ifBlank { url }
        }
    }
}

/** A position or length as the transport shows it: `m:ss`, or `h:mm:ss` from an hour up. */
fun formatMediaTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
