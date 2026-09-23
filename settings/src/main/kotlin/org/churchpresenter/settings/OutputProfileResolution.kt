package org.churchpresenter.settings

/**
 * What an output assigned to [profile] actually renders with: the global document's content
 * (library folder, file lists, translation-stack membership, column widths -- see
 * [BIBLE_GLOBAL_KEYS]/[SONG_GLOBAL_KEYS]) with every other field taken from the profile.
 *
 * [styleTreeOf] projects a *full* settings object down to its non-content keys -- a keep-list, not
 * a diff -- and [withSparseOverride]/[BibleSettings.withSparseBibleOverride] merge that onto the
 * global document exactly as they always have. [DictionarySettings]/[StageMonitorSettings] have no
 * content-only fields at all, so the profile's copies are used wholesale.
 *
 * Backgrounds are the exception: a profile follows the Background tab surface by surface unless it
 * says otherwise ([OutputProfile.backgroundOverrides]), so the house background -- what shows when
 * nothing is live -- stays one thing to change, and a profile overrides only what it means to.
 */
fun AppSettings.resolvedFor(profile: OutputProfile): AppSettings = copy(
    stageMonitorSettings = profile.stageMonitorSettings,
    // No `dictionarySettings` here on purpose: the dictionary's look is one per install, edited
    // from the Dictionary tab's own gear, so every output resolves the document's copy unchanged.
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
