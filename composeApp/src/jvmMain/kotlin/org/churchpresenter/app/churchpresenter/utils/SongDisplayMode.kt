package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants

/**
 * True when any of the four song output surfaces — fullscreen, lower third, and their look-ahead
 * variants — is in per-line mode rather than whole-verse mode. This is what turns on arrow-key line
 * navigation, the on-screen nav hint and per-line highlighting. Extracted from three identical inline
 * OR-chains in SongsTab so the "is any surface in line mode" question lives in one tested place.
 */
internal fun isSongLineMode(settings: SongSettings): Boolean =
    settings.fullscreenDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
        settings.lowerThirdDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
        settings.lookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE ||
        settings.lowerThirdLookAheadDisplayMode != Constants.SONG_DISPLAY_MODE_VERSE

