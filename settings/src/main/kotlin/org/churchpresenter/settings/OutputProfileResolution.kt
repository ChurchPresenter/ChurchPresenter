package org.churchpresenter.settings

import kotlinx.serialization.KSerializer
import org.churchpresenter.settings.utils.Constants

/**
 * What an output assigned to [profile] actually renders with: the global document's content
 * (library folder, file lists, translation-stack membership, column widths -- see
 * [BIBLE_GLOBAL_KEYS]/[SONG_GLOBAL_KEYS]) with every other field taken from the profile.
 *
 * [styleTreeOf] projects a *full* settings object down to its non-content keys -- a keep-list, not
 * a diff -- and [withSparseOverride]/[BibleSettings.withSparseBibleOverride] merge that onto the
 * global document exactly as they always have; captions and Q&A go the same way, keeping
 * [STT_GLOBAL_KEYS]/[QA_GLOBAL_KEYS]. [DictionarySettings]/[MediaSettings]/[StageMonitorSettings]
 * have no content-only fields at all, so the profile's copies are used wholesale.
 *
 * Backgrounds are the exception: a profile follows the Background tab surface by surface unless it
 * says otherwise ([OutputProfile.backgroundOverrides]), so the house background -- what shows when
 * nothing is live -- stays one thing to change, and a profile overrides only what it means to.
 *
 * Each merged section is remembered by the identity of the two objects it is made from -- the
 * document's section and the profile's copy -- because every output window, preview tile and
 * off-screen output resolves on each settings change, and a merge encodes and decodes the whole
 * section. Settings are immutable, so a change elsewhere in the document reuses the same answer.
 */
fun AppSettings.resolvedFor(profile: OutputProfile): AppSettings = copy(
    stageMonitorSettings = profile.stageMonitorSettings,
    dictionarySettings = profile.dictionarySettings,
    mediaSettings = profile.mediaSettings,
    // Scaling is a property of the screen's shape, so it is the profile's: a wall that wants Fill
    // and a portrait confidence screen that wants Fit can show the same picture at once.
    pictureSettings = pictureSettings.copy(scaleMode = profile.pictureScaleMode),
    mediaScaleMode = profile.mediaScaleMode,
    sttSettings = resolvedStt.of(sttSettings, profile.sttSettings) { global, own ->
        global.styledBy(own, STTSettings.serializer(), STT_GLOBAL_KEYS)
    },
    qaSettings = resolvedQa.of(qaSettings, profile.qaSettings) { global, own ->
        global.styledBy(own, QASettings.serializer(), QA_GLOBAL_KEYS)
    },
    backgroundSettings = resolveBackgroundSurfaces(
        global = backgroundSettings,
        profile = profile.backgroundSettings,
        overridden = profile.backgroundOverrides,
    ),
    songSettings = songSettingsFor(profile),
    bibleSettings = bibleSettingsFor(profile),
)

/** The song settings an output assigned to [profile] uses -- [resolvedFor]'s, without the rest. */
fun AppSettings.songSettingsFor(profile: OutputProfile): SongSettings =
    resolvedSongs.of(songSettings, profile.songSettings) { global, own ->
        global.styledBy(own, SongSettings.serializer(), SONG_GLOBAL_KEYS)
    }

/** The Bible settings an output assigned to [profile] uses -- [resolvedFor]'s, without the rest. */
fun AppSettings.bibleSettingsFor(profile: OutputProfile): BibleSettings =
    resolvedBible.of(bibleSettings, profile.bibleSettings) { global, own ->
        global.withSparseBibleOverride(styleTreeOf(own, BibleSettings.serializer(), BIBLE_GLOBAL_KEYS))
    }

/** This section of the document with [own]'s styling written over it -- see [styleTreeOf]. */
private fun <T> T.styledBy(own: T, serializer: KSerializer<T>, globalKeys: Set<String>): T =
    withSparseOverride(this, styleTreeOf(own, serializer, globalKeys), serializer)

private val resolvedStt = RecentMerges<STTSettings>()
private val resolvedQa = RecentMerges<QASettings>()
private val resolvedSongs = RecentMerges<SongSettings>()
private val resolvedBible = RecentMerges<BibleSettings>()

/**
 * The ids of the profiles some output is following: screens first, then Browser Source, NDI and
 * OMT outputs, in that order.
 */
fun ProjectionSettings.profileIdsInUse(): List<String> =
    (screenAssignments + browserSourceOutputs + ndiOutputs + omtOutputs)
        .mapNotNull { it.activeProfileId }
        .distinct()

/**
 * The profile the operator's own window follows for what is not one per install: the first one an
 * output is using, else the first profile there is. `null` only when there are no profiles at all.
 *
 * The document keeps only the install-wide part of the Bible and Song settings, so whatever the
 * main window decides by -- splitting a long verse, offering a title slide, repeating the chorus,
 * line mode -- is read from here, the same values the main output draws by.
 */
fun ProjectionSettings.operatorProfile(): OutputProfile? {
    val inUse = profileIdsInUse()
    return inUse.firstNotNullOfOrNull { id -> outputProfiles.find { it.id == id } }
        ?: outputProfiles.firstOrNull()
}

/**
 * The song settings the operator's window goes by -- see [operatorProfile].
 *
 * Remembered for the last song settings and profile asked about: the main window asks on every
 * recomposition, and resolving decodes the whole section. Settings are immutable, so the same two
 * instances are the same answer, whatever else in the document changed since.
 */
fun AppSettings.operatorSongSettings(): SongSettings = operatorSongs.of(this)

/** The Bible settings the operator's window goes by -- see [operatorProfile] and [operatorSongSettings]. */
fun AppSettings.operatorBibleSettings(): BibleSettings = operatorBible.of(this)

private val operatorSongs = LastResult(
    inputs = { s -> listOf(s.songSettings, s.projectionSettings.operatorProfile()) },
) { s -> s.projectionSettings.operatorProfile()?.let { s.songSettingsFor(it) } ?: s.songSettings }

private val operatorBible = LastResult(
    inputs = { s -> listOf(s.bibleSettings, s.projectionSettings.operatorProfile()) },
) { s -> s.projectionSettings.operatorProfile()?.let { s.bibleSettingsFor(it) } ?: s.bibleSettings }

/**
 * The profile [assignment] follows, or `null` when its [ScreenAssignment.activeProfileId] names no
 * profile in [ProjectionSettings.outputProfiles] -- a dangling reference (the profile it pointed at
 * was deleted from under it, or a document was hand-edited). Callers resolve this defensively rather
 * than assuming it is always found: every assignment is *meant* to always have a valid one once
 * `migrateOutputProfiles` and the factory default have run, but nothing in the type system enforces
 * that a hand-edited `settings.json` keeps it true. [BLANK_OUTPUT_PROFILE_ID] resolves to the
 * built-in [BLANK_OUTPUT_PROFILE].
 */
fun ProjectionSettings.profileFor(assignment: ScreenAssignment): OutputProfile? =
    if (assignment.activeProfileId == BLANK_OUTPUT_PROFILE_ID) BLANK_OUTPUT_PROFILE
    else assignment.activeProfileId?.let { id -> outputProfiles.find { it.id == id } }

/** The screens whose output profile is a stage monitor, by their index among the assignments. */
fun stageMonitorScreenIndices(proj: ProjectionSettings): List<Int> =
    proj.screenAssignments.indices.filter {
        proj.profileFor(proj.screenAssignments[it])?.displayMode == Constants.DISPLAY_MODE_STAGE_MONITOR
    }
