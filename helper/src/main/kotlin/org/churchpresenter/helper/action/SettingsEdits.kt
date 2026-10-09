package org.churchpresenter.helper.action

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BackgroundSurface
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.editProfile
import org.churchpresenter.settings.profileIdsInUse
import org.churchpresenter.settings.utils.Constants
import kotlin.math.roundToInt

internal const val MIN_FONT_SIZE = 12
internal const val MAX_FONT_SIZE = 200
private const val FONT_STEP_FRACTION = 0.1f

/**
 * The profiles an appearance change goes to: every one an output is using, else the one the main
 * window falls back to. Text style is never read from the global document, only from a profile.
 */
internal fun AppSettings.profilesToEdit(): List<String> =
    projectionSettings.profileIdsInUse().ifEmpty { listOf(projectionSettings.fallbackProfileId) }

/**
 * [settings] with the [scope] background set to the colour [hex]: on the Background tab, and on every
 * profile in use that keeps its own copy of that surface — otherwise those outputs would not change.
 */
fun withBackgroundColor(settings: AppSettings, scope: ContentScope, hex: String): AppSettings {
    val surfaces = when (scope) {
        ContentScope.SONG -> listOf(BackgroundSurface.SONG)
        ContentScope.BIBLE -> listOf(BackgroundSurface.BIBLE)
        ContentScope.ALL -> listOf(BackgroundSurface.SONG, BackgroundSurface.BIBLE)
    }
    var projection = settings.projectionSettings
    for (id in settings.profilesToEdit()) {
        val profile = projection.outputProfiles.find { it.id == id } ?: continue
        val overridden = surfaces.filter { it.name in profile.backgroundOverrides }
        if (overridden.isNotEmpty()) {
            projection = projection.editProfile(id) {
                it.copy(backgroundSettings = it.backgroundSettings.withColor(overridden, hex))
            }
        }
    }
    return settings.copy(
        backgroundSettings = settings.backgroundSettings.withColor(surfaces, hex),
        projectionSettings = projection,
    )
}

private fun BackgroundSettings.withColor(surfaces: List<BackgroundSurface>, hex: String): BackgroundSettings =
    surfaces.fold(this) { acc, surface ->
        when (surface) {
            BackgroundSurface.SONG -> acc.copy(songBackground = acc.songBackground.asColor(hex))
            BackgroundSurface.BIBLE -> acc.copy(bibleBackground = acc.bibleBackground.asColor(hex))
            else -> acc
        }
    }

private fun BackgroundConfig.asColor(hex: String) =
    copy(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = hex)

/**
 * [settings] with the [scope] text a step bigger ([direction] +1) or smaller (-1) on every profile in
 * use. Song text that fits itself to the screen moves its largest and smallest size, since its fixed
 * size is not read then.
 */
fun withFontStep(settings: AppSettings, scope: ContentScope, direction: Int): AppSettings {
    var projection = settings.projectionSettings
    for (id in settings.profilesToEdit()) {
        projection = projection.editProfile(id) { it.withFontStep(scope, direction) }
    }
    return settings.copy(projectionSettings = projection)
}

private fun OutputProfile.withFontStep(scope: ContentScope, direction: Int): OutputProfile = copy(
    songSettings = if (scope == ContentScope.BIBLE) songSettings else songSettings.stepped(direction),
    bibleSettings = if (scope == ContentScope.SONG) bibleSettings else bibleSettings.stepped(direction),
)

private fun SongSettings.stepped(direction: Int): SongSettings =
    if (lyricsFontSizeAutoFit) {
        copy(
            lyricsMaxFontSize = step(lyricsMaxFontSize, direction),
            lyricsMinFontSize = step(lyricsMinFontSize, direction),
        )
    } else {
        copy(lyricsFontSize = step(lyricsFontSize, direction))
    }

private fun BibleSettings.stepped(direction: Int): BibleSettings = copy(
    primaryBibleFontSize = step(primaryBibleFontSize, direction),
    secondaryBibleFontSize = step(secondaryBibleFontSize, direction),
)

/** [size] moved a tenth (at least one point) in [direction], kept to a readable range. */
internal fun step(size: Int, direction: Int): Int {
    val delta = maxOf(1, (size * FONT_STEP_FRACTION).roundToInt())
    return (size + delta * direction).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
}
