package org.churchpresenter.app.churchpresenter

import java.io.File
import org.churchpresenter.app.churchpresenter.data.Songs
import org.churchpresenter.lowerthird.SkiaLottieFrameRenderer
import org.churchpresenter.ui.HeicDecoder
import org.churchpresenter.app.churchpresenter.utils.UsageEvent
import org.churchpresenter.app.churchpresenter.utils.UsageEvents
import org.churchpresenter.companionserver.CompanionHost
import org.churchpresenter.core.models.songs.SongItem

/**
 * Everything `:companion-server` is allowed to know about this app, in one place.
 *
 * The module holds the HTTP surface and nothing app-shaped, so the four things a route genuinely
 * needs from here — the version to report, the usage counter, the HEIC decoder and the song
 * parser — are handed over as values, along with the Skia renderer the lottie cache draws with.
 * If a route needs a fifth, it is added here and nowhere else.
 */
internal fun appCompanionHost(): CompanionHost = CompanionHost(
    appVersion = BuildConfig.APP_VERSION,
    onMobileClientConnected = { UsageEvents.recordOncePerRun(UsageEvent.MOBILE_APP_CONNECTED) },
    decodeHeicToJpeg = { file -> HeicDecoder.toJpegBytes(file) },
    loadSongs = ::loadSongFile,
    lottieRenderer = SkiaLottieFrameRenderer,
)

/** Parses one `.sps` file into the songs it holds. Throws, so the caller can report the file. */
private fun loadSongFile(file: File): List<SongItem> =
    Songs().apply { loadFromSpsAppend(file.absolutePath) }.getSongs()
