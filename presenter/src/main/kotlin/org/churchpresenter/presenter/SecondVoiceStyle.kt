package org.churchpresenter.presenter

import org.churchpresenter.core.models.songs.SongTextOverrides
import org.churchpresenter.settings.SongSettings
import kotlin.math.roundToInt

/**
 * These settings with one song's own font, size and colours ([overrides]) written over the
 * primary lyrics' look, full screen and lower third alike.
 *
 * Applied once, where a song is drawn, so everything that reads the look — the slide, the fit, the
 * boxes, the band — sees the song's values without being told about them. A language with a look
 * of its own keeps it; one that follows the primary follows the song too.
 */
internal fun SongSettings.withTextOverrides(overrides: SongTextOverrides): SongSettings {
    if (!overrides.isCustom) return this
    val scale = overrides.sizeScale
    return copy(
        lyricsFontType = overrides.font.ifBlank { lyricsFontType },
        lyricsLowerThirdFontType = overrides.font.ifBlank { lyricsLowerThirdFontType },
        lyricsFontSize = (lyricsFontSize * scale).roundToInt(),
        lyricsLowerThirdFontSize = (lyricsLowerThirdFontSize * scale).roundToInt(),
        lyricsColor = overrides.color.ifBlank { lyricsColor },
        lyricsLowerThirdColor = overrides.color.ifBlank { lyricsLowerThirdColor },
        layoutExtras = layoutExtras.copy(
            secondVoiceColor = overrides.secondVoiceColor.ifBlank { layoutExtras.secondVoiceColor },
        ),
    )
}
