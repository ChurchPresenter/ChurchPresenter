package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.settings.AppSettings

// The full-screen backgrounds Bible and song slides draw, alone, for an output's background layer.
//
// Unlike the slide, the background does not fade out and back in with each new verse or section: it
// stays up while it is the same background, crossfades when it changes, and follows the slide's fade
// only while the display is being cleared.

/**
 * The full-screen background [BiblePresenter] draws for the same arguments, alone -- what an output
 * puts on its background layer while the verse is on the slide layer. It makes the presenter's own
 * decisions, from the same [BibleLook]: nothing on a lower third (the band carries it), and nothing
 * until a verse this output shows is up.
 */
@Composable
internal fun BibleSlideBackground(
    modifier: Modifier,
    selectedVerses: List<SelectedVerse>,
    appSettings: AppSettings,
    isLowerThird: Boolean,
    outputRole: String,
    transitionAlpha: Float,
    clearing: Boolean,
    showBackground: Boolean,
    bibleTranslations: List<Int>,
) {
    if (isLowerThird) return
    val look = BibleLook(
        selectedVerses = selectedVerses,
        appSettings = appSettings,
        isLowerThird = false,
        isLowerThirdVertical = false,
        outputRole = outputRole,
        transitionAlpha = transitionAlpha,
        showBackground = showBackground,
        crossfadeEnabled = false,
        bibleTranslations = bibleTranslations,
    )
    look.effectiveVerses.firstOrNull() ?: return
    val resolvedBg = resolveBackground(
        settings = appSettings.backgroundSettings,
        config = look.bgConfig,
        isLowerThird = false,
        showBackground = showBackground,
        transparentWhenBlank = LocalTransparentBlanking.current,
    )
    val enterAlpha = rememberBibleEnterAlpha(appSettings)
    PersistentBackground(
        background = resolvedBg,
        modifier = modifier,
        alpha = { (if (clearing) transitionAlpha else 1f) * enterAlpha.value },
        changeMs = appSettings.bibleSettings.transitionDuration.toInt(),
    )
}

@Composable
internal fun rememberBibleEnterAlpha(appSettings: AppSettings) = rememberEnterAlpha(
    fadeIn = appSettings.bibleSettings.fadeIn,
    durationMs = appSettings.bibleSettings.transitionDuration.toInt().coerceAtLeast(MIN_FADE_IN_MS),
)

/**
 * The full-screen background [SongPresenter] draws for [lyricSection], alone -- what an output puts
 * on its background layer while the section is on the slide layer. Nothing on a lower third, whose
 * band carries it; otherwise the section's own background, the quick tray's pick or the Songs
 * background, resolved exactly as the presenter resolves it.
 */
@Composable
internal fun SongSlideBackground(
    modifier: Modifier,
    lyricSection: LyricSection,
    appSettings: AppSettings,
    isLowerThird: Boolean,
    transitionAlpha: Float,
    clearing: Boolean,
    showBackground: Boolean,
) {
    if (isLowerThird) return
    val resolvedBg = resolveBackground(
        settings = appSettings.backgroundSettings,
        config = appSettings.backgroundSettings.songBackground,
        isLowerThird = false,
        showBackground = showBackground,
        transparentWhenBlank = LocalTransparentBlanking.current,
        ownBackground = lyricSection.background,
    )
    val enterAlpha = rememberSongEnterAlpha(appSettings)
    PersistentBackground(
        background = resolvedBg,
        modifier = modifier,
        alpha = { (if (clearing) transitionAlpha else 1f) * enterAlpha.value },
        changeMs = appSettings.songSettings.transitionDuration.toInt(),
    )
}

@Composable
internal fun rememberSongEnterAlpha(appSettings: AppSettings) = rememberEnterAlpha(
    fadeIn = appSettings.songSettings.fadeIn,
    durationMs = appSettings.songSettings.transitionDuration.toInt().coerceAtLeast(MIN_FADE_IN_MS),
)

private const val MIN_FADE_IN_MS = 100
