package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

@Serializable
data class WindowLayoutSettings(
    val schedulePanelWidthDp: Int = 280,
    val schedulePanelCollapsed: Boolean = false,
    val previewPanelWidthDp: Int = 280,
    val previewPanelCollapsed: Boolean = false,
    val lyricsPanelWidthDp: Int = 0,
    val splitLivePanelWidth: Int = 300,
    val bibleColWidthBook: Int = 200,
    val bibleColWidthChapter: Int = 120,
    val lowerThirdListWidthDp: Int = 240,
    val canvasLeftPanelWidthDp: Int = 200,
    val canvasRightPanelWidthDp: Int = 200,
    val qaRightPanelWidthDp: Int = 280,
    val sttRightPanelWidthDp: Int = 280,
    val announcementsPreviewPanelWidthDp: Int = 260,
    val announcementsLeftPanelWidthDp: Int = 300,
    val dictionaryListWidthDp: Int = 320,
)
