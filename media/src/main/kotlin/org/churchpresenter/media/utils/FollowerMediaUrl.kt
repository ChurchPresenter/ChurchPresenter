package org.churchpresenter.media.utils

import org.churchpresenter.settings.utils.Constants
import java.io.File

/**
 * Which URL a follower actually plays for a schedule item's media.
 *
 * A mirrored item's path usually only exists on the primary's disk, so it is streamed from there
 * ([remoteStreamUrl], null when there is nothing to stream from). A path that *does* resolve here —
 * a shared network drive, or the same layout on both machines — is played from disk instead: no
 * network in the path, and seeking a local file beats seeking an HTTP stream. A URL-type item is
 * already reachable from anywhere and is never rewritten.
 */
internal fun followerMediaUrl(mediaType: String, localUrl: String, remoteStreamUrl: String?): String =
    if (mediaType == Constants.MEDIA_TYPE_LOCAL && remoteStreamUrl != null && !File(localUrl).exists()) {
        remoteStreamUrl
    } else {
        localUrl
    }
