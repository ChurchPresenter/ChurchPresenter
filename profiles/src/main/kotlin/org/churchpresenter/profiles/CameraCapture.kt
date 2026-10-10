package org.churchpresenter.profiles

import org.churchpresenter.canvas.DeckLinkManager
import org.churchpresenter.canvas.isFfmpegAvailable

/** Whether this machine can open a camera background: ffmpeg, or a DeckLink card. */
internal fun canCaptureCamera(
    ffmpegAvailable: () -> Boolean = ::isFfmpegAvailable,
    deckLinkAvailable: () -> Boolean = DeckLinkManager::isAvailable,
): Boolean = ffmpegAvailable() || deckLinkAvailable()
