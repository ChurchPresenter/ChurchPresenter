package org.churchpresenter.app.churchpresenter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.churchpresenter.bibletab.BibleEngineClient
import org.churchpresenter.bibletab.BibleViewModel
import org.churchpresenter.dictionary.DictionaryViewModel
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.slides.viewmodel.PresentationViewModel
import org.churchpresenter.canvas.SceneViewModel
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.songs.SongsViewModel
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.bibletab.onEngineScripture
import org.churchpresenter.bibletab.onEngineVersion
import org.churchpresenter.settings.AppSettings

/**
 * The ViewModels the main screen owns, created once and disposed with it.
 *
 * They are hoisted here, above the tabs, so they outlive a tab switch or a collapsed panel — the
 * schedule survives collapsing its panel, and the Bible engine survives leaving the Bible tab. This
 * holder goes only to MainDesktop's own wiring and layout files — the standing exception AGENT.md
 * records for the root screen — and never leaves them.
 *
 * [publish] and [link] are read through [State] so a callback handed to a ViewModel once always
 * reaches the latest one MainDesktop was given.
 */
internal class MainDesktopViewModels(
    appSettings: AppSettings,
    publish: State<MainDesktopPublishers>,
    link: State<InstanceLinkBridge>,
) {
    val picturesViewModel = PicturesViewModel(appSettings)
    val presentationViewModel = PresentationViewModel(appSettings)
    val sceneViewModel = SceneViewModel()
    val songsViewModel = SongsViewModel(
        appSettings,
        onSongsLoaded = { songs -> publish.value.onSongsLoaded?.invoke(songs) },
        remoteSyncLog = { event, fields -> InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, event, fields) },
    )
    val bibleViewModel = BibleViewModel(
        appSettings,
        onBibleLoaded = { bible, translation -> publish.value.onBibleLoaded?.invoke(bible, translation) },
        onSecondaryBibleFilePathChanged = { path -> link.value.onSecondaryBibleFilePathChanged?.invoke(path) },
        onBibleFilePathsChanged = { paths -> link.value.onBibleFilePathsChanged?.invoke(paths) },
        remoteSyncLog = { event, fields -> InstanceLinkLogger.log(InstanceLinkLogSide.FOLLOWER, event, fields) },
    )

    /** The Bible Lookup Engine client — feeds detected scripture into the Bible tab and forwards the
     *  reverse-lookup level to the engine. */
    val bibleEngineClient = BibleEngineClient(onScripture = { e ->
        bibleViewModel.onEngineScripture(e)
    }, onVersion = { version ->
        bibleViewModel.onEngineVersion(version)
    }).also { client ->
        bibleViewModel.onTextMatchLevelChanged = { level -> client.setLevel(level.name.lowercase()) }
        bibleViewModel.onContinuationSpeedChanged = { speed -> client.setContinuationSpeed(speed.name.lowercase()) }
    }

    val dictionaryViewModel = DictionaryViewModel()
    val scheduleViewModel = ScheduleViewModel(
        onScheduleChanged = { items -> publish.value.onScheduleChanged?.invoke(items) },
    )

    /** Disposes them newest first, the order their separate disposal effects used to run in. */
    fun dispose() {
        scheduleViewModel.dispose()
        dictionaryViewModel.dispose()
        bibleEngineClient.dispose()
        bibleViewModel.dispose()
        songsViewModel.dispose()
        presentationViewModel.dispose()
        picturesViewModel.dispose()
    }
}

@Composable
internal fun rememberMainDesktopViewModels(
    appSettings: AppSettings,
    publish: MainDesktopPublishers,
    link: InstanceLinkBridge,
): MainDesktopViewModels {
    val currentPublish = rememberUpdatedState(publish)
    val currentLink = rememberUpdatedState(link)
    val vms = remember { MainDesktopViewModels(appSettings, currentPublish, currentLink) }
    DisposableEffect(Unit) { onDispose { vms.dispose() } }
    return vms
}
