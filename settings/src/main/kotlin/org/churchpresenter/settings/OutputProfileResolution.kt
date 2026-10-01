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
    songSettings = songSettingsFor(profile),
    bibleSettings = bibleSettingsFor(profile),
)

/** The song settings an output assigned to [profile] uses -- [resolvedFor]'s, without the rest. */
fun AppSettings.songSettingsFor(profile: OutputProfile): SongSettings = withSparseOverride(
    songSettings,
    styleTreeOf(profile.songSettings, SongSettings.serializer(), SONG_GLOBAL_KEYS),
    SongSettings.serializer(),
)

/** The Bible settings an output assigned to [profile] uses -- [resolvedFor]'s, without the rest. */
fun AppSettings.bibleSettingsFor(profile: OutputProfile): BibleSettings = bibleSettings.withSparseBibleOverride(
    styleTreeOf(profile.bibleSettings, BibleSettings.serializer(), BIBLE_GLOBAL_KEYS),
)

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
 * Remembered for the last document asked about: the main window asks on every recomposition, and
 * resolving decodes the whole section. Settings are immutable, so the same instance is the same answer.
 */
fun AppSettings.operatorSongSettings(): SongSettings = operatorSongs.of(this)

/** The Bible settings the operator's window goes by -- see [operatorProfile] and [operatorSongSettings]. */
fun AppSettings.operatorBibleSettings(): BibleSettings = operatorBible.of(this)

private val operatorSongs = LastResult { s: AppSettings ->
    s.projectionSettings.operatorProfile()?.let { s.songSettingsFor(it) } ?: s.songSettings
}

private val operatorBible = LastResult { s: AppSettings ->
    s.projectionSettings.operatorProfile()?.let { s.bibleSettingsFor(it) } ?: s.bibleSettings
}

/** [compute]'s answer for the last [AppSettings] instance it was asked about. */
private class LastResult<T>(private val compute: (AppSettings) -> T) {
    @Volatile private var last: Pair<AppSettings, T>? = null

    fun of(settings: AppSettings): T {
        last?.let { (key, value) -> if (key === settings) return value }
        return compute(settings).also { last = settings to it }
    }
}

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
