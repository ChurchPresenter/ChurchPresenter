package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.remote_label_image
import org.churchpresenter.strings.generated.resources.remote_label_kilobytes
import org.churchpresenter.strings.generated.resources.remote_label_section
import org.churchpresenter.strings.generated.resources.remote_label_song
import org.churchpresenter.strings.generated.resources.slide_number
import org.churchpresenter.app.churchpresenter.server.RemoteLabel
import org.jetbrains.compose.resources.getString

private const val BYTES_PER_KB = 1024L

/** A remote-activity label in the operator's language: names as they are, numbered labels worded. */
internal suspend fun RemoteLabel.text(): String = when (this) {
    is RemoteLabel.Text -> value
    is RemoteLabel.Song -> getString(Res.string.remote_label_song, number)
    is RemoteLabel.Section -> getString(Res.string.remote_label_section, index)
    is RemoteLabel.Slide -> getString(Res.string.slide_number, number)
    is RemoteLabel.Image -> getString(Res.string.remote_label_image, index)
    is RemoteLabel.Size -> getString(Res.string.remote_label_kilobytes, bytes / BYTES_PER_KB)
}
