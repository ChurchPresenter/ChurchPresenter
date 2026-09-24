package org.churchpresenter.settings

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
 */
fun AppSettings.resolvedFor(profile: OutputProfile): AppSettings = copy(
    stageMonitorSettings = profile.stageMonitorSettings,
    dictionarySettings = profile.dictionarySettings,
    mediaSettings = profile.mediaSettings,
    // Scaling is a property of the screen's shape, so it is the profile's: a wall that wants Fill
    // and a portrait confidence screen that wants Fit can show the same picture at once.
    pictureSettings = pictureSettings.copy(scaleMode = profile.pictureScaleMode),
    mediaScaleMode = profile.mediaScaleMode,
    sttSettings = withSparseOverride(
        sttSettings,
        styleTreeOf(profile.sttSettings, STTSettings.serializer(), STT_GLOBAL_KEYS),
        STTSettings.serializer(),
    ),
    qaSettings = withSparseOverride(
        qaSettings,
        styleTreeOf(profile.qaSettings, QASettings.serializer(), QA_GLOBAL_KEYS),
        QASettings.serializer(),
    ),
    backgroundSettings = resolveBackgroundSurfaces(
        global = backgroundSettings,
        profile = profile.backgroundSettings,
        overridden = profile.backgroundOverrides,
    ),
    songSettings = withSparseOverride(
        songSettings,
        styleTreeOf(profile.songSettings, SongSettings.serializer(), SONG_GLOBAL_KEYS),
        SongSettings.serializer(),
    ),
    bibleSettings = bibleSettings.withSparseBibleOverride(
        styleTreeOf(profile.bibleSettings, BibleSettings.serializer(), BIBLE_GLOBAL_KEYS),
    ),
)

/**
 * The profile [assignment] follows, or `null` when its [ScreenAssignment.activeProfileId] names no
 * profile in [ProjectionSettings.outputProfiles] -- a dangling reference (the profile it pointed at
 * was deleted from under it, or a document was hand-edited). Callers resolve this defensively rather
 * than assuming it is always found: every assignment is *meant* to always have a valid one once
 * `migrateOutputProfiles` and the factory default have run, but nothing in the type system enforces
 * that a hand-edited `settings.json` keeps it true.
 */
fun ProjectionSettings.profileFor(assignment: ScreenAssignment): OutputProfile? =
    assignment.activeProfileId?.let { id -> outputProfiles.find { it.id == id } }
