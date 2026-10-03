package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.BackgroundSettings

/**
 * Whether a slide announced by the primary is one that can actually be fetched.
 *
 * The snapshot sent on connect always carries this event, with an empty id when the primary has no
 * presentation open — asking for that slide's bytes would be a request for nothing, answered with a
 * 404 and logged as a failed mirror.
 */
internal fun hasFetchableSlide(slideId: String): Boolean = slideId.isNotBlank()

/**
 * Whether the mirrored background cache has to be emptied before fetching.
 *
 * The per-file check is "does this exist locally", so a background the primary has replaced under
 * the same name would otherwise never be re-downloaded — the stale copy would satisfy the check
 * forever.
 */
internal fun shouldInvalidateBackgroundCache(backgroundsUpdatedSignal: Int): Boolean =
    backgroundsUpdatedSignal > 0

/**
 * The settings the output should actually render with.
 *
 * Only the rendering paths see the mirrored backgrounds; editing and persistence keep using this
 * instance's own, so a follower never saves the primary's backgrounds over its own configuration.
 */
internal fun withMirroredBackgrounds(
    settings: AppSettings,
    mirrored: BackgroundSettings?,
): AppSettings = if (mirrored == null) settings else settings.copy(backgroundSettings = mirrored)

/**
 * [settings] with the quick tray's [picked] entry standing in front of every background.
 *
 * This is where a live pick takes effect, and the only place: `effectiveAppSettings` feeds both the
 * presenter windows and the live preview, so one overlay reaches every output. Nothing is written —
 * dropping [picked] back to null restores whatever the Background settings tab, and the songs
 * themselves, already said.
 *
 * The two halves travel separately because a quick background is a pair, exactly as a song's is:
 * the full-screen picture and the lower-third band are chosen in the same panel and can differ.
 */
internal fun withQuickBackground(settings: AppSettings, picked: QuickBackground?): AppSettings =
    if (picked == null) settings else settings.copy(
        backgroundSettings = settings.backgroundSettings.copy(
            quickBackground = picked.background,
            // An inheriting half is no override at all: the band stays whatever the output says.
            quickLowerThirdBackground = picked.lowerThirdBackground.takeIf { it.isCustom },
        ),
    )

/**
 * Whether the mirrored picture cache has to be emptied.
 *
 * The counterpart to [shouldInvalidateBackgroundCache]: cached pictures are keyed by folder and
 * position only, so an image the primary has replaced at the same position would be served from the
 * stale copy forever. Zero is the value the flow replays on subscribe, which announces nothing.
 */
internal fun shouldInvalidatePictureCache(picturesUpdatedSignal: Int): Boolean =
    picturesUpdatedSignal != 0
