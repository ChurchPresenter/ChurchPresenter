package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.DictionarySettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.withSparseOverride

// An override is a sparse tree of what one screen changed, not a settings object. These read one
// back as settings, against whichever document it is a difference from -- the defaults, for a test
// that never says otherwise. Null still means the screen follows the document entirely.

fun ScreenAssignment.songSettingsOn(global: SongSettings = SongSettings()): SongSettings? =
    songOverride?.let { withSparseOverride(global, it, SongSettings.serializer()) }

fun ScreenAssignment.bibleSettingsOn(global: BibleSettings = BibleSettings()): BibleSettings? =
    bibleOverride?.let { withSparseOverride(global, it, BibleSettings.serializer()) }

fun ScreenAssignment.dictionarySettingsOn(
    global: DictionarySettings = DictionarySettings(),
): DictionarySettings? =
    dictionaryOverride?.let { withSparseOverride(global, it, DictionarySettings.serializer()) }

fun ScreenAssignment.backgroundSettingsOn(
    global: BackgroundSettings = BackgroundSettings(),
): BackgroundSettings? =
    backgroundOverride?.let { withSparseOverride(global, it, BackgroundSettings.serializer()) }

fun ScreenAssignment.stageMonitorSettingsOn(
    global: StageMonitorSettings = StageMonitorSettings(),
): StageMonitorSettings? =
    stageMonitorOverride?.let { withSparseOverride(global, it, StageMonitorSettings.serializer()) }
