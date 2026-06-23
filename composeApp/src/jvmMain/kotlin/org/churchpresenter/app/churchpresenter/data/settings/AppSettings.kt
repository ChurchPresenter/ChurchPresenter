package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.utils.Constants

@Serializable
data class AppSettings(
    val songSettings: SongSettings = SongSettings(),
    val bibleSettings: BibleSettings = BibleSettings(),
    val backgroundSettings: BackgroundSettings = BackgroundSettings(),
    val projectionSettings: ProjectionSettings = ProjectionSettings(),
    val pictureSettings: PictureSettings = PictureSettings(),
    val presentationSettings: PresentationSettings = PresentationSettings(),
    val streamingSettings: StreamingSettings = StreamingSettings(),
    val announcementsSettings: AnnouncementsSettings = AnnouncementsSettings(),
    val qaSettings: QASettings = QASettings(),
    val sttSettings: STTSettings = STTSettings(),
    val bibleEngineSettings: BibleEngineSettings = BibleEngineSettings(),
    val serverSettings: ServerSettings = ServerSettings(),
    val stageMonitorSettings: StageMonitorSettings = StageMonitorSettings(),
    val presentationStorageDirectory: String = "",
    val mediaStorageDirectory: String = "",
    val schedulePanelWidthDp: Int = 280,
    val schedulePanelCollapsed: Boolean = false,
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
    val songFavorites: List<String> = emptyList(),
    val songFavoritesPanelHeightDp: Int = 120,
    val songColOrder: List<String> = emptyList(),
    val songHiddenCols: Set<String> = setOf("tune", "play_count", "author", "composer"),
    val setupWizardShown: Boolean = false
)
