package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.utils.UpdateCheckInterval
import org.churchpresenter.app.churchpresenter.models.SongTuning

@Serializable
data class AppSettings(
    /**
     * Schema version of this settings document, used by `SettingsManager` to decide which
     * migrations still need to run. A file written before versioning existed has no such key and
     * is therefore treated as version 0 — every migration runs, exactly as it did before.
     *
     * See [CURRENT_SETTINGS_VERSION] for the bump procedure.
     */
    val settingsVersion: Int = CURRENT_SETTINGS_VERSION,
    val songSettings: SongSettings = SongSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val dictionarySettings: DictionarySettings = DictionarySettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val stockPhotoSettings: StockPhotoSettings = StockPhotoSettings(),
    val projectionSettings: ProjectionSettings = ProjectionSettings(),
    val pictureSettings: PictureSettings = PictureSettings(),
    val presentationSettings: PresentationSettings = PresentationSettings(),
    val streamingSettings: StreamingSettings = StreamingSettings(),
    val announcementsSettings: AnnouncementsSettings = AnnouncementsSettings(),
    val qaSettings: QASettings = QASettings(),
    val presentationRemoteSettings: PresentationRemoteSettings = PresentationRemoteSettings(),
    val sttSettings: STTSettings = STTSettings(),
    val bibleEngineSettings: BibleEngineSettings = BibleEngineSettings(),
    val serverSettings: ServerSettings = ServerSettings(),
    val stageMonitorSettings: StageMonitorSettings = StageMonitorSettings(),
    val keyboardShortcutSettings: KeyboardShortcutSettings = KeyboardShortcutSettings(),
    val presentationStorageDirectory: String = "",
    val mediaStorageDirectory: String = "",
    val schedulePanelWidthDp: Int = 280,
    val schedulePanelCollapsed: Boolean = false,
    val scheduleItemZoomPercent: Int = 100,
    /**
     * Legacy schedule card layout: the item's buttons sit on their own line under the title and are
     * always visible, instead of the hover overlay that paints over the title's right-hand end.
     */
    val scheduleLegacyRowActions: Boolean = false,
    /**
     * Schedule toolbar buttons the operator has turned off, by `ScheduleToolbarButton` name — the
     * same shape as [hiddenTabs], so an unknown name from a newer build is simply ignored.
     */
    val hiddenScheduleButtons: Set<String> = emptySet(),
    val previewPanelWidthDp: Int = 280,
    val previewPanelCollapsed: Boolean = false,
    val maximizedLayout: WindowLayoutSettings = WindowLayoutSettings(),
    val windowedLayout: WindowLayoutSettings = WindowLayoutSettings(),
    val theme: String = Constants.SYSTEM,
    val language: String = "en",
    val eulaAcceptedVersion: Int = 0,
    val webBookmarks: List<WebBookmark> = emptyList(),
    val windowPlacement: String = "maximized",
    val windowWidth: Int = 1280,
    val windowHeight: Int = 800,
    val windowX: Int = -1,
    val windowY: Int = -1,
    val hiddenTabs: Set<String> = setOf("QA", "STT"),
    val crosswordUnlockedLevel: Int = 0,
    val crosswordProgress: Map<Int, String> = emptyMap(),
    val obsSettings: OBSSettings = OBSSettings(),
    val atemSettings: AtemSettings = AtemSettings(),
    val planningCenterSettings: PlanningCenterSettings = PlanningCenterSettings(),
    val companionSatelliteConnections: List<CompanionSatelliteSettings> = listOf(CompanionSatelliteSettings()),
    val instanceLink: InstanceLinkSettings = InstanceLinkSettings(),
    val songFavorites: List<String> = emptyList(),
    val songFavoritesPanelHeightDp: Int = 120,
    val songBpm: Map<String, Int> = emptyMap(), // songId -> metronome BPM (0 = off), not stored in the .song file
    val songCapo: Map<String, Int> = emptyMap(), // songId -> capo fret (0 = none), not stored in the .song file
    val songColOrder: List<String> = emptyList(),
    val songHiddenCols: Set<String> = setOf("tune", "play_count", "author", "composer"),
    val setupWizardShown: Boolean = false,
    val analyticsReportingEnabled: Boolean = true,
    val participateInPrereleases: Boolean = false,
    val updateCheckInterval: UpdateCheckInterval = UpdateCheckInterval.EVERY_LAUNCH,
    val lastUpdateCheckTimestamp: Long = 0L,
    val storyPrompt: StoryPromptState = StoryPromptState()
) {
    /** What the song identified by [songId] is played at — tempo and capo together. */
    fun tuningFor(songId: String): SongTuning =
        SongTuning(bpm = songBpm[songId] ?: 0, capo = songCapo[songId] ?: 0)

    /** These settings with [songId]'s tuning replaced by [tuning]. */
    fun withTuning(songId: String, tuning: SongTuning): AppSettings = copy(
        songBpm = songBpm + (songId to tuning.bpm),
        songCapo = songCapo + (songId to tuning.capo),
    )

    companion object {
        /**
         * The schema version this build writes. Bump by one whenever a settings field changes in a
         * way plain defaults can't absorb — a rename, a type change, or a restructure — and add the
         * matching step to `SettingsManager`'s migration chain with that same target version.
         *
         * Purely *additive* fields need no bump: `ignoreUnknownKeys` plus a default already handles
         * those in both directions.
         */
        const val CURRENT_SETTINGS_VERSION = 7
    }
}
